@BeforeEach
void setUp() {
    // 初始化 Mockito 注解 (@Mock, @InjectMocks)
    MockitoAnnotations.openMocks(this);

    // 初始化请求头
    requestHeader.clear(); // 确保每次测试前都是干净的
    requestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CIN12345");

    // ========== Mock 关键的 Service 依赖 ==========
    
    // 1. Mock TradeLimitService: 模拟限额检查，返回一个默认的、有充足额度的响应
    when(tradeLimitService.retrieveLimitations(anyMap()))
            .thenReturn(createDefaultRetrieveTransferLimitResponse());

    // 2. Mock E2ETrustTokenUtil: 提供一个固定的模拟 token
    when(e2ETrustTokenUtil.getE2ETrustToken()).thenReturn("mock-e2e-token");

    // 3. Mock SreValidationServiceImpl: 模拟SRE验证成功
    when(sreValidationService.callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap()))
            .thenReturn(new RuleResponse());
    doNothing().when(sreValidationService).handleSreValidateResponse(any(RuleResponse.class));

    // 4. Mock DuplicateSubmitPreventionService: 返回一个固定的唯一键
    when(duplicateSubmitPreventionService.generateUniqueKey()).thenReturn("unique-key-123");

    // ========== Mock RestClientService 的 HTTP 调用 ==========
    // 使用通用匹配器来捕获所有可能的 restClientService.get/put/post 调用
    // 并根据返回类型返回相应的模拟数据
    
    // 注意：这里的顺序很重要！通常先定义更具体的期望，但这里我们用通配符。
    // 在实际中，如果同一个 URL 可能返回不同类型的对象，需要在具体测试中重新定义。

    when(restClientService.get(
            anyString(), // URL (any string)
            anyMap(),    // Headers (any map)
            eq(GoldPriceResponse.class), // 明确指定返回类型
            anyInt(),    // Timeout
            anyBoolean() // Print Log Flag
    )).thenReturn(createGoldPriceResponse());

    when(restClientService.get(
            anyString(),
            anyMap(),
            eq(PartyNameResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(createPartyNameResponse());

    when(restClientService.get(
            anyString(),
            anyMap(),
            eq(PartyContactResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(createPartyContactResponse());

    when(restClientService.get(
            anyString(),
            anyMap(),
            eq(CustomerAccounts.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(createCustomerAccounts());

    when(restClientService.get(
            anyString(),
            anyMap(),
            eq(RetrieveTransferListResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(createRetrieveTransferListResponse());

    when(restClientService.get(
            anyString(),
            anyMap(),
            eq(RetrieveTransferDetailResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(createRetrieveTransferDetailResponse());

    when(restClientService.post(
            anyString(),
            anyMap(),
            any(CreateTransferRequest.class),
            eq(CreateTransferResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(createCreateTransferResponse());

    when(restClientService.put(
            anyString(),
            anyMap(),
            any(UpdateTransferRequest.class),
            eq(UpdateTransferResponse.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(createUpdateTransferResponse());

    when(restClientService.get(
            anyString(),
            anyMap(),
            eq(AccountId.class),
            anyInt(),
            anyBoolean()
    )).thenReturn(createAccountId());

    // ========== 设置 AbstractRestService 的成员变量 ==========
    // 这些值在您的 YAML 中定义，但在单元测试中直接赋值
    tradeTransferService.timeout = 5000;
    tradeTransferService.printMessageLog = false;
    tradeTransferService.tradeOnlineUrl = "https://srbp-aag-uat-wealth-platform-amh-dev.ikp1001snp.cloud.hk.hsbc/api/srbp-online/v3";
    // 如果还有其他类似 sreUrl, cepNameUrl 等，也在这里设置
    // tradeTransferService.sreUrl = "...";
    // tradeTransferService.cepNameUrl = "...";

    log.info("TradeTransferServiceImplTest setup completed.");
}
