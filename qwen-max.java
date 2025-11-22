@Test
void createTransfers_Success() {
    // Arrange
    CreateTransferRequest request = new CreateTransferRequest();
    CreateTransferRequestData data = new CreateTransferRequestData();
    request.setData(data);
    String checksum = "CHECKSUM123";
    request.getData().setSenderInvestmentAccountChecksumIdentifier(checksum);
    request.getData().setActionRequestCode(ActionRequestCode.D);

    List<ReceiverInfo> receivers = new ArrayList<>();
    ReceiverInfo receiver = new ReceiverInfo();
    receiver.setTransferQuantity(new BigDecimal("10.5"));
    receiver.setReceiverCustomerNumber("RECEIVER123");
    receivers.add(receiver);
    request.getData().setReceiverLists(receivers);

    // === 1. Mock retrieveAccountIdWithCheckSum (via customerAccountUrl) ===
    RetrieveCustomerAccountsIdListResponse accountIdListResponse = new RetrieveCustomerAccountsIdListResponse();
    InvestmentAccountIdList accountIdList = new InvestmentAccountIdList();
    InvestmentAccountId accountId = new InvestmentAccountId();
    accountId.setCountryAccountCode("HK");
    accountId.setGroupMemberAccountCode("HBAP");
    accountId.setAccountNumber("123456789");
    accountId.setAccountProductTypeCode("GOLD");
    accountId.setAccountTypeCode("01");
    accountId.setAccountCurrencyCode("HKD");
    accountIdList.setAccountId(accountId);
    accountIdListResponse.setAccountIdList(Collections.singletonList(accountIdList));

    // 注意：这里使用的是 restClientService（被注入的那个），不是 mockRestClientService！
    when(restClientService.get(
            startsWith(MOCK_CUSTOMER_ACCOUNT_URL), // URL 包含 customerAccountUrl + encoded body
            argThat(headers -> checksum.equals(headers.get(TransferQueryParameterConstant.CHECKSUM))),
            eq(RetrieveCustomerAccountsIdListResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(accountIdListResponse);

    // === 2. Mock retrieveCustomerNamesWithCinNumber (sender name) ===
    PartyNameResponse partyNameResponse = new PartyNameResponse();
    PartyName name = new PartyName();
    name.setGivenName("John");
    name.setCustomerChristianName("Michael");
    name.setLastName("Doe");
    partyNameResponse.setName(name);
    when(restClientService.get(
            eq(MOCK_CEP_PARTY_NAME_URL),
            anyMap(),
            eq(PartyNameResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(partyNameResponse);

    // === 3. Mock SRE validation for receivers ===
    RuleResponse sreResponse = new RuleResponse();
    when(sreValidationService.callSreForTransferValidation(
            eq("dac_tokenized_gold_transfer_sender_rule"),
            eq("TESTcin123"), // 来自 baseRequestHeaders 的 X_HSBC_CUSTOMER_ID
            eq("RECEIVER123"),
            anyMap()
    )).thenReturn(sreResponse);

    // === 4. Mock GoldPrice (for ActionRequestCode.D) ===
    GoldPriceResponse goldPriceResponse = new GoldPriceResponse();
    GoldPriceResponseData goldPriceData = new GoldPriceResponseData();
    goldPriceData.setGoldPriceAmount(new BigDecimal("1800.50"));
    goldPriceData.setPublishTime("2025-11-23T10:00:00Z");
    goldPriceResponse.setData(goldPriceData);
    when(restClientService.get(
            eq(MOCK_MDS_GOLD_QUOTES_URL + "/XGTHKD"),
            anyMap(),
            eq(GoldPriceResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(goldPriceResponse);

    // === 5. Mock tradeLimitService ===
    RetrieveTransferLimitResponse limitResponse = new RetrieveTransferLimitResponse();
    RetrieveTransferLimitResponseData limitData = new RetrieveTransferLimitResponseData();
    limitData.setAvailableTodayAmount(new BigDecimal("50000"));
    limitData.setAvailableMonthToDateAmount(new BigDecimal("100000"));
    limitData.setAvailableYearToDateAmount(new BigDecimal("500000"));
    limitData.setMaxDailyLimitedAmount(new BigDecimal("50000"));
    limitData.setMaxMonthlyLimitedAmount(new BigDecimal("100000"));
    limitData.setMaxYearlyLimitedAmount(new BigDecimal("500000"));
    limitResponse.setData(limitData);
    when(tradeLimitService.retrieveLimitations(anyMap())).thenReturn(limitResponse);

    // === 6. Mock final POST to trade-online ===
    CreateTransferResponse mockPostResponse = new CreateTransferResponse();
    ResponseDetails responseDetails = new ResponseDetails();
    responseDetails.setResponseCodeNumber(0);
    mockPostResponse.setResponseDetails(responseDetails);
    when(restClientService.post(
            eq(MOCK_TRADE_ONLINE_URL + "/transfers"),
            anyMap(),
            any(CreateTransferRequest.class),
            eq(CreateTransferResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(mockPostResponse);

    // Act
    CreateTransferResponse result = tradeTransferService.createTransfers(baseRequestHeaders, request);

    // Assert
    assertNotNull(result);
    assertEquals(0, result.getResponseDetails().getResponseCodeNumber());

    // Verify关键调用
    verify(restClientService, times(1)).get(
            argThat(url -> url.startsWith(MOCK_CUSTOMER_ACCOUNT_URL)),
            anyMap(),
            eq(RetrieveCustomerAccountsIdListResponse.class),
            anyInt(),
            anyBoolean()
    );
    verify(restClientService, times(1)).get(
            eq(MOCK_CEP_PARTY_NAME_URL),
            anyMap(),
            eq(PartyNameResponse.class),
            anyInt(),
            anyBoolean()
    );
    verify(restClientService, times(1)).get(
            eq(MOCK_MDS_GOLD_QUOTES_URL + "/XGTHKD"),
            anyMap(),
            eq(GoldPriceResponse.class),
            anyInt(),
            anyBoolean()
    );
    verify(sreValidationService, times(1)).callSreForTransferValidation(
            eq("dac_tokenized_gold_transfer_sender_rule"),
            eq("TESTcin123"),
            eq("RECEIVER123"),
            anyMap()
    );
    verify(tradeLimitService, times(1)).retrieveLimitations(anyMap());
    verify(restClientService, times(1)).post(
            eq(MOCK_TRADE_ONLINE_URL + "/transfers"),
            anyMap(),
            any(CreateTransferRequest.class),
            eq(CreateTransferResponse.class),
            anyInt(),
            anyBoolean()
    );
}
