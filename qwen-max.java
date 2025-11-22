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

    // === 1. Mock retrieveAccountIdWithCheckSum (via customerAccountUrl) ===
    RetrieveCustomerAccountsIdListResponse accountIdListResponse = new RetrieveCustomerAccountsIdListResponse();
    InvestmentAccountId accountId = new InvestmentAccountId();
    accountId.setCountryAccountCode("HK");
    accountId.setGroupMemberAccountCode("HBAP");
    accountId.setAccountNumber("123456789");
    accountId.setAccountProductTypeCode("GOLD");
    accountId.setAccountTypeCode("01");
    accountId.setAccountCurrencyCode("HKD");
    InvestmentAccountIdList accountIdList = new InvestmentAccountIdList();
    accountIdList.setAccountId(accountId);
    accountIdListResponse.setAccountIdList(Collections.singletonList(accountIdList));

    // 注意：customerAccountUrl 是私有字段，需构造 URL 格式
    String expectedCustomerAccountUrl = String.format(
            MOCK_CUSTOMER_ACCOUNT_URL + "/%s?body=%s",
            "accounts-ids",
            URLEncoder.encode(
                    JacksonUtil.convertObjectToJsonString(Map.of("checksumList", List.of("CHECKSUM123"))),
                    StandardCharsets.UTF_8
            )
    );

    when(restClientService.get(
            eq(expectedCustomerAccountUrl),
            anyMap(), // 包含 CHECKSUM 头
            eq(RetrieveCustomerAccountsIdListResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(accountIdListResponse);

    // === 2. Mock retrieveCustomerNamesWithCinNumber (for sender name) ===
    PartyNameResponse partyNameResponse = new PartyNameResponse();
    PartyName name = new PartyName();
    name.setGivenName("John");
    name.setCustomerChristianName("Michael");
    name.setLastName("Doe");
    partyNameResponse.setName(name);

    // CEP headers: 经过 updateHeaderforCEP + buildSensitiveHeaders
    String customerInternalNumber = "CUST123"; // 来自 sourceRequestHeader
    String sensitiveJson = "[{\"key\":\"SensitiveHeadersKey\",\"value\":\"" + customerInternalNumber + "\"}]";
    String base64Sensitive = Base64.getEncoder().encodeToString(sensitiveJson.getBytes(StandardCharsets.UTF_8));
    Map<String, String> cepHeaders = new HashMap<>();
    cepHeaders.putAll(sourceRequestHeader);
    cepHeaders.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    cepHeaders.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, customerInternalNumber);
    cepHeaders.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID_TYPE, "N");
    cepHeaders.put(HTTPRequestHeaderConstants.X_HSBC_SENSITIVE_DATA, base64Sensitive);
    cepHeaders.remove(HTTPRequestHeaderConstants.X_HSBC_SAML);
    cepHeaders.remove(HTTPRequestHeaderConstants.X_HSBC_SAML3);
    cepHeaders.put(HTTPRequestHeaderConstants.X_HSBC_E2E_TRUST_TOKEN, "e2e-token");
    cepHeaders.put(HTTPRequestHeaderConstants.X_HSBC_GBGF, "");
    cepHeaders.put(HTTPRequestHeaderConstants.X_HSBC_SOURCE_SYSTEM_ID, "");

    when(restClientService.get(
            eq(MOCK_CEP_PARTY_NAME_URL.replace("CIN-SensitiveHeadersKey", customerInternalNumber)),
            argThat(h -> h.equals(cepHeaders)),
            eq(PartyNameResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(partyNameResponse);

    // === 3. Mock SRE validation ===
    RuleResponse sreResponse = new RuleResponse();
    when(sreValidationService.callSreForTransferValidation(
            eq("dac_tokenized_gold_transfer_sender_rule"),
            eq("CUST123"),
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

    // === 5. Mock TradeLimitService ===
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
            eq(request),
            eq(CreateTransferResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(mockPostResponse);

    // === 7. Mock E2E token (used in updateHeaderforCEP) ===
    when(e2ETrustTokenUtil.getE2ETrustToken()).thenReturn("e2e-token");

    // Act
    CreateTransferResponse result = tradeTransferService.createTransfers(sourceRequestHeader, request);

    // Assert
    assertNotNull(result);
    assertEquals(0, result.getResponseDetails().getResponseCodeNumber());
    assertNotNull(result.getData().getSenderCustomerName());
    assertEquals("Doe John Michael", result.getData().getSenderCustomerName());

    // Verify key interactions
    verify(restClientService, times(1)).get(
            eq(expectedCustomerAccountUrl),
            anyMap(),
            eq(RetrieveCustomerAccountsIdListResponse.class),
            anyInt(),
            anyBoolean()
    );
    verify(restClientService, times(1)).get(
            anyString(),
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
    verify(restClientService, times(1)).post(
            eq(MOCK_TRADE_ONLINE_URL + "/transfers"),
            anyMap(),
            eq(request),
            eq(CreateTransferResponse.class),
            anyInt(),
            anyBoolean()
    );
    verify(sreValidationService, times(1)).callSreForTransferValidation(
            eq("dac_tokenized_gold_transfer_sender_rule"),
            eq("CUST123"),
            eq("RECEIVER123"),
            anyMap()
    );
    verify(tradeLimitService, times(1)).retrieveLimitations(anyMap());
}
