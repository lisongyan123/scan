@Test
void testRetrieveTransferDetail_Success_WithCustomerName() {
    // Arrange
    String transferReferenceNumber = "ref123";
    String customerInternalNumber = baseRequestHeaders.get(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID); // ← 改为从 baseRequestHeaders 读取

    // 1. Mock: retrieveTransferDetail Response
    RetrieveTransferDetailResponse mockResponse = new RetrieveTransferDetailResponse();
    RetrieveTransferDetailResponseData responseData = new RetrieveTransferDetailResponseData();

    AccountId accountId = new AccountId();
    accountId.setAccountNumber("987654321");
    responseData.setInvestmentAccount(accountId);

    ResponseDetails responseDetails = new ResponseDetails();
    responseDetails.setResponseCodeNumber(0);
    responseData.setResponseDetails(responseDetails);
    mockResponse.setData(responseData);

    // 2. Mock CustomerAccounts
    CustomerAccounts mockCustomerAccounts = new CustomerAccounts();
    InvestmentAccount acc1 = new InvestmentAccount();
    acc1.setChecksum("chk123");
    com.hsbc.trade.transfer.domain.account.AccountId investmentAccountId = 
        com.hsbc.trade.transfer.domain.account.AccountId.builder()
            .countryAccountCode("CN")
            .groupMemberAccountCode("G001")
            .accountNumber("987654321")
            .accountProductTypeCode("01")
            .accountTypeCode("INV")
            .accountCurrencyCode("CNY")
            .build();
    acc1.setInvestmentAccountId(investmentAccountId);
    mockCustomerAccounts.setInvestmentAccountList(Collections.singletonList(acc1));

    // 3. Mock CEP responses
    PartyNameResponse mockPartyNameResponse = createMockPartyNameResponse();
    PartyContactResponse mockPartyContactResponse = new PartyContactResponse();
    PartyContact contact = new PartyContact();
    contact.setMobileNumber1("123456789");
    mockPartyContactResponse.setContact(contact);

    // --- Build expected URLs ---
    String expectedMainUrl = MOCK_TRADE_ONLINE_URL + "/transfers/" + transferReferenceNumber +
            "?customerInternalNumber=" + customerInternalNumber + "&sParameterType=SENS";

    // Mock main call → ✅ 这是你要求的核心 mock
    when(restClientService.get(
            eq(expectedMainUrl),
            anyMap(),
            eq(RetrieveTransferDetailResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(mockResponse);

    // Mock accounts map
    when(restClientService.get(
            eq(MOCK_ACCOUNTS_MAP_URL + "accounts-map?consumerId=DAC"),
            anyMap(),
            eq(CustomerAccounts.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(mockCustomerAccounts);

    // Mock CEP name & contact
    String sensitiveDataJson = "[{\"key\":\"SensitiveHeadersKey\",\"value\":\"" + customerInternalNumber + "\"}]";
    String base64SensitiveData = Base64.getEncoder().encodeToString(sensitiveDataJson.getBytes(StandardCharsets.UTF_8));
    Map<String, String> expectedHeadersForCEP = new HashMap<>(baseRequestHeaders);
    expectedHeadersForCEP.remove(HTTPRequestHeaderConstants.X_HSBC_SAML);
    expectedHeadersForCEP.remove(HTTPRequestHeaderConstants.X_HSBC_SAML3);
    expectedHeadersForCEP.put(HTTPRequestHeaderConstants.X_HSBC_E2E_TRUST_TOKEN, "e2e-token");
    expectedHeadersForCEP.put(HTTPRequestHeaderConstants.X_HSBC_GBGF, "");
    expectedHeadersForCEP.put(HTTPRequestHeaderConstants.X_HSBC_SOURCE_SYSTEM_ID, "");
    expectedHeadersForCEP.put(HTTPRequestHeaderConstants.X_HSBC_SENSITIVE_DATA, base64SensitiveData);

    String expectedNameUrl = MOCK_CEP_PARTY_NAME_URL.replace("CIN-SensitiveHeadersKey", customerInternalNumber);
    String expectedContactUrl = MOCK_CEP_PARTY_CONTACT_URL.replace("CIN-SensitiveHeadersKey", customerInternalNumber);

    when(restClientService.get(
            eq(expectedNameUrl),
            argThat(headers -> headers.equals(expectedHeadersForCEP)),
            eq(PartyNameResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(mockPartyNameResponse);

    when(restClientService.get(
            eq(expectedContactUrl),
            argThat(headers -> headers.equals(expectedHeadersForCEP)),
            eq(PartyContactResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(mockPartyContactResponse);

    when(e2ETrustTokenUtil.getE2ETrustToken()).thenReturn("e2e-token");

    // Act
    RetrieveTransferDetailResponse result = tradeTransferService.retrieveTransferDetail(
            baseRequestHeaders, transferReferenceNumber);

    // Assert
    assertNotNull(result);
    assertNotNull(result.getData());
    assertEquals("987654321", result.getData().getInvestmentAccount().getAccountNumber());
    assertEquals("chk123", result.getData().getAccountChecksumIdentifier());
    assertEquals(0, result.getResponseDetails().getResponseCodeNumber());

    // Verify
    verify(restClientService).get(eq(expectedMainUrl), anyMap(), eq(RetrieveTransferDetailResponse.class), anyInt(), anyBoolean());
    verify(restClientService).get(eq(MOCK_ACCOUNTS_MAP_URL + "accounts-map?consumerId=DAC"), anyMap(), eq(CustomerAccounts.class), anyInt(), anyBoolean());
    verify(restClientService).get(eq(expectedNameUrl), anyMap(), eq(PartyNameResponse.class), anyInt(), anyBoolean());
    verify(restClientService).get(eq(expectedContactUrl), anyMap(), eq(PartyContactResponse.class), anyInt(), anyBoolean());
    verify(e2ETrustTokenUtil, times(2)).getE2ETrustToken();
}
