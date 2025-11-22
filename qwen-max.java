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

    // === 1. Mock: retrieveAccountIdWithCheckSum → restClientService.get to customerAccountUrl ===
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

    // ✅ 关键：mock restClientService.get 调用（用于 retrieveCustomerAccountIdsList）
    when(restClientService.get(
            argThat(url -> url != null && url.startsWith(MOCK_CUSTOMER_ACCOUNT_URL)),
            anyMap(),
            eq(RetrieveCustomerAccountsIdListResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(accountIdListResponse);

    // === 2. Mock: TradeLimitService ===
    RetrieveTransferLimitResponse limitResponse = createMockTransferLimitResponse();
    when(tradeLimitService.retrieveLimitations(anyMap())).thenReturn(limitResponse);

    // === 3. Mock: SRE Validation ===
    RuleResponse sreResponse = new RuleResponse();
    when(sreValidationService.callSreForTransferValidation(
            eq("dac_tokenized_gold_transfer_sender_rule"),
            eq("CUST123"),
            eq("RECEIVER123"),
            anyMap()
    )).thenReturn(sreResponse);

    // === 4. Mock: CEP Party Name (for sender name) ===
    PartyNameResponse partyNameResponse = createMockPartyNameResponse();
    when(restClientService.get(
            argThat(url -> url != null && url.startsWith(MOCK_CEP_PARTY_NAME_URL)),
            anyMap(),
            eq(PartyNameResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(partyNameResponse);

    // === 5. Mock: GoldPrice (for ActionRequestCode.D) ===
    GoldPriceResponse goldPriceResponse = createMockGoldPriceResponse();
    when(restClientService.get(
            argThat(url -> url != null && url.startsWith(MOCK_MDS_GOLD_QUOTES_URL)),
            anyMap(),
            eq(GoldPriceResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(goldPriceResponse);

    // === 6. Mock: Final POST to trade-online ===
    CreateTransferResponse mockResponse = new CreateTransferResponse();
    CreateTransferResponseData responseData = new CreateTransferResponseData();
    List<TransferOrderInfo> orderList = new ArrayList<>();
    TransferOrderInfo order = new TransferOrderInfo();
    orderList.add(order);
    responseData.setTransferOrderLists(orderList);
    ResponseDetails responseDetails = new ResponseDetails();
    responseDetails.setResponseCodeNumber(0);
    mockResponse.setData(responseData);
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
    assertNotNull(result.getData());
    assertEquals(0, result.getResponseDetails().getResponseCodeNumber());
    assertNotNull(result.getData().getSenderCustomerName());
    assertEquals("Doe John Michael", result.getData().getSenderCustomerName()); // 来自 setSenderFullName

    // Verify关键调用
    verify(tradeLimitService, times(1)).retrieveLimitations(anyMap());
    verify(restClientService, times(1)).get(
            argThat(url -> url != null && url.startsWith(MOCK_CUSTOMER_ACCOUNT_URL)),
            anyMap(),
            eq(RetrieveCustomerAccountsIdListResponse.class),
            anyInt(),
            anyBoolean()
    );
    verify(sreValidationService, times(1)).callSreForTransferValidation(
            eq("dac_tokenized_gold_transfer_sender_rule"),
            eq("CUST123"),
            eq("RECEIVER123"),
            anyMap()
    );
    verify(restClientService, times(1)).get(
            argThat(url -> url != null && url.startsWith(MOCK_CEP_PARTY_NAME_URL)),
            anyMap(),
            eq(PartyNameResponse.class),
            anyInt(),
            anyBoolean()
    );
    verify(restClientService, times(1)).get(
            argThat(url -> url != null && url.startsWith(MOCK_MDS_GOLD_QUOTES_URL)),
            anyMap(),
            eq(GoldPriceResponse.class),
            anyInt(),
            anyBoolean()
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
