// ==================== 补充测试用例 ====================

    @Test
    void modifyTransfers_AcceptAction_Success_WithAccountIdRetrieved() {
        // Arrange
        UpdateTransferRequest request = new UpdateTransferRequest();
        UpdateTransferRequestData data = new UpdateTransferRequestData();
        request.setData(data);
        
        request.getData().setTransferActionCode(TransferActionCode.A);
        request.getData().setReceiverInvestmentAccountChecksumIdentifier("CHECKSUM123");
        request.getData().setReceiverCustomerInternalNumber("RECEIVER123");
        
        UpdateTransferResponse mockResponse = new UpdateTransferResponse();
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        mockResponse.setResponseDetails(responseDetails);

        // Mock the SRE validation call
        RuleResponse sreResponse = new RuleResponse();
        when(sreValidationService.callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(sreResponse);

        // Mock the gold price retrieval
        GoldPriceResponse goldPriceResponse = createMockGoldPriceResponse();
        when(restClientService.get(anyString(), anyMap(), eq(GoldPriceResponse.class), anyInt(), anyBoolean()))
                .thenReturn(goldPriceResponse);

        // Mock the retrieveAccountIdWithCheckSum call
        // This is the key missing part: Mocking RetrieveCustomerAccountsIdListResponse
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
        
        when(restClientService.get(anyString(), anyMap(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(accountIdListResponse);

        // Act
        UpdateTransferResponse result = tradeTransferService.modifyTransfers(sourceRequestHeader, request);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getResponseDetails().getResponseCodeNumber());
        // Verify that the accountId was set on the request
        assertNotNull(request.getData().getReceiverInvestmentAccount());
        assertEquals("123456789", request.getData().getReceiverInvestmentAccount().getAccountNumber());
        assertEquals("HK", request.getData().getReceiverInvestmentAccount().getCountryAccountCode());
        assertEquals("HBAP", request.getData().getReceiverInvestmentAccount().getGroupMemberAccountCode());
        // Verify the calls were made
        verify(restClientService, times(1)).get(anyString(), anyMap(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean());
        verify(restClientService, times(1)).put(anyString(), anyMap(), any(UpdateTransferRequest.class), 
                eq(UpdateTransferResponse.class), anyInt(), anyBoolean());
        verify(sreValidationService, times(1)).callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap());
        verify(restClientService, times(1)).get(anyString(), anyMap(), eq(GoldPriceResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void modifyTransfers_AcceptAction_WhenAccountIdRetrievalReturnsNull_ShouldThrowBadRequest() {
        // Arrange
        UpdateTransferRequest request = new UpdateTransferRequest();
        UpdateTransferRequestData data = new UpdateTransferRequestData();
        request.setData(data);
        
        request.getData().setTransferActionCode(TransferActionCode.A);
        request.getData().setReceiverInvestmentAccountChecksumIdentifier("CHECKSUM123");
        request.getData().setReceiverCustomerInternalNumber("RECEIVER123");
        
        // Mock the SRE validation call
        RuleResponse sreResponse = new RuleResponse();
        when(sreValidationService.callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(sreResponse);

        // Mock the gold price retrieval
        GoldPriceResponse goldPriceResponse = createMockGoldPriceResponse();
        when(restClientService.get(anyString(), anyMap(), eq(GoldPriceResponse.class), anyInt(), anyBoolean()))
                .thenReturn(goldPriceResponse);

        // Mock the retrieveAccountIdWithCheckSum call to return null
        when(restClientService.get(anyString(), anyMap(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(null); // This will cause the method to throw BadRequestException

        // Act & Assert
        assertThrows(BadRequestException.class, () -> {
            tradeTransferService.modifyTransfers(sourceRequestHeader, request);
        });
    }

    @Test
    void setSenderNames_WhenPartyNameResponseIsNull_ShouldNotSetNames() {
        // Arrange
        CreateTransferRequest createTransferRequest = new CreateTransferRequest();
        CreateTransferRequestData data = new CreateTransferRequestData();
        createTransferRequest.setData(data);
        
        // Act
        tradeTransferService.setSenderNames(createTransferRequest, null); // Passing null PartyNameResponse

        // Assert
        // The names should remain null as they are not set
        assertNull(createTransferRequest.getData().getSenderCustomerFirstName());
        assertNull(createTransferRequest.getData().getSenderCustomerMiddleName());
        assertNull(createTransferRequest.getData().getSenderCustomerLastName());
    }

    @Test
    void setSenderNames_WhenPartyNameResponseNameIsNull_ShouldNotSetNames() {
        // Arrange
        CreateTransferRequest createTransferRequest = new CreateTransferRequest();
        CreateTransferRequestData data = new CreateTransferRequestData();
        createTransferRequest.setData(data);
        
        PartyNameResponse partyNameResponse = new PartyNameResponse();
        partyNameResponse.setName(null); // Explicitly set name to null

        // Act
        tradeTransferService.setSenderNames(createTransferRequest, partyNameResponse);

        // Assert
        // The names should remain null as the name field is null
        assertNull(createTransferRequest.getData().getSenderCustomerFirstName());
        assertNull(createTransferRequest.getData().getSenderCustomerMiddleName());
        assertNull(createTransferRequest.getData().getSenderCustomerLastName());
    }

    @Test
    void setSenderNames_WhenPartyNameResponseNameIsValid_ShouldSetNames() {
        // Arrange
        CreateTransferRequest createTransferRequest = new CreateTransferRequest();
        CreateTransferRequestData data = new CreateTransferRequestData();
        createTransferRequest.setData(data);
        
        PartyNameResponse partyNameResponse = createMockPartyNameResponse(); // This has a non-null name with values

        // Act
        tradeTransferService.setSenderNames(createTransferRequest, partyNameResponse);

        // Assert
        assertEquals("John", createTransferRequest.getData().getSenderCustomerFirstName());
        assertEquals("Michael", createTransferRequest.getData().getSenderCustomerMiddleName());
        assertEquals("Doe", createTransferRequest.getData().getSenderCustomerLastName());
    }

    @Test
    void setSenderFullName_WhenPartyNameResponseIsNull_ShouldNotSetFullName() {
        // Arrange
        CreateTransferResponse createTransferResponse = new CreateTransferResponse();
        CreateTransferRequestData data = new CreateTransferRequestData();
        createTransferResponse.setData(data);
        
        // Act
        tradeTransferService.setSenderFullName(createTransferResponse, null); // Passing null PartyNameResponse

        // Assert
        assertNull(createTransferResponse.getData().getSenderCustomerName());
    }

    @Test
    void setSenderFullName_WhenPartyNameResponseNameIsNull_ShouldNotSetFullName() {
        // Arrange
        CreateTransferResponse createTransferResponse = new CreateTransferResponse();
        CreateTransferRequestData data = new CreateTransferRequestData();
        createTransferResponse.setData(data);
        
        PartyNameResponse partyNameResponse = new PartyNameResponse();
        partyNameResponse.setName(null); // Explicitly set name to null

        // Act
        tradeTransferService.setSenderFullName(createTransferResponse, partyNameResponse);

        // Assert
        assertNull(createTransferResponse.getData().getSenderCustomerName());
    }

    @Test
    void setSenderFullName_WhenPartyNameResponseNameIsValid_ShouldSetFullName() {
        // Arrange
        CreateTransferResponse createTransferResponse = new CreateTransferResponse();
        CreateTransferRequestData data = new CreateTransferRequestData();
        createTransferResponse.setData(data);
        
        PartyNameResponse partyNameResponse = createMockPartyNameResponse(); // This has a non-null name with values

        // Act
        tradeTransferService.setSenderFullName(createTransferResponse, partyNameResponse);

        // Assert
        // The full name should be "Doe John Michael"
        assertEquals("Doe John Michael", createTransferResponse.getData().getSenderCustomerName());
    }
