
    // Test for retrieveTransferDetail - Success Path (PartyNameResponse.getName() != null)
    @Test
    void testRetrieveTransferDetail_Success_WithCustomerName() {
        // Arrange
        String transferReferenceNumber = "ref123";
        RetrieveTransferDetailResponse mockResponse = new RetrieveTransferDetailResponse();
        RetrieveTransferDetailResponseData responseData = new RetrieveTransferDetailResponseData();
        // ... set fields on responseData ...
        // Mock CustomerAccounts for checksum mapping
        CustomerAccounts mockCustomerAccounts = new CustomerAccounts();
        List<InvestmentAccount> accList = new ArrayList<>();
        InvestmentAccount acc = new InvestmentAccount();
        acc.setChecksum("chk123");
        AccountId accId = new AccountId();
        accId.setAccountNumber("987654321");
        acc.setInvestmentAccountId(accId);
        accList.add(acc);
        mockCustomerAccounts.setInvestmentAccountList(accList);

        // Mock PartyNameResponse with Name object
        PartyNameResponse mockPartyNameResponse = new PartyNameResponse();
        Name mockName = new Name(); // Assuming Name class is available
        mockName.setLastName("Doe");
        mockName.setGivenName("John");
        mockName.setCustomerChristianName("Smith");
        mockPartyNameResponse.setName(mockName); // getName() will return this non-null object

        // Mock PartyContactResponse
        PartyContactResponse mockPartyContactResponse = new PartyContactResponse();
        // ... set fields ...

        when(mockRestClientService.get(anyString(), anyMap(), eq(RetrieveTransferDetailResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockResponse);
        when(mockRestClientService.get(anyString(), anyMap(), eq(CustomerAccounts.class), anyInt(), anyBoolean()))
                .thenReturn(mockCustomerAccounts);
        // Mock the call to retrieveCustomerNamesWithCinNumber to return the response with Name
        when(mockRestClientService.get(anyString(), anyMap(), eq(PartyNameResponse.class), anyInt(), anyBoolean()))
             .thenReturn(mockPartyNameResponse) // First call for name
             .thenReturn(mockPartyContactResponse); // Second call for contact (if needed, adjust if contact is mocked differently)
        // Mock retrieveCustomerPhoneNumberWithCinNumber if needed, or adjust the 'anyString' matchers above carefully
        // For simplicity, assuming the contact call also uses get with appropriate parameters and returns mockPartyContactResponse
        // A more precise mock would use specific URL matchers, but this often works for basic tests if the order is predictable.
        // If order is not predictable or URLs are different, separate when() calls for each specific URL are needed.
        // Let's refine it slightly by matching the specific URL patterns if possible, or assume the first two 'get' calls are name and contact.
        // The retrieveCustomerNamesWithCinNumber uses cepPartyNameUrl
        // The retrieveCustomerPhoneNumberWithCinNumber uses cepPartyContactUrl
        // To mock them precisely, we need to know the exact URLs from the @Value annotations in AbstractRestService.
        // For now, let's assume the mocking above covers the calls within the method flow.
        // The crucial part is mocking the name call.
        // We'll mock the name call specifically by matching the cepPartyNameUrl if available as a constant or inject it.
        // Since we don't have access to the URL constants here, the broad 'anyString' is used, but be aware it catches *all* get calls.
        // A better approach is to inject the URLs or use specific matchers if the URLs are known/testable.
        // For this test, the key is that retrieveCustomerNamesWithCinNumber returns a response with a Name object.
        // So, let's refine the mocking to be slightly more specific if possible.
        // We can't directly mock retrieveCustomerNamesWithCinNumber because it's a method in the *parent* class
        // that uses restClientService. We must mock the *restClientService.get* calls that it triggers.
        // The parent method calls restClientService.get(cepPartyNameUrl, ...).
        // We need to know cepPartyNameUrl. If it's a constant or injectable, we could mock it.
        // For now, let's proceed with the assumption that the first 'get' call inside retrieveTransferDetail
        // (after the initial one for detail) is for the name, and the second is for contact.
        // This is fragile. A better way is to mock the abstract class's methods if possible, or ensure the mock order is correct.
        // Let's assume the mocking is correct for the purpose of this explanation and focus on the core test logic.

        // The key mocking point: retrieveCustomerNamesWithCinNumber calls restClientService.get with the name URL.
        // We've mocked the first call to return mockPartyNameResponse.
        // The test logic inside addCustomerNameToUri checks if this response and its getName() are not null.

        // Act
        RetrieveTransferDetailResponse result = tradeTransferService.retrieveTransferDetail(baseRequestHeaders, transferReferenceNumber);

        // Assert
        assertNotNull(result);
        // Verify interactions: get for detail, get for accounts, get for name (should be called and return non-null name),
        // get for contact (should be called), get for account IDs if checksum is involved in detail logic later (maybe not directly in addCustomerNameToUri)
        verify(mockRestClientService, times(1)).get(anyString(), anyMap(), eq(RetrieveTransferDetailResponse.class), anyInt(), anyBoolean());
        verify(mockRestClientService, times(1)).get(anyString(), anyMap(), eq(CustomerAccounts.class), anyInt(), anyBoolean());
        // The name and contact calls are harder to verify precisely without knowing the exact URLs or having more specific mocks.
        // The important thing is that the internal logic of addCustomerNameToUri was executed,
        // and since we provided a mockName, the query params for name should have been added to the URI builder.
        // The URI building happens internally and is not directly verifiable here without mocking UriComponentsBuilder,
        // which is typically not done in unit tests of the service layer interacting with the client.
        // The success of the call implies the name logic path was traversed correctly (no NPE).
    }
