@Test
void createTransfers_Success() {
    // Arrange
    CreateTransferRequest request = new CreateTransferRequest();
    CreateTransferRequestData data = new CreateTransferRequestData();
    request.setData(data);
    request.getData().setSenderInvestmentAccountChecksumIdentifier("CHECKSUM123");
    request.getData().setActionRequestCode(ActionRequestCode.D);

    List<ReceiverInfo> receivers = new ArrayList<>();
    ReceiverInfo receiver = new ReceiverInfo();
    receiver.setTransferQuantity(new BigDecimal("10.5"));
    receiver.setReceiverCustomerNumber("RECEIVER123");
    receivers.add(receiver);
    request.getData().setReceiverLists(receivers);

    // ✅ 1. Mock retrieveAccountIdWithCheckSum (via restClientService.get to customerAccountUrl)
    RetrieveCustomerAccountsIdListResponse accountIdListResponse = new RetrieveCustomerAccountsIdListResponse();
    InvestmentAccountId accountId = new InvestmentAccountId();
    accountId.setCountryAccountCode("HK");
    accountId.setGroupMemberAccountCode("HBAP");
    accountId.setAccountNumber("123456789");
    accountId.setAccountProductTypeCode("SAV");
    accountId.setAccountTypeCode("01");
    accountId.setAccountCurrencyCode("HKD");

    InvestmentAccountIdList accountIdList = new InvestmentAccountIdList();
    accountIdList.setAccountId(accountId);
    accountIdListResponse.setAccountIdList(Collections.singletonList(accountIdList));

    when(restClientService.get(
            argThat(url -> url.contains(MOCK_CUSTOMER_ACCOUNT_URL)),
            anyMap(),
            eq(RetrieveCustomerAccountsIdListResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(accountIdListResponse);

    // ✅ 2. Mock retrieveCustomerNamesWithCinNumber (required by setSenderNames)
    PartyNameResponse partyNameResponse = new PartyNameResponse();
    PartyName name = new PartyName();
    name.setGivenName("John");
    name.setCustomerChristianName("Michael");
    name.setLastName("Doe");
    partyNameResponse.setName(name);
    when(restClientService.get(
            argThat(url -> url.contains(MOCK_CEP_PARTY_NAME_URL)),
            anyMap(),
            eq(PartyNameResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(partyNameResponse);

    // ✅ 3. Mock SRE validation for receivers (关键！)
    RuleResponse sreResponse = new RuleResponse();
    // 注意：validateSreForReceivers 会为每个 receiver 调用一次 callSreForTransferValidation
    // sender = sourceRequestHeader.get(X_HSBC_CUSTOMER_ID) = "CUST123"
    // receiver = "RECEIVER123"
    // rule = "dac_tokenized_gold_transfer_sender_rule"
    when(sreValidationService.callSreForTransferValidation(
            eq("dac_tokenized_gold_transfer_sender_rule"),
            eq("CUST123"),
            eq("RECEIVER123"),
            anyMap() // headersWithE2ETrustToken
    )).thenReturn(sreResponse);

    // ✅ 4. Mock GoldPrice (for ActionRequestCode.D)
    GoldPriceResponse goldPriceResponse = createMockGoldPriceResponse();
    when(restClientService.get(
            argThat(url -> url.contains(MOCK_MDS_GOLD_QUOTES_URL)),
            anyMap(),
            eq(GoldPriceResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(goldPriceResponse);

    // ✅ 5. Mock tradeLimitService
    RetrieveTransferLimitResponse limitResponse = createMockTransferLimitResponse();
    when(tradeLimitService.retrieveLimitations(anyMap())).thenReturn(limitResponse);

    // ✅ 6. Mock final post to trade-online
    CreateTransferResponse mockResponse = new CreateTransferResponse();
    ResponseDetails responseDetails = new ResponseDetails();
    responseDetails.setResponseCodeNumber(0);
    mockResponse.setResponseDetails(responseDetails);
    when(restClientService.post(
            anyString(),
            anyMap(),
            any(CreateTransferRequest.class),
            eq(CreateTransferResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(mockResponse);

    // Act
    CreateTransferResponse result = tradeTransferService.createTransfers(sourceRequestHeader, request);

    // Assert
    assertNotNull(result);
    assertEquals(0, result.getResponseDetails().getResponseCodeNumber());

    // ✅ Verify all critical calls
    verify(tradeLimitService, times(1)).retrieveLimitations(anyMap());
    verify(restClientService, times(1)).get(
            argThat(url -> url.contains(MOCK_CUSTOMER_ACCOUNT_URL)),
            anyMap(),
            eq(RetrieveCustomerAccountsIdListResponse.class),
            anyInt(),
            anyBoolean()
    );
    verify(restClientService, times(1)).get(
            argThat(url -> url.contains(MOCK_CEP_PARTY_NAME_URL)),
            anyMap(),
            eq(PartyNameResponse.class),
            anyInt(),
            anyBoolean()
    );
    verify(restClientService, times(1)).get(
            argThat(url -> url.contains(MOCK_MDS_GOLD_QUOTES_URL)),
            anyMap(),
            eq(GoldPriceResponse.class),
            anyInt(),
            anyBoolean()
    );
    verify(sreValidationService, times(1)).callSreForTransferValidation(
            eq("dac_tokenized_gold_transfer_sender_rule"),
            eq("CUST123"),
            eq("RECEIVER123"),
            anyMap()
    );
    verify(restClientService, times(1)).post(
            anyString(),
            anyMap(),
            any(CreateTransferRequest.class),
            eq(CreateTransferResponse.class),
            anyInt(),
            anyBoolean()
    );
}
