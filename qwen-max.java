@Test
void testRetrieveTransferDetail_Success_WithCustomerName() {
    // Arrange
    String transferReferenceNumber = "ref123";
    String customerInternalNumber = "TESTcin123";

    // 1. Mock trade-online response
    RetrieveTransferDetailResponse mockResponse = new RetrieveTransferDetailResponse();
    RetrieveTransferDetailResponseData responseData = new RetrieveTransferDetailResponseData();
    AccountId accountId = new AccountId();
    accountId.setAccountNumber("987654321");
    responseData.setInvestmentAccount(accountId);
    ResponseDetails responseDetails = new ResponseDetails();
    responseDetails.setResponseCodeNumber(0);
    responseData.setResponseDetails(responseDetails);
    mockResponse.setData(responseData);

    // 2. Mock accounts-map (for checksum)
    CustomerAccounts mockCustomerAccounts = new CustomerAccounts();
    InvestmentAccount acc = new InvestmentAccount();
    acc.setChecksum("chk123");
    com.hsbc.trade.transfer.domain.account.AccountId invAccountId = 
        com.hsbc.trade.transfer.domain.account.AccountId.builder()
            .accountNumber("987654321")
            .countryAccountCode("HK")
            .groupMemberAccountCode("HBAP")
            .accountProductTypeCode("GOLD")
            .accountTypeCode("01")
            .accountCurrencyCode("HKD")
            .build();
    acc.setInvestmentAccountId(invAccountId);
    mockCustomerAccounts.setInvestmentAccountList(Collections.singletonList(acc));

    // 3. Mock CEP name response
    PartyNameResponse mockPartyNameResponse = new PartyNameResponse();
    PartyName name = new PartyName();
    name.setLastName("Doe");
    name.setGivenName("John");
    name.setCustomerChristianName("Smith");
    mockPartyNameResponse.setName(name);

    // 4. Build expected CEP headers
    Map<String, String> built = tradeTransferService.buildRequestHeaders(baseRequestHeaders);
    String sensitiveJson = "[{\"key\":\"SensitiveHeadersKey\",\"value\":\"" + customerInternalNumber + "\"}]";
    String base64 = Base64.getEncoder().encodeToString(sensitiveJson.getBytes(StandardCharsets.UTF_8));
    built.put(HTTPRequestHeaderConstants.X_HSBC_SENSITIVE_DATA, base64);

    Map<String, String> expectedCepHeaders = new HashMap<>(built);
    expectedCepHeaders.remove(HTTPRequestHeaderConstants.X_HSBC_SAML);
    expectedCepHeaders.remove(HTTPRequestHeaderConstants.X_HSBC_SAML3);
    expectedCepHeaders.put(HTTPRequestHeaderConstants.X_HSBC_E2E_TRUST_TOKEN, "mock-e2e-token");
    expectedCepHeaders.put(HTTPRequestHeaderConstants.X_HSBC_GBGF, "");
    expectedCepHeaders.put(HTTPRequestHeaderConstants.X_HSBC_SOURCE_SYSTEM_ID, "");

    // 5. Mock E2E token
    when(e2ETrustTokenUtil.getE2ETrustToken()).thenReturn("mock-e2e-token");

    // 6. Mock all restClientService calls
    when(restClientService.get(
            eq(MOCK_TRADE_ONLINE_URL + "/transfers/ref123?customerInternalNumber=TESTcin123&sParameterType=SENS"),
            anyMap(),
            eq(RetrieveTransferDetailResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(mockResponse);

    when(restClientService.get(
            eq(MOCK_ACCOUNTS_MAP_URL + "accounts-map?consumerId=DAC"),
            anyMap(),
            eq(CustomerAccounts.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(mockCustomerAccounts);

    // ✅ 关键：Mock CEP name call with expected headers
    when(restClientService.get(
            eq(MOCK_CEP_PARTY_NAME_URL.replace("CIN-SensitiveHeadersKey", customerInternalNumber)),
            argThat(h -> h.equals(expectedCepHeaders)),
            eq(PartyNameResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(mockPartyNameResponse);

    // Act
    RetrieveTransferDetailResponse result = tradeTransferService.retrieveTransferDetail(baseRequestHeaders, transferReferenceNumber);

    // Assert
    assertNotNull(result);
    assertEquals("Doe", result.getData().getSenderCustomerLastName()); // ✅ 来自 CEP
    assertEquals("John", result.getData().getSenderCustomerFirstName());
    assertEquals("Smith", result.getData().getSenderCustomerMiddleName());
}
