@BeforeEach
void setUp() {
    MockitoAnnotations.openMocks(this);

    // 初始化请求头
    requestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CIN12345");

    // ==================== 模拟 application-hk-hsbc-local.yml 中的配置 ====================

    // 1. 模拟 TradeTransferServiceImpl 中的 @Value 字段（来自 yml）
    // 注意：这些字段在类中是 private，但你可以在测试类中通过反射设置，或直接修改父类字段（如果继承的字段是 protected）

    // 由于你没有提供 TradeTransferServiceImpl 的完整源码，我们假设：
    // - 它继承了 AbstractRestService（包含 tradeOnlineUrl）
    // - 它有私有字段如 contactEnquiryUrl、srbpOnlineUrl 等，但你没有暴露 setter

    // ✅ 最稳妥方式：通过反射设置 private 字段（推荐）
    setPrivateField(tradeTransferService, "tradeOnlineUrl", "https://srbp-aag-uat-wealth-platform-amh-dev.ikp1001snp.cloud.hk.hsbc/api/srbp-online/v3");
    setPrivateField(tradeTransferService, "contactEnquiryUrl", "https://digitaldev-int-rbwm.hk.hsbc/cb-obs-hk/cb-hk-hbap-obs-shrd-clc-tran-lmt-enq-wpb-sct-internal-proxy/v2/tran-lmt-enq");
    setPrivateField(tradeTransferService, "srbpOnlineUrl", "https://srbp-aag-uat-wealth-platform-amh-dev.ikp1001snp.cloud.hk.hsbc/api/srbp-online/v3");

    // 2. 模拟 CustomerLimitConfig 的值（来自 yml 的 clc 参数）
    when(tradeLimitService.retrieveLimitations(anyMap()))
            .thenReturn(createDefaultRetrieveTransferLimitResponse());

    // 3. 模拟 CEP、MDS、SRE 等 URL（这些是通过 restClientService 调用的，你已经 mock 了）
    // 但你可能在 buildRequestHeaders 或其他方法中用到了这些值，所以也要设置
    setPrivateField(tradeTransferService, "customerAccountUrl", "https://srbp-cag-core.uat.wealth-platform-amh.ape1.dev.aws.cloud.hsbc/api/equities/accounts/%s");
    setPrivateField(tradeTransferService, "accountsMapUrl", "https://srbp-cag-core.uat.wealth-platform-amh.ape1.dev.aws.cloud.hsbc/api/equities/accounts/");
    setPrivateField(tradeTransferService, "sreUrl", "https://srbp-cag-core.uat.wealth-platform-amh.ape1.dev.aws.cloud.hsbc/api/equities/rule-engine/api/v2/rules");
    setPrivateField(tradeTransferService, "cepPartyNameUrl", "https://digitaldev-int-rbwm.hk.hsbc/gdt-mds-cep-260-cus-profile-qry-hk-hbap-cert-internal-proxy/v1/party/CIN-SensitiveHeadersKey/name");
    setPrivateField(tradeTransferService, "cepPartyContactUrl", "https://digitaldev-int-rbwm.hk.hsbc/gdt-mds-cep-260-cus-profile-qry-hk-hbap-cert-internal-proxy/v1/party/CIN-SensitiveHeadersKey/contact");
    setPrivateField(tradeTransferService, "mdsGoldQuotesEndpoint", "https://cag.uat.wealth-platform-amh.ape1.dev.aws.cloud.hsbc/mdl/gold/{productAlternativeIdentifier}/quotes");

    // 4. 模拟 clc.header.*（这些可能用于 buildBaseHeaders）
    setPrivateField(tradeTransferService, "gbgf", "WPB");
    setPrivateField(tradeTransferService, "sourceSystemId", "10898670");
    setPrivateField(tradeTransferService, "clientIp", "127.0.0.1");
    setPrivateField(tradeTransferService, "clientId", "d9ccc780ed7042f997cb53696f7d4d59");
    setPrivateField(tradeTransferService, "clientSecret", ""); // 空字符串
    setPrivateField(tradeTransferService, "targetSystemEnvironmentId", "O63");
    setPrivateField(tradeTransferService, "sessionCorrelationId", "0");

    // 5. 模拟 clc.params.*
    setPrivateField(tradeTransferService, "bankNumber", "004");
    setPrivateField(tradeTransferService, "channelIndicator", "N");
    setPrivateField(tradeTransferService, "enquiryChannel", "N");
    setPrivateField(tradeTransferService, "customerId", "SensitiveHeadersKey");
    setPrivateField(tradeTransferService, "customerIdType", "N");
    setPrivateField(tradeTransferService, "limitType", "P2PS");
    setPrivateField(tradeTransferService, "sequentIndicator", "Y");

    // 6. 模拟 AbstractRestService 的字段（避免 NPE）
    tradeTransferService.timeout = 5000;
    tradeTransferService.printMessageLog = false;

    // 7. 模拟 E2E Token
    when(e2ETrustTokenUtil.getE2ETrustToken()).thenReturn("mock-e2e-token");

    // 8. 模拟 SRE 验证
    when(sreValidationService.callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap()))
            .thenReturn(new RuleResponse());
    when(sreValidationService.handleSreValidateResponse(any(RuleResponse.class)))
            .thenReturn(true);

    // 9. 模拟外部服务响应（你原来的 mock 保持不变）
    when(restClientService.get(anyString(), anyMap(), eq(GoldPriceResponse.class), anyInt(), anyBoolean()))
            .thenReturn(createGoldPriceResponse());
    when(restClientService.get(anyString(), anyMap(), eq(PartyNameResponse.class), anyInt(), anyBoolean()))
            .thenReturn(createPartyNameResponse());
    when(restClientService.get(anyString(), anyMap(), eq(PartyContactResponse.class), anyInt(), anyBoolean()))
            .thenReturn(createPartyContactResponse());
    when(restClientService.get(anyString(), anyMap(), eq(CustomerAccounts.class), anyInt(), anyBoolean()))
            .thenReturn(createCustomerAccounts());
    when(restClientService.get(anyString(), anyMap(), eq(RetrieveTransferListResponse.class), anyInt(), anyBoolean()))
            .thenReturn(createRetrieveTransferListResponse());
    when(restClientService.get(anyString(), anyMap(), eq(RetrieveTransferDetailResponse.class), anyInt(), anyBoolean()))
            .thenReturn(createRetrieveTransferDetailResponse());
    when(restClientService.post(anyString(), anyMap(), any(CreateTransferRequest.class), eq(CreateTransferResponse.class), anyInt(), anyBoolean()))
            .thenReturn(createCreateTransferResponse());
    when(restClientService.put(anyString(), anyMap(), any(UpdateTransferRequest.class), eq(UpdateTransferResponse.class), anyInt(), anyBoolean()))
            .thenReturn(createUpdateTransferResponse());
    when(restClientService.get(anyString(), anyMap(), eq(AccountId.class), anyInt(), anyBoolean()))
            .thenReturn(createAccountId());
    when(duplicateSubmitPreventionService.generateUniqueKey()).thenReturn("unique-key-123");
}

// ✅ 工具方法：通过反射设置 private 字段
private void setPrivateField(Object target, String fieldName, Object value) {
    try {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    } catch (NoSuchFieldException | IllegalAccessException e) {
        throw new RuntimeException("Failed to set field: " + fieldName, e);
    }
}
