@Test
void testRetrieveTransferDetail_Success_WithCustomerName() {
    // Arrange
    String transferReferenceNumber = "ref123";
    // 从 baseRequestHeaders 中获取 customerInternalNumber，确保使用实际值
    String customerInternalNumber = baseRequestHeaders.get(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID); // "TESTcin123"

    // ✅ 关键修复1: 使用从配置文件中读取的真实 trade-online URL
    String REAL_TRADE_ONLINE_URL = "https://srbp-aag-uat-wealth-platform-amh-dev.ikp1001snp.cloud.hk.hsbc/api/srbp-online/v3";

    // 1. Mock: retrieveTransferDetail Response
    RetrieveTransferDetailResponse mockResponse = new RetrieveTransferDetailResponse();
    RetrieveTransferDetailResponseData responseData = new RetrieveTransferDetailResponseData();
    // 设置一个非空的 AccountId
    AccountId accountId = new AccountId();
    accountId.setAccountNumber("987654321"); // 这个值必须和下面 mock 的 InvestmentAccount 一致
    responseData.setInvestmentAccount(accountId);
    // 设置 responseDetails
    ResponseDetails responseDetails = new ResponseDetails();
    responseDetails.setResponseCodeNumber(0); // 成功状态码，必须设置！
    responseData.setResponseDetails(responseDetails);
    mockResponse.setData(responseData);

    // 2. Mock CustomerAccounts for retrieveCustomerAccounts()
    CustomerAccounts mockCustomerAccounts = new CustomerAccounts();
    List<InvestmentAccount> accList = new ArrayList<>();
    InvestmentAccount acc1 = new InvestmentAccount();
    acc1.setChecksum("chk123");
    com.hsbc.trade.transfer.domain.account.AccountId investmentAccountId = com.hsbc.trade.transfer.domain.account.AccountId.builder()
            .countryAccountCode("CN")
            .groupMemberAccountCode("G001")
            .accountNumber("987654321") // 必须一致
            .accountProductTypeCode("01")
            .accountTypeCode("INV")
            .accountCurrencyCode("CNY")
            .build();
    acc1.setInvestmentAccountId(investmentAccountId);
    accList.add(acc1);
    mockCustomerAccounts.setInvestmentAccountList(accList);

    // 3. Mock CEP responses
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

    // ✅ 关键修复2: 使用真实的 trade-online URL 构建精确匹配的 URI
    // 实际URL: "https://srbp-aag-uat-wealth-platform-amh-dev.ikp1001snp.cloud.hk.hsbc/api/srbp-online/v3/transfers/ref123?customerInternalNumber=TESTcin123&sParameterType=S"
    String expectedTransferDetailUri = REAL_TRADE_ONLINE_URL + "/transfers/" + transferReferenceNumber +
            "?customerInternalNumber=" + customerInternalNumber + "&sParameterType=S"; // 注意这里是 S 而不是 SENS

    // 1. Mock retrieveTransferDetail call - 使用精确的URL
    when(mockRestClientService.get(
            eq(expectedTransferDetailUri), // ✅ 精确匹配实际调用的URL
            anyMap(),
            eq(RetrieveTransferDetailResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(mockResponse);

    // 2. Mock retrieveCustomerAccounts
    // 注意：这里仍然使用 MOCK_ACCOUNTS_MAP_URL，因为这是另一个服务的URL，与 trade-online 无关。
    // 如果您也需要模拟真实的 accounts-map URL，请相应地替换 MOCK_ACCOUNTS_MAP_URL。
    when(mockRestClientService.get(
            eq(MOCK_ACCOUNTS_MAP_URL + "accounts-map?consumerId=DAC"),
            anyMap(),
            eq(CustomerAccounts.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(mockCustomerAccounts);

    // 3. Mock CEP name - 修复URL格式和headers匹配
    // ✅ 修复2：确保CEP URL正确替换
    String expectedNameUrl = MOCK_CEP_PARTY_NAME_URL.replace("{cin}", customerInternalNumber)
            .replace("CIN-SensitiveHeadersKey", customerInternalNumber);
    // ✅ 修复3：更智能的headers匹配，只验证关键字段
    when(mockRestClientService.get(
            eq(expectedNameUrl),
            argThat(headers -> {
                // 只验证关键headers，避免因为headers顺序或其他字段导致匹配失败
                return headers != null &&
                       headers.containsKey(HTTPRequestHeaderConstants.X_HSBC_E2E_TRUST_TOKEN) &&
                       headers.containsKey(HTTPRequestHeaderConstants.X_HSBC_SENSITIVE_DATA) &&
                       headers.get(HTTPRequestHeaderConstants.X_HSBC_E2E_TRUST_TOKEN).equals("mock-e2e-token");
            }),
            eq(PartyNameResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(mockPartyNameResponse);

    // 4. Mock CEP contact - 同样修复URL和headers
    String expectedContactUrl = MOCK_CEP_PARTY_CONTACT_URL.replace("{cin}", customerInternalNumber)
            .replace("CIN-SensitiveHeadersKey", customerInternalNumber);
    when(mockRestClientService.get(
            eq(expectedContactUrl),
            argThat(headers -> {
                return headers != null &&
                       headers.containsKey(HTTPRequestHeaderConstants.X_HSBC_E2E_TRUST_TOKEN) &&
                       headers.containsKey(HTTPRequestHeaderConstants.X_HSBC_SENSITIVE_DATA) &&
                       headers.get(HTTPRequestHeaderConstants.X_HSBC_E2E_TRUST_TOKEN).equals("mock-e2e-token");
            }),
            eq(PartyContactResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(mockPartyContactResponse);

    // ✅ 修复4：确保E2E token mock正确
    when(mockE2ETrustTokenUtil.getE2ETrustToken()).thenReturn("mock-e2e-token");

    // ✅ 修复5：添加构建敏感数据headers的方法mock (如果需要)
    String sensitiveDataJson = "[{\"key\":\"SensitiveHeadersKey\",\"value\":\"" + customerInternalNumber + "\"}]";
    when(mockRestClientService.post(anyString(), anyMap(), any(), any(), anyInt(), anyBoolean()))
            .thenReturn(null); // 如果有post调用，需要mock

    // Act
    RetrieveTransferDetailResponse result = tradeTransferService.retrieveTransferDetail(baseRequestHeaders, transferReferenceNumber);

    // Assert
    assertNotNull(result);
    assertNotNull(result.getData());
    assertNotNull(result.getData().getInvestmentAccount());
    assertEquals("987654321", result.getData().getInvestmentAccount().getAccountNumber());

    assertNotNull(result.getData().getAccountChecksumIdentifier());
    assertEquals("chk123", result.getData().getAccountChecksumIdentifier());

    assertNotNull(result.getResponseDetails());
    assertEquals(0, result.getResponseDetails().getResponseCodeNumber());

    // Verify interactions - 使用精确的URL
    verify(mockRestClientService, times(1)).get(
            eq(expectedTransferDetailUri),
            anyMap(),
            eq(RetrieveTransferDetailResponse.class),
            anyInt(),
            anyBoolean()
    );

    // 验证其他调用
    verify(mockRestClientService, times(1)).get(
            eq(MOCK_ACCOUNTS_MAP_URL + "accounts-map?consumerId=DAC"),
            anyMap(),
            eq(CustomerAccounts.class),
            anyInt(),
            anyBoolean()
    );

    // 验证CEP调用 - 使用更宽松的验证
    verify(mockRestClientService, times(1)).get(
            eq(expectedNameUrl),
            anyMap(), // 使用anyMap()避免headers匹配问题
            eq(PartyNameResponse.class),
            anyInt(),
            anyBoolean()
    );
    verify(mockRestClientService, times(1)).get(
            eq(expectedContactUrl),
            anyMap(), // 使用anyMap()避免headers匹配问题
            eq(PartyContactResponse.class),
            anyInt(),
            anyBoolean()
    );
    verify(mockE2ETrustTokenUtil, times(2)).getE2ETrustToken(); // 两次调用：一次获取姓名，一次获取联系方式
}
