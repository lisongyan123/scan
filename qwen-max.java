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

    // === ✅ 关键1: mock retrieveAccountIdWithCheckSum 的远程调用 ===
    RetrieveCustomerAccountsIdListResponse mockAccountListResponse = new RetrieveCustomerAccountsIdListResponse();
    InvestmentAccountIdList accountIdList = new InvestmentAccountIdList();
    InvestmentAccountId investmentAccountId = new InvestmentAccountId();
    investmentAccountId.setCountryAccountCode("HK");
    investmentAccountId.setGroupMemberAccountCode("HBAP");
    investmentAccountId.setAccountNumber("123456789");
    investmentAccountId.setAccountProductTypeCode("SAV");
    investmentAccountId.setAccountTypeCode("01");
    investmentAccountId.setAccountCurrencyCode("HKD");
    accountIdList.setAccountId(investmentAccountId);
    mockAccountListResponse.setAccountIdList(Collections.singletonList(accountIdList));

    when(restClientService.get(
            argThat(url -> url != null && url.contains(MOCK_CUSTOMER_ACCOUNT_URL)),
            anyMap(),
            eq(RetrieveCustomerAccountsIdListResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(mockAccountListResponse);

    // === Mock TradeLimitService ===
    RetrieveTransferLimitResponse limitResponse = createMockTransferLimitResponse();
    when(tradeLimitService.retrieveLimitations(anyMap())).thenReturn(limitResponse);

    // === Mock SRE Validation ===
    RuleResponse sreResponse = new RuleResponse();
    when(sreValidationService.callSreForTransferValidation(
            anyString(), anyString(), anyString(), anyMap()
    )).thenReturn(sreResponse);

    // === Mock CEP Name ===
    PartyNameResponse partyNameResponse = createMockPartyNameResponse();
    when(restClientService.get(
            argThat(url -> url != null && url.contains(MOCK_CEP_PARTY_NAME_URL)),
            anyMap(),
            eq(PartyNameResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(partyNameResponse);

    // === Mock GoldPrice (for ActionRequestCode.D) ===
    GoldPriceResponse goldPriceResponse = createMockGoldPriceResponse();
    when(restClientService.get(
            argThat(url -> url != null && url.contains(MOCK_MDS_GOLD_QUOTES_URL)),
            anyMap(),
            eq(GoldPriceResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(goldPriceResponse);

    // === Mock final POST ===
    CreateTransferResponse mockResponse = new CreateTransferResponse();
    CreateTransferResponseData responseData = new CreateTransferResponseData();
    responseData.setTransferOrderLists(Collections.singletonList(new TransferOrderInfo()));
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

    // Verify
    verify(tradeLimitService, times(1)).retrieveLimitations(anyMap());
    verify(restClientService, times(1)).get(
            argThat(url -> url != null && url.contains(MOCK_CUSTOMER_ACCOUNT_URL)),
            anyMap(),
            eq(RetrieveCustomerAccountsIdListResponse.class),
            anyInt(),
            anyBoolean()
    );
    verify(sreValidationService, times(1)).callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap());
    verify(restClientService, times(1)).post(anyString(), anyMap(), any(CreateTransferRequest.class),
            eq(CreateTransferResponse.class), anyInt(), anyBoolean());
}
