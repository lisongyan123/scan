    @Test
    void testRetrieveTransferDetail_Success_WithCustomerName() {
        // Arrange
        String transferReferenceNumber = "ref123";
        String customerInternalNumber = "testCIN123"; // Extracted from baseRequestHeaders

        // 1. Mock: retrieveTransferDetail Response
        RetrieveTransferDetailResponse mockResponse = new RetrieveTransferDetailResponse();
        RetrieveTransferDetailResponseData responseData = new RetrieveTransferDetailResponseData();
        responseData.setInvestmentAccount(new AccountId()); // Ensure investmentAccount is not null for later processing
        mockResponse.setData(responseData);

        // 2. Mock: CustomerAccounts for extractAccountIdMap (must be valid to pass the check)
        CustomerAccounts mockCustomerAccounts = new CustomerAccounts();
        List<InvestmentAccount> accList = new ArrayList<>();
        InvestmentAccount acc1 = new InvestmentAccount();
        acc1.setChecksum("chk123");
        com.hsbc.trade.transfer.domain.InvestmentAccountId rawAccId1 = new com.hsbc.trade.transfer.domain.InvestmentAccountId(); // Correct raw type from domain
        rawAccId1.setCountryAccountCode("HK");
        rawAccId1.setGroupMemberAccountCode("HSBC");
        rawAccId1.setAccountNumber("987654321");
        rawAccId1.setAccountProductTypeCode("GOLD");
        rawAccId1.setAccountTypeCode("PHYS");
        rawAccId1.setAccountCurrencyCode("HKD");
        acc1.setInvestmentAccountId(rawAccId1);
        accList.add(acc1);
        mockCustomerAccounts.setInvestmentAccountList(accList); // Now the list is not empty and contains valid objects

        // 3. Mock: PartyNameResponse for addCustomerNameToUri (getName() != null path)
        PartyNameResponse mockPartyNameResponse = new PartyNameResponse();
        PartyName mockName = new PartyName(); // Assuming PartyName class is available
        mockName.setLastName("Doe");
        mockName.setGivenName("John");
        mockName.setCustomerChristianName("Smith");
        mockPartyNameResponse.setName(mockName); // getName() will return this non-null object

        // 4. Mock: PartyContactResponse for addCustomerContactToUri (getContact() != null path)
        PartyContactResponse mockPartyContactResponse = new PartyContactResponse();
        PartyContact mockContact = new PartyContact(); // Assuming PartyContact class is available
        mockContact.setMobileNumber1("123456789");
        mockPartyContactResponse.setContact(mockContact); // getContact() will return this non-null object

        // --- CRITICAL: Mock the specific restClientService.get calls with exact URLs ---
        // 1. Call for retrieveTransferDetail itself
        when(mockRestClientService.get(
                eq(MOCK_TRADE_ONLINE_URL + "/transfers/{transferReferenceNumber}?customerInternalNumber=" + customerInternalNumber + "&sParameterType=SENS"), // This is how UriComponentsBuilder builds it with the number
                anyMap(), // request headers rebuilt
                eq(RetrieveTransferDetailResponse.class),
                anyInt(), // timeout
                anyBoolean() // printMessageLog
        )).thenReturn(mockResponse);

        // 2. Call for retrieveCustomerAccounts (inside retrieveTransferDetail)
        when(mockRestClientService.get(
                eq(MOCK_ACCOUNTS_MAP_URL + "accounts-map?consumerId=DAC"), // Built URI string
                anyMap(), // request headers rebuilt
                eq(CustomerAccounts.class),
                anyInt(), // timeout
                anyBoolean() // printMessageLog
        )).thenReturn(mockCustomerAccounts);

        // 3. Call for retrieveCustomerNamesWithCinNumber (inside addCustomerNameToUri)
        // The URL is built inside retrieveCustomerNamesWithCinNumber using cepPartyNameUrl.
        // It replaces "CIN-SensitiveHeadersKey" with the actual CIN.
        // Mock the URL after the replacement: cepPartyNameUrl.replace("CIN-SensitiveHeadersKey", cinNumber)
        String expectedNameUrl = MOCK_CEP_PARTY_NAME_URL.replace("CIN-SensitiveHeadersKey", customerInternalNumber);
        when(mockRestClientService.get(
                eq(expectedNameUrl),
                anyMap(), // headers with E2E token (from updateHeaderforCEP)
                eq(PartyNameResponse.class),
                anyInt(), // timeout
                anyBoolean() // printMessageLog
        )).thenReturn(mockPartyNameResponse);

        // 4. Call for retrieveCustomerPhoneNumberWithCinNumber (inside addCustomerContactToUri)
        // Similarly, mock the URL after CIN replacement.
        String expectedContactUrl = MOCK_CEP_PARTY_CONTACT_URL.replace("CIN-SensitiveHeadersKey", customerInternalNumber);
        when(mockRestClientService.get(
                eq(expectedContactUrl),
                anyMap(), // headers with E2E token (from updateHeaderforCEP)
                eq(PartyContactResponse.class),
                anyInt(), // timeout
                anyBoolean() // printMessageLog
        )).thenReturn(mockPartyContactResponse);

        // Mock E2E token generation for updateHeaderforCEP calls (used by addCustomerNameToUri and addCustomerContactToUri)
        when(mockE2ETrustTokenUtil.getE2ETrustToken()).thenReturn("mock-e2e-token");

        // Mock retrieveCustomerProfilesService.getCIN if needed by buildRequestHeaders (though CIN is already in baseRequestHeaders)
        // when(mockRetrieveCustomerProfilesService.getCIN(anyMap())).thenReturn("fallback-cin"); // Not needed if CIN is in baseRequestHeaders

        // Act
        RetrieveTransferDetailResponse result = tradeTransferService.retrieveTransferDetail(baseRequestHeaders, transferReferenceNumber);

        // Assert
        assertNotNull(result);
        // Verify the specific interactions happened
        verify(mockRestClientService, times(1)).get(
                eq(MOCK_TRADE_ONLINE_URL + "/transfers/{transferReferenceNumber}?customerInternalNumber=" + customerInternalNumber + "&sParameterType=SENS"),
                anyMap(),
                eq(RetrieveTransferDetailResponse.class),
                anyInt(),
                anyBoolean()
        );
        verify(mockRestClientService, times(1)).get(
                eq(MOCK_ACCOUNTS_MAP_URL + "accounts-map?consumerId=DAC"),
                anyMap(),
                eq(CustomerAccounts.class),
                anyInt(),
                anyBoolean()
        );
        verify(mockRestClientService, times(1)).get(
                eq(expectedNameUrl),
                anyMap(),
                eq(PartyNameResponse.class),
                anyInt(),
                anyBoolean()
        );
        verify(mockRestClientService, times(1)).get(
                eq(expectedContactUrl),
                anyMap(),
                eq(PartyContactResponse.class),
                anyInt(),
                anyBoolean()
        );
        // Verify E2E token was fetched twice (once for name, once for contact)
        verify(mockE2ETrustTokenUtil, times(2)).getE2ETrustToken();
    }
