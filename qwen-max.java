@Test
void testRetrieveTransferDetail_Success_WithCustomerName() {
    // Arrange
    String transferReferenceNumber = "ref123";
    String customerInternalNumber = "TESTcin123"; // 从请求头中获取的实际值
    
    // 1. 构建精确匹配的 URL - 这是关键修复
    String expectedTransferDetailUri = MOCK_TRADE_ONLINE_URL + "/transfers/" + transferReferenceNumber + 
            "?customerInternalNumber=" + customerInternalNumber + "&sParameterType=S";
    
    // 2. Mock: retrieveTransferDetail Response - 确保设置 responseDetails
    RetrieveTransferDetailResponse mockResponse = new RetrieveTransferDetailResponse();
    RetrieveTransferDetailResponseData responseData = new RetrieveTransferDetailResponseData();
    
    // 设置投资账户信息
    AccountId accountId = new AccountId();
    accountId.setAccountNumber("987654321");
    responseData.setInvestmentAccount(accountId);
    
    // ✅ 关键：设置 responseDetails，这是成功响应所必需的
    ResponseDetails responseDetails = new ResponseDetails();
    responseDetails.setResponseCodeNumber(0); // 成功状态码
    responseData.setResponseDetails(responseDetails);
    mockResponse.setData(responseData);
    
    // 3. Mock CustomerAccounts for retrieveCustomerAccounts()
    CustomerAccounts mockCustomerAccounts = new CustomerAccounts();
    List<InvestmentAccount> accList = new ArrayList<>();
    
    InvestmentAccount acc1 = new InvestmentAccount();
    acc1.setChecksum("chk123");
    
    // 创建完整的 InvestmentAccountId
    com.hsbc.trade.transfer.domain.account.AccountId investmentAccountId = 
        com.hsbc.trade.transfer.domain.account.AccountId.builder()
            .countryAccountCode("CN")
            .groupMemberAccountCode("G001")
            .accountNumber("987654321") // 必须与上面的 accountId 一致
            .accountProductTypeCode("01")
            .accountTypeCode("INV")
            .accountCurrencyCode("CNY")
            .build();
    
    acc1.setInvestmentAccountId(investmentAccountId);
    accList.add(acc1);
    mockCustomerAccounts.setInvestmentAccountList(accList);
    
    // 4. Mock CEP responses
    PartyNameResponse mockPartyNameResponse = new PartyNameResponse();
    PartyName mockName = new PartyName();
    mockName.setLastName("Doe");
    mockName.setGivenName("John");
    mockName.setCustomerChristianName("Smith");
    mockPartyNameResponse.setName(mockName);
    
    PartyContactResponse mockPartyContactResponse = new PartyContactResponse();
    PartyContact mockContact = new PartyContact();
    mockContact.setMobileNumber1("123456789");
    mockPartyContactResponse.setContact(mockContact);
    
    // 5. ✅ 关键修复：设置精确的 mock 调用，使用正确的 URL
    when(mockRestClientService.get(
            eq(expectedTransferDetailUri), // 精确匹配实际调用的 URL
            anyMap(), // 请求头
            eq(RetrieveTransferDetailResponse.class),
            anyInt(), // 超时时间
            anyBoolean() // 是否打印日志
    )).thenReturn(mockResponse);
    
    // 6. Mock retrieveCustomerAccounts 调用
    when(mockRestClientService.get(
            eq(MOCK_ACCOUNTS_MAP_URL + "accounts-map?consumerId=DAC"),
            anyMap(),
            eq(CustomerAccounts.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(mockCustomerAccounts);
    
    // 7. 构建 CEP 请求所需的敏感数据头
    String sensitiveDataJson = "[{\"key\":\"SensitiveHeadersKey\",\"value\":\"" + customerInternalNumber + "\"}]";
    
    // 8. ✅ 修复 CEP URL 格式 - 确保正确替换占位符
    String expectedNameUrl = MOCK_CEP_PARTY_NAME_URL.replace("{cin}", customerInternalNumber)
            .replace("CIN-SensitiveHeadersKey", customerInternalNumber);
    
    String expectedContactUrl = MOCK_CEP_PARTY_CONTACT_URL.replace("{cin}", customerInternalNumber)
            .replace("CIN-SensitiveHeadersKey", customerInternalNumber);
    
    // 9. Mock E2E token
    when(mockE2ETrustTokenUtil.getE2ETrustToken()).thenReturn("mock-e2e-token");
    
    // 10. ✅ 关键：简化 headers 匹配，避免因为 headers 顺序或额外字段导致匹配失败
    when(mockRestClientService.get(
            eq(expectedNameUrl),
            argThat(headers -> headers != null && 
                    headers.containsKey(HTTPRequestHeaderConstants.X_HSBC_E2E_TRUST_TOKEN) &&
                    headers.containsKey(HTTPRequestHeaderConstants.X_HSBC_SENSITIVE_DATA)),
            eq(PartyNameResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(mockPartyNameResponse);
    
    when(mockRestClientService.get(
            eq(expectedContactUrl),
            argThat(headers -> headers != null && 
                    headers.containsKey(HTTPRequestHeaderConstants.X_HSBC_E2E_TRUST_TOKEN) &&
                    headers.containsKey(HTTPRequestHeaderConstants.X_HSBC_SENSITIVE_DATA)),
            eq(PartyContactResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(mockPartyContactResponse);
    
    // 11. 准备请求头
    Map<String, String> requestHeaders = new HashMap<>();
    requestHeaders.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, customerInternalNumber);
    requestHeaders.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
    requestHeaders.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
    requestHeaders.put(HTTPRequestHeaderConstants.X_HSBC_SAML, "<saml:Assertion>dummy-token</saml:Assertion>");
    requestHeaders.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, "<saml:Assertion>dummy-token</saml:Assertion>");
    
    // Act
    RetrieveTransferDetailResponse result = tradeTransferService.retrieveTransferDetail(requestHeaders, transferReferenceNumber);
    
    // Assert
    assertNotNull(result, "响应不应为 null");
    assertNotNull(result.getData(), "响应数据不应为 null");
    assertNotNull(result.getData().getInvestmentAccount(), "投资账户不应为 null");
    assertEquals("987654321", result.getData().getInvestmentAccount().getAccountNumber(), "账户号码应匹配");
    
    // ✅ 验证 checksum 被正确设置
    assertNotNull(result.getData().getAccountChecksumIdentifier(), "应设置 checksum 标识符");
    assertEquals("chk123", result.getData().getAccountChecksumIdentifier(), "checksum 应匹配");
    
    // ✅ 验证 responseDetails 被正确设置
    assertNotNull(result.getResponseDetails(), "应有响应详情");
    assertEquals(0, result.getResponseDetails().getResponseCodeNumber(), "响应码应为 0 (成功)");
    
    // Verify interactions
    verify(mockRestClientService, times(1)).get(
            eq(expectedTransferDetailUri),
            anyMap(),
            eq(RetrieveTransferDetailResponse.class),
            anyInt(),
            anyBoolean()
    );
    
    verify(mockRestClientService, times(1)).get(
            eq(MOCK_ACCOUNTS_MAP_URL + "accounts-map?consumerId=DAC"),
            anyMap(),
            eq(CustomerAccounts.class),
            anyInt(),
            anyBoolean()
    );
    
    verify(mockRestClientService, times(1)).get(
            eq(expectedNameUrl),
            anyMap(),
            eq(PartyNameResponse.class),
            anyInt(),
            anyBoolean()
    );
    
    verify(mockRestClientService, times(1)).get(
            eq(expectedContactUrl),
            anyMap(),
            eq(PartyContactResponse.class),
            anyInt(),
            anyBoolean()
    );
    
    verify(mockE2ETrustTokenUtil, times(2)).getE2ETrustToken(); // 一次用于姓名，一次用于联系方式
}
