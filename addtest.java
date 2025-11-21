@Test
void modifyTransfers_AcceptAction_Success_WithAccountIdRetrieved() {
    // Arrange
    UpdateTransferRequest request = new UpdateTransferRequest();
    UpdateTransferRequestData data = new UpdateTransferRequestData();
    request.setData(data);
    
    request.getData().setTransferActionCode(TransferActionCode.A); // 关键：A操作会调用 retrieveAccountIdWithCheckSum
    request.getData().setReceiverInvestmentAccountChecksumIdentifier("CHECKSUM123"); // 这个会被 put 进 requestHeaders
    
    // 创建一个最小化的、能通过 if (response != null && response.getAccountIdList() != null && !response.getAccountIdList().isEmpty()) 的响应
    RetrieveCustomerAccountsIdListResponse mockResponse = new RetrieveCustomerAccountsIdListResponse();
    InvestmentAccountIdList accountIdList = new InvestmentAccountIdList();
    
    // 创建一个最小化的 AccountId，它只需要有 accountNumber 和 countryAccountCode 就够了
    InvestmentAccountId accountId = new InvestmentAccountId();
    accountId.setAccountNumber("123456789"); // 这是被测代码最终会 set 到 updateTransferRequest 中的值
    accountId.setCountryAccountCode("HK");
    accountId.setGroupMemberAccountCode("HBAP");
    
    accountIdList.setAccountId(accountId); // 将 accountId 设置到 accountIdList 中
    mockResponse.setAccountIdList(Collections.singletonList(accountIdList)); // 将 accountIdList 设置到 response 中，确保列表不为空
    
    // Mock 调用：当 restClientService.get 被调用时，无论传入什么参数，都返回上面创建的 mockResponse
    when(restClientService.get(anyString(), anyMap(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean()))
            .thenReturn(mockResponse);

    // 为其他依赖设置 Mock (避免 NPE)
    RuleResponse sreResponse = new RuleResponse();
    ResponseDetails sreDetails = new ResponseDetails();
    sreDetails.setResponseCodeNumber(0);
    sreResponse.setResponseDetails(sreDetails);
    when(sreValidationService.callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap()))
            .thenReturn(sreResponse);

    GoldPriceResponse goldPriceResponse = createMockGoldPriceResponse();
    when(restClientService.get(anyString(), anyMap(), eq(GoldPriceResponse.class), anyInt(), anyBoolean()))
            .thenReturn(goldPriceResponse);

    // Act
    UpdateTransferResponse result = tradeTransferService.modifyTransfers(sourceRequestHeader, request);

    // Assert
    assertNotNull(result);
    assertEquals(0, result.getResponseDetails().getResponseCodeNumber());
    // 验证核心逻辑：updateTransferRequest 中的 receiverInvestmentAccount 是否被正确设置
    assertNotNull(request.getData().getReceiverInvestmentAccount());
    assertEquals("123456789", request.getData().getReceiverInvestmentAccount().getAccountNumber());
    assertEquals("HK", request.getData().getReceiverInvestmentAccount().getCountryAccountCode());
}
