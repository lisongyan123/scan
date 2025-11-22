@Test
void createTransfers_Success() {
    // Arrange
    CreateTransferRequest request = new CreateTransferRequest();
    CreateTransferRequestData data = new CreateTransferRequestData();
    request.setData(data);
    // Set sender checksum
    request.getData().setSenderInvestmentAccountChecksumIdentifier("CHECKSUM123");
    request.getData().setActionRequestCode(ActionRequestCode.D);

    // Create receiver list
    List<ReceiverInfo> receivers = new ArrayList<>();
    ReceiverInfo receiver = new ReceiverInfo();
    receiver.setTransferQuantity(new BigDecimal("10.5"));
    receiver.setReceiverCustomerNumber("RECEIVER123");
    receivers.add(receiver);
    request.getData().setReceiverLists(receivers);

    // ✅ Mock retrieveCustomerAccountIdsList 调用（关键！）
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

    // Mock the restClientService.get for customerAccountUrl
    when(restClientService.get(
            argThat(url -> url.contains(MOCK_CUSTOMER_ACCOUNT_URL)),
            anyMap(),
            eq(RetrieveCustomerAccountsIdListResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(accountIdListResponse);

    // Mock other external calls
    CreateTransferResponse mockResponse = new CreateTransferResponse();
    ResponseDetails responseDetails = new ResponseDetails();
    responseDetails.setResponseCodeNumber(0);
    mockResponse.setResponseDetails(responseDetails);

    RetrieveTransferLimitResponse limitResponse = createMockTransferLimitResponse();
    GoldPriceResponse goldPriceResponse = createMockGoldPriceResponse();
    RuleResponse sreResponse = new RuleResponse();

    when(tradeLimitService.retrieveLimitations(anyMap())).thenReturn(limitResponse);
    when(restClientService.post(anyString(), anyMap(), any(CreateTransferRequest.class),
            eq(CreateTransferResponse.class), anyInt(), anyBoolean())).thenReturn(mockResponse);
    when(sreValidationService.callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap()))
            .thenReturn(sreResponse);
    when(restClientService.get(anyString(), anyMap(), eq(GoldPriceResponse.class), anyInt(), anyBoolean()))
            .thenReturn(goldPriceResponse); // mock GoldPrice call if ActionRequestCode == D

    // Act
    CreateTransferResponse result = tradeTransferService.createTransfers(sourceRequestHeader, request);

    // Assert
    assertNotNull(result);
    assertEquals(0, result.getResponseDetails().getResponseCodeNumber());
    verify(tradeLimitService, times(1)).retrieveLimitations(anyMap());
    verify(restClientService, times(1)).get(
            argThat(url -> url.contains(MOCK_CUSTOMER_ACCOUNT_URL)),
            anyMap(),
            eq(RetrieveCustomerAccountsIdListResponse.class),
            anyInt(),
            anyBoolean()
    );
    verify(restClientService, times(1)).post(anyString(), anyMap(), any(CreateTransferRequest.class),
            eq(CreateTransferResponse.class), anyInt(), anyBoolean());
}
