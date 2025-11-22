@Test
void retrieveTransferList_Success() {
    // Arrange
    String cin = "CUST123";
    Map<String, String> requestHeader = new HashMap<>();
    requestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, cin);
    requestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, "<saml>token</saml>");
    requestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
    requestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");

    // 1. Mock retrieveCustomerAccounts → 保证 accountIdMap 不为空
    CustomerAccounts mockAccounts = new CustomerAccounts();
    List<InvestmentAccount> accounts = new ArrayList<>();
    
    InvestmentAccount acc1 = new InvestmentAccount();
    acc1.setChecksum("CHK123");
    com.hsbc.trade.transfer.domain.InvestmentAccountId id1 = new com.hsbc.trade.transfer.domain.InvestmentAccountId();
    id1.setCountryAccountCode("HK");
    id1.setGroupMemberAccountCode("HBAP");
    id1.setAccountNumber("123456789");
    id1.setAccountProductTypeCode("SAV");
    id1.setAccountTypeCode("01");
    id1.setAccountCurrencyCode("HKD");
    acc1.setInvestmentAccountId(id1);
    accounts.add(acc1);

    mockAccounts.setInvestmentAccountList(accounts);
    when(restClientService.get(
            eq("https://accounts.map/accounts-map?consumerId=DAC"),
            anyMap(),
            eq(CustomerAccounts.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(mockAccounts);

    // 2. Mock CEP Name
    PartyNameResponse nameResp = new PartyNameResponse();
    PartyName name = new PartyName();
    name.setLastName("Doe");
    name.setGivenName("John");
    name.setCustomerChristianName("Michael");
    nameResp.setName(name);
    when(restClientService.get(
            eq("https://cep.name/CUST123"),
            argThat(h -> h.containsKey(HTTPRequestHeaderConstants.X_HSBC_E2E_TRUST_TOKEN)),
            eq(PartyNameResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(nameResp);

    // 3. Mock CEP Contact
    PartyContactResponse contactResp = new PartyContactResponse();
    PartyContact contact = new PartyContact();
    contact.setMobileNumber1("123456789");
    contactResp.setContact(contact);
    when(restClientService.get(
            eq("https://cep.contact/CUST123"),
            argThat(h -> h.containsKey(HTTPRequestHeaderConstants.X_HSBC_E2E_TRUST_TOKEN)),
            eq(PartyContactResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(contactResp);

    // 4. Mock 主接口返回
    RetrieveTransferListResponse mainResp = new RetrieveTransferListResponse();
    RetrieveTransferListResponseData data = new RetrieveTransferListResponseData();
    data.setTransferLists(new ArrayList<>());
    mainResp.setData(data);
    mainResp.setResponseDetails(new com.hsbc.trade.common.ResponseDetails());

    // 构建最终 URL（含所有 query 参数）
    String expectedUrl = "https://trade.online/transfers" +
            "?transferStatusCode=ACCEPTED" +
            "&pagination=1" +
            "&productId=PROD1" +
            "&sParameterType=PLAIN_TEXT" +
            "&customerInternalNumber=CUST123" +
            "&lastName=Doe" +
            "&firstName=John" +
            "&christianName=Michael" +
            "&telephoneNumber=123456789" +
            "&checksumIdentifiers=CHK123";

    when(restClientService.get(
            eq(expectedUrl),
            anyMap(),
            eq(RetrieveTransferListResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(mainResp);

    // 5. Mock E2E Token
    when(e2ETrustTokenUtil.getE2ETrustToken()).thenReturn("mock-e2e-token");

    // Act
    RetrieveTransferListResponse result = tradeTransferService.retrieveTransferList(
            requestHeader, "ACCEPTED", Arrays.asList("CHK123"), "1", "PROD1", "PLAIN_TEXT"
    );

    // Assert
    assertNotNull(result);
    assertEquals(0, result.getResponseDetails().getResponseCodeNumber());
    verify(restClientService, times(1))
            .get(eq(expectedUrl), anyMap(), eq(RetrieveTransferListResponse.class), anyInt(), anyBoolean());
}
