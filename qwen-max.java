@Test
void testRetrieveTransferDetail_Success_WithCustomerName() {
    // Arrange
    String transferReferenceNumber = "ref123";
    String customerInternalNumber = "testCIN123"; // Extracted from baseRequestHeaders

    // 1. Mock: retrieveTransferDetail Response
    RetrieveTransferDetailResponse mockResponse = new RetrieveTransferDetailResponse();
    RetrieveTransferDetailResponseData responseData = new RetrieveTransferDetailResponseData();
    // ✅ 关键修复1：设置一个非空的 AccountId
    AccountId accountId = new AccountId();
    accountId.setAccountNumber("987654321"); // 这个值必须和下面 mock 的 InvestmentAccount 一致
    responseData.setInvestmentAccount(accountId);
    // ✅ 关键修复2：设置 responseDetails！这是 ResponseInfoHandler.prepareResponse 所必需的
    ResponseDetails responseDetails = new ResponseDetails();
    responseDetails.setResponseCodeNumber(0); // ✅ 成功状态码，必须设置！
    responseData.setResponseDetails(responseDetails); // ✅ 将 responseDetails 设置到 data 中
    mockResponse.setData(responseData); // ✅ 设置 data

    // 2. ✅ 关键：Mock CustomerAccounts for retrieveCustomerAccounts() - 这是之前缺失的！
    CustomerAccounts mockCustomerAccounts = new CustomerAccounts();
    List<InvestmentAccount> accList = new ArrayList<>();
    InvestmentAccount acc1 = new InvestmentAccount();
    acc1.setChecksum("chk123");
    com.hsbc.trade.transfer.domain.account.AccountId investmentAccountId = com.hsbc.trade.transfer.domain.account.AccountId.builder()
            .countryAccountCode("CN")
            .groupMemberAccountCode("G001")
            .accountNumber("987654321") // ✅ 必须一致
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

    // ✅ 关键修改：构造与实际调用完全一致的 URI（transferReferenceNumber 已替换）
    String expectedTransferDetailUri = MOCK_TRADE_ONLINE_URL + "/transfers/" + transferReferenceNumber +
            "?customerInternalNumber=" + customerInternalNumber + "&sParameterType=SENS";

    // 1. Mock retrieveTransferDetail call —— 使用具体 URI
    when(mockRestClientService.get(
            eq(expectedTransferDetailUri), // ✅ 修改点：使用已解析的 URI
            anyMap(),
            eq(RetrieveTransferDetailResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(mockResponse);

    // 2. Mock retrieveCustomerAccounts
    when(mockRestClientService.get(
            eq(MOCK_ACCOUNTS_MAP_URL + "accounts-map?consumerId=DAC"),
            anyMap(),
            eq(CustomerAccounts.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(mockCustomerAccounts);

    // 3. Mock CEP name
    String sensitiveDataJson = "[{\"key\":\"SensitiveHeadersKey\",\"value\":\"" + customerInternalNumber + "\"}]";
    String base64SensitiveData = Base64.getEncoder().encodeToString(sensitiveDataJson.getBytes(StandardCharsets.UTF_8));
    Map<String, String> expectedHeadersForCEP = new HashMap<>();
    expectedHeadersForCEP.putAll(baseRequestHeaders);
    expectedHeadersForCEP.remove(HTTPRequestHeaderConstants.X_HSBC_SAML);
    expectedHeadersForCEP.remove(HTTPRequestHeaderConstants.X_HSBC_SAML3);
    expectedHeadersForCEP.put(HTTPRequestHeaderConstants.X_HSBC_E2E_TRUST_TOKEN, "mock-e2e-token");
    expectedHeadersForCEP.put(HTTPRequestHeaderConstants.X_HSBC_GBGF, "");
    expectedHeadersForCEP.put(HTTPRequestHeaderConstants.X_HSBC_SOURCE_SYSTEM_ID, "");
    expectedHeadersForCEP.put(HTTPRequestHeaderConstants.X_HSBC_SENSITIVE_DATA, base64SensitiveData);

    String expectedNameUrl = MOCK_CEP_PARTY_NAME_URL.replace("CIN-SensitiveHeadersKey", customerInternalNumber);
    when(mockRestClientService.get(
            eq(expectedNameUrl),
            argThat(headers -> headers.equals(expectedHeadersForCEP)),
            eq(PartyNameResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(mockPartyNameResponse);

    // 4. Mock CEP contact
    String expectedContactUrl = MOCK_CEP_PARTY_CONTACT_URL.replace("CIN-SensitiveHeadersKey", customerInternalNumber);
    when(mockRestClientService.get(
            eq(expectedContactUrl),
            argThat(headers -> headers.equals(expectedHeadersForCEP)),
            eq(PartyContactResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(mockPartyContactResponse);

    when(mockE2ETrustTokenUtil.getE2ETrustToken()).thenReturn("mock-e2e-token");

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

    // Verify interactions
    verify(mockRestClientService, times(1)).get(
            eq(expectedTransferDetailUri), // ✅ 验证使用的是具体 URI
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
            argThat(headers -> headers.equals(expectedHeadersForCEP)),
            eq(PartyNameResponse.class),
            anyInt(),
            anyBoolean()
    );
    verify(mockRestClientService, times(1)).get(
            eq(expectedContactUrl),
            argThat(headers -> headers.equals(expectedHeadersForCEP)),
            eq(PartyContactResponse.class),
            anyInt(),
            anyBoolean()
    );
    verify(mockE2ETrustTokenUtil, times(2)).getE2ETrustToken();
}
