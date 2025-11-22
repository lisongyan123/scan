// ... (其他 imports 保持不变)
import com.hsbc.trade.transfer.domain.account.InvestmentAccount; // 确保导入 InvestmentAccount
import com.hsbc.trade.transfer.domain.account.InvestmentAccountId; // 确保导入 InvestmentAccountId

// ... (其他测试方法保持不变)

    @Test
    void retrieveTransferList() {
        // --- Arrange ---
        // Mock retrieveCustomerAccounts to return a CustomerAccounts object with a non-empty list
        CustomerAccounts mockCustomerAccounts = new CustomerAccounts();
        List<InvestmentAccount> investmentAccountList = new ArrayList<>();
        InvestmentAccount mockAccount = new InvestmentAccount();
        mockAccount.setChecksum("CHECKSUM123");
        InvestmentAccountId mockAccountId = new InvestmentAccountId();
        mockAccountId.setCountryAccountCode("HK");
        mockAccountId.setGroupMemberAccountCode("HBAP");
        mockAccountId.setAccountNumber("123456");
        mockAccountId.setAccountProductTypeCode("SAV");
        mockAccountId.setAccountTypeCode("01");
        mockAccountId.setAccountCurrencyCode("HKD");
        // mockAccount.setInvestmentAccountId(mockAccountId); // 如果 InvestmentAccount 有这个字段，需要设置
        investmentAccountList.add(mockAccount);
        mockCustomerAccounts.setInvestmentAccountList(investmentAccountList);

        // Mock the call to retrieveCustomerAccounts (which internally calls restClientService.get with accountsMapUrl)
        String expectedAccountsMapUrl = "http://test-accounts-map/accounts-map?consumerId=DAC"; // Ensure this matches the URL built in retrieveCustomerAccounts
        when(mockRestClientService.get(eq(expectedAccountsMapUrl), anyMap(), eq(CustomerAccounts.class), anyInt(), anyBoolean()))
                .thenReturn(mockCustomerAccounts); // Return the mock object

        // Mock the call to retrieveCustomerNamesWithCinNumber (if needed for CEP headers)
        PartyNameResponse mockPartyNameResponse = new PartyNameResponse();
        // Set mock name data if necessary
        // PartyName mockName = new PartyName(); mockName.setLastName("Test"); ... mockPartyNameResponse.setName(mockName);
        when(mockRestClientService.get(anyString(), anyMap(), eq(PartyNameResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockPartyNameResponse); // Mock CEP name call (URL and headers are complex, using anyString/anyMap)

        // Mock the call to retrieveCustomerPhoneNumberWithCinNumber (if needed for CEP headers)
        PartyContactResponse mockPartyContactResponse = new PartyContactResponse();
        // Set mock contact data if necessary
        // PartyContact mockContact = new PartyContact(); mockContact.setMobileNumber1("12345678"); ... mockPartyContactResponse.setContact(mockContact);
        when(mockRestClientService.get(anyString(), anyMap(), eq(PartyContactResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockPartyContactResponse); // Mock CEP contact call

        // Mock the main restClientService.get call for the transfer list endpoint
        RetrieveTransferListResponse response = new RetrieveTransferListResponse();
        RetrieveTransferListResponseData responseData = new RetrieveTransferListResponseData();
        responseData.setTransferLists(new ArrayList<TransferListItemInfo>());
        response.setData(responseData);
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        response.setResponseDetails(responseDetails);

        // Use a more flexible matcher for the URL, as it will contain query parameters built by UriComponentsBuilder
        when(mockRestClientService.get(anyString(), anyMap(), eq(RetrieveTransferListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(response);

        // --- Act & Assert ---
        assertDoesNotThrow(() -> { // Use assertDoesNotThrow to verify no exception is thrown
            tradeTransferServiceUnderTest.retrieveTransferList(sourceRequestHeader, "ACCEPTED", Collections.singletonList("123"),
                    "1", "PROD1", "PLAIN_TEXT");
        });

        // Verify the calls were made as expected
        // Verify retrieveCustomerAccounts was called
        verify(mockRestClientService, times(1)).get(eq(expectedAccountsMapUrl), anyMap(), eq(CustomerAccounts.class), anyInt(), anyBoolean());
        // Verify the main API call was made
        verify(mockRestClientService, times(1)).get(anyString(), anyMap(), eq(RetrieveTransferListResponse.class), anyInt(), anyBoolean());
        // Verify CEP calls were made (times depend on your implementation's exact flow, here assuming once each)
        verify(mockRestClientService, times(1)).get(anyString(), argThat(headers -> headers.containsKey(HTTPRequestHeaderConstants.X_HSBC_E2E_TRUST_TOKEN)), eq(PartyNameResponse.class), anyInt(), anyBoolean());
        verify(mockRestClientService, times(1)).get(anyString(), argThat(headers -> headers.containsKey(HTTPRequestHeaderConstants.X_HSBC_E2E_TRUST_TOKEN)), eq(PartyContactResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void retrieveTransferList_Success() {
        // --- Arrange ---
        // Mock retrieveCustomerAccounts to return a CustomerAccounts object with a non-empty list
        CustomerAccounts mockCustomerAccounts = new CustomerAccounts();
        List<InvestmentAccount> investmentAccountList = new ArrayList<>();
        InvestmentAccount mockAccount = new InvestmentAccount();
        mockAccount.setChecksum("CHECKSUM456");
        InvestmentAccountId mockAccountId = new InvestmentAccountId();
        mockAccountId.setCountryAccountCode("US");
        mockAccountId.setGroupMemberAccountCode("HSBC");
        mockAccountId.setAccountNumber("789012");
        mockAccountId.setAccountProductTypeCode("CUR");
        mockAccountId.setAccountTypeCode("02");
        mockAccountId.setAccountCurrencyCode("USD");
        // mockAccount.setInvestmentAccountId(mockAccountId); // If applicable
        investmentAccountList.add(mockAccount);
        mockCustomerAccounts.setInvestmentAccountList(investmentAccountList);

        // Mock the call to retrieveCustomerAccounts
        String expectedAccountsMapUrl = "http://test-accounts-map/accounts-map?consumerId=DAC";
        when(mockRestClientService.get(eq(expectedAccountsMapUrl), anyMap(), eq(CustomerAccounts.class), anyInt(), anyBoolean()))
                .thenReturn(mockCustomerAccounts);

        // Mock CEP calls
        PartyNameResponse mockPartyNameResponse = new PartyNameResponse();
        // Set mock name data if necessary
        when(mockRestClientService.get(anyString(), anyMap(), eq(PartyNameResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockPartyNameResponse);

        PartyContactResponse mockPartyContactResponse = new PartyContactResponse();
        // Set mock contact data if necessary
        when(mockRestClientService.get(anyString(), anyMap(), eq(PartyContactResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockPartyContactResponse);

        // Mock the main restClientService.get call for the transfer list endpoint
        RetrieveTransferListResponse mockResponse = new RetrieveTransferListResponse();
        RetrieveTransferListResponseData responseData = new RetrieveTransferListResponseData();
        responseData.setTransferLists(new ArrayList<>()); // Empty list for success case
        mockResponse.setData(responseData);
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        mockResponse.setResponseDetails(responseDetails);

        // Use a flexible matcher for the URL and verify the response
        when(mockRestClientService.get(anyString(), anyMap(), eq(RetrieveTransferListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockResponse);

        // --- Act ---
        RetrieveTransferListResponse result = tradeTransferServiceUnderTest.retrieveTransferList(
                sourceRequestHeader, "ACCEPTED", Arrays.asList("123"), "1", "PROD1", "PLAIN_TEXT");

        // --- Assert ---
        assertNotNull(result);
        assertEquals(0, result.getResponseDetails().getResponseCodeNumber());
        // Verify the calls were made as expected
        verify(mockRestClientService, times(1)).get(eq(expectedAccountsMapUrl), anyMap(), eq(CustomerAccounts.class), anyInt(), anyBoolean());
        verify(mockRestClientService, times(1)).get(anyString(), anyMap(), eq(RetrieveTransferListResponse.class), anyInt(), anyBoolean());
        verify(mockRestClientService, times(1)).get(anyString(), argThat(headers -> headers.containsKey(HTTPRequestHeaderConstants.X_HSBC_E2E_TRUST_TOKEN)), eq(PartyNameResponse.class), anyInt(), anyBoolean());
        verify(mockRestClientService, times(1)).get(anyString(), argThat(headers -> headers.containsKey(HTTPRequestHeaderConstants.X_HSBC_E2E_TRUST_TOKEN)), eq(PartyContactResponse.class), anyInt(), anyBoolean());
    }

// ... (其他测试方法保持不变)
