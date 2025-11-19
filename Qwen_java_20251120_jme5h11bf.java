package com.hsbc.trade.transfer.service.impl;

import com.hsbc.trade.ErrorCodes;
import com.hsbc.trade.HTTPRequestHeaderConstants;
import com.hsbc.trade.common.AccountId;
import com.hsbc.trade.service.DuplicateSubmitPreventionService;
import com.hsbc.trade.service.RestClientService;
import com.hsbc.trade.transfer.common.TransferSideCode;
import com.hsbc.trade.transfer.constant.TransferQueryParameterConstant;
import com.hsbc.trade.transfer.createtransfer.ActionRequestCode;
import com.hsbc.trade.transfer.createtransfer.CreateTransferRequest;
import com.hsbc.trade.transfer.createtransfer.CreateTransferResponse;
import com.hsbc.trade.transfer.createtransfer.ReceiverInfo;
import com.hsbc.trade.transfer.createtransfer.TransferOrderInfo;
import com.hsbc.trade.transfer.domain.account.CustomerAccounts;
import com.hsbc.trade.transfer.domain.account.InvestmentAccount;
import com.hsbc.trade.transfer.domain.cep.PartyContactResponse;
import com.hsbc.trade.transfer.domain.cep.PartyNameResponse;
import com.hsbc.trade.transfer.domain.eligibility.RuleResponse;
import com.hsbc.trade.transfer.domain.mds.GoldPriceResponse;
import com.hsbc.trade.transfer.domain.mds.GoldPriceResponseData;
import com.hsbc.trade.transfer.exception.TransferLimitExceededException;
import com.hsbc.trade.transfer.retrievetransferdetail.RetrieveTransferDetailResponse;
import com.hsbc.trade.transfer.retrievetransferdetail.RetrieveTransferDetailResponseData;
import com.hsbc.trade.transfer.retrievetransferlist.RetrieveTransferListResponse;
import com.hsbc.trade.transfer.retrievetransferlist.RetrieveTransferListResponseData;
import com.hsbc.trade.transfer.retrievetransferlist.TransferListItemInfo;
import com.hsbc.trade.transfer.service.AbstractRestService;
import com.hsbc.trade.transfer.service.TradeTransferService;
import com.hsbc.trade.transfer.updatetransfer.TransferActionCode;
import com.hsbc.trade.transfer.updatetransfer.UpdateTransferRequest;
import com.hsbc.trade.transfer.updatetransfer.UpdateTransferResponse;
import com.hsbc.trade.transfer.utils.ResponseInfoHandler;
import com.hsbc.trade.utils.E2ETrustTokenUtil;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TradeTransferServiceImplTest {

    @Mock
    private RestClientService restClientService;

    @Mock
    private E2ETrustTokenUtil e2ETrustTokenUtil;

    @Mock
    private DuplicateSubmitPreventionService duplicateSubmitPreventionService;

    @Mock
    private TradeLimitServiceImpl tradeLimitService;

    @Mock
    private SreValidationServiceImpl sreValidationService;

    @InjectMocks
    private TradeTransferServiceImpl tradeTransferService;

    private final Map<String, String> requestHeader = new HashMap<>();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // 初始化请求头
        requestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CIN12345");

        // Mock 基础配置
        when(tradeLimitService.retrieveLimitations(any())).thenCallRealMethod();
        when(tradeLimitService.retrieveLimitations(anyMap()))
                .thenReturn(createDefaultLimitResponse());

        // Mock E2E Token
        when(e2ETrustTokenUtil.getE2ETrustToken()).thenReturn("mock-e2e-token");

        // Mock SRE 验证成功
        when(sreValidationService.callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new RuleResponse());
        when(sreValidationService.handleSreValidateResponse(any(RuleResponse.class)))
                .thenReturn(true);

        // Mock 金价响应
        when(restClientService.get(anyString(), anyMap(), eq(GoldPriceResponse.class), anyInt(), anyBoolean()))
                .thenReturn(createGoldPriceResponse());

        // Mock 客户姓名响应
        when(restClientService.get(anyString(), anyMap(), eq(PartyNameResponse.class), anyInt(), anyBoolean()))
                .thenReturn(createPartyNameResponse());

        // Mock 客户联系方式响应
        when(restClientService.get(anyString(), anyMap(), eq(PartyContactResponse.class), anyInt(), anyBoolean()))
                .thenReturn(createPartyContactResponse());

        // Mock 账户列表响应
        when(restClientService.get(anyString(), anyMap(), eq(CustomerAccounts.class), anyInt(), anyBoolean()))
                .thenReturn(createCustomerAccounts());

        // Mock 转账列表响应
        when(restClientService.get(anyString(), anyMap(), eq(RetrieveTransferListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(createRetrieveTransferListResponse());

        // Mock 转账详情响应
        when(restClientService.get(anyString(), anyMap(), eq(RetrieveTransferDetailResponse.class), anyInt(), anyBoolean()))
                .thenReturn(createRetrieveTransferDetailResponse());

        // Mock 创建转账响应
        when(restClientService.post(anyString(), anyMap(), any(CreateTransferRequest.class), eq(CreateTransferResponse.class), anyInt(), anyBoolean()))
                .thenReturn(createCreateTransferResponse());

        // Mock 修改转账响应
        when(restClientService.put(anyString(), anyMap(), any(UpdateTransferRequest.class), eq(UpdateTransferResponse.class), anyInt(), anyBoolean()))
                .thenReturn(createUpdateTransferResponse());

        // Mock 唯一键生成
        when(duplicateSubmitPreventionService.generateUniqueKey()).thenReturn("unique-key-123");

        // Mock 账户ID查找
        when(restClientService.get(anyString(), anyMap(), eq(AccountId.class), anyInt(), anyBoolean()))
                .thenReturn(createAccountId());
    }

    // ==================== 测试 retrieveTransferList ====================

    @Test
    void testRetrieveTransferList_Success() {
        // Act
        RetrieveTransferListResponse response = tradeTransferService.retrieveTransferList(
                requestHeader, "PENDING", Arrays.asList("chk1", "chk2"), "{}", "PROD1", "S1");

        // Assert
        assertNotNull(response);
        assertNotNull(response.getData());
        assertEquals(1, response.getData().getTransferLists().size());
        assertEquals("MASKED*", response.getData().getTransferLists().get(0).getSenderCustomerFirstName());
        assertEquals("MASKED*", response.getData().getTransferLists().get(0).getSenderCustomerMiddleName());

        verify(restClientService, times(1)).get(anyString(), anyMap(), eq(CustomerAccounts.class), anyInt(), anyBoolean());
        verify(restClientService, times(1)).get(anyString(), anyMap(), eq(PartyNameResponse.class), anyInt(), anyBoolean());
        verify(restClientService, times(1)).get(anyString(), anyMap(), eq(PartyContactResponse.class), anyInt(), anyBoolean());
        verify(restClientService, times(1)).get(anyString(), anyMap(), eq(RetrieveTransferListResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void testRetrieveTransferList_AccountListEmpty() {
        // Arrange: 返回空账户列表
        when(restClientService.get(anyString(), anyMap(), eq(CustomerAccounts.class), anyInt(), anyBoolean()))
                .thenReturn(new CustomerAccounts());

        // Act & Assert
        RetrieveTransferListResponse response = tradeTransferService.retrieveTransferList(
                requestHeader, "PENDING", null, "{}", null, null);

        assertNotNull(response);
        assertNull(response.getData().getTransferLists()); // 无异常，但无账户信息
        verify(restClientService, times(1)).get(anyString(), anyMap(), eq(CustomerAccounts.class), anyInt(), anyBoolean());
    }

    // ==================== 测试 retrieveTransferDetail ====================

    @Test
    void testRetrieveTransferDetail_Success() {
        // Act
        RetrieveTransferDetailResponse response = tradeTransferService.retrieveTransferDetail(requestHeader, "REF123");

        // Assert
        assertNotNull(response);
        assertNotNull(response.getData());
        assertEquals("chk1", response.getData().getAccountChecksumIdentifier());
        assertEquals("MASKED*", response.getData().getSenderCustomerFirstName());

        verify(restClientService, times(1)).get(anyString(), anyMap(), eq(CustomerAccounts.class), anyInt(), anyBoolean());
        verify(restClientService, times(1)).get(anyString(), anyMap(), eq(PartyNameResponse.class), anyInt(), anyBoolean());
        verify(restClientService, times(1)).get(anyString(), anyMap(), eq(PartyContactResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void testRetrieveTransferDetail_AccountChecksumNotFound() {
        // Arrange: 账户列表中无匹配 accountNumber
        InvestmentAccount account = new InvestmentAccount();
        account.setInvestmentAccountId(new AccountId().setAccountNumber("OTHER_ACCOUNT"));
        CustomerAccounts accounts = new CustomerAccounts();
        accounts.setInvestmentAccountList(Collections.singletonList(account));
        when(restClientService.get(anyString(), anyMap(), eq(CustomerAccounts.class), anyInt(), anyBoolean()))
                .thenReturn(accounts);

        // Act & Assert
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> tradeTransferService.retrieveTransferDetail(requestHeader, "REF123")
        );

        assertEquals("ACCOUNT_LIST_EMPTY_ERROR", exception.getMessage());
        verify(restClientService, times(1)).get(anyString(), anyMap(), eq(CustomerAccounts.class), anyInt(), anyBoolean());
    }

    @Test
    void testRetrieveTransferDetail_ResponseDataNull() {
        // Arrange: 响应体为 null
        RetrieveTransferDetailResponse response = new RetrieveTransferDetailResponse();
        when(restClientService.get(anyString(), anyMap(), eq(RetrieveTransferDetailResponse.class), anyInt(), anyBoolean()))
                .thenReturn(response);

        // Act
        RetrieveTransferDetailResponse result = tradeTransferService.retrieveTransferDetail(requestHeader, "REF123");

        // Assert
        assertNotNull(result);
        assertNull(result.getData());
        verify(restClientService, times(1)).get(anyString(), anyMap(), eq(RetrieveTransferDetailResponse.class), anyInt(), anyBoolean());
    }

    // ==================== 测试 createTransfers ====================

    @Test
    void testCreateTransfers_Success() {
        // Arrange
        CreateTransferRequest request = new CreateTransferRequest();
        ReceiverInfo receiver = new ReceiverInfo();
        receiver.setReceiverCustomerNumber("CIN999");
        receiver.setTransferQuantity(new BigDecimal("100.00"));
        request.getData().setReceiverLists(Collections.singletonList(receiver));
        request.getData().setRequestPriceValue(new BigDecimal("50.00"));
        request.getData().setActionRequestCode(ActionRequestCode.D);
        request.getData().setSenderInvestmentAccountChecksumIdentifier("chk1");

        // Act
        CreateTransferResponse response = tradeTransferService.createTransfers(requestHeader, request);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getData());
        assertEquals("unique-key-123", response.getData().getRequestUniqueKey());
        assertEquals(new BigDecimal("1234.56"), response.getData().getTransferOrderLists().get(0).getRequestPriceValue());
        assertEquals("HKD", response.getData().getTransferOrderLists().get(0).getPriceCurrencyCode());
        assertEquals("Smith John A", response.getData().getSenderCustomerName());

        verify(tradeLimitService, times(1)).retrieveLimitations(anyMap());
        verify(sreValidationService, times(1)).callSreForTransferValidation(eq("dac_tokenized_gold_transfer_sender_rule"), anyString(), eq("CIN999"), anyMap());
        verify(restClientService, times(1)).get(anyString(), anyMap(), eq(GoldPriceResponse.class), anyInt(), anyBoolean());
        verify(restClientService, times(1)).post(anyString(), anyMap(), any(CreateTransferRequest.class), eq(CreateTransferResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void testCreateTransfers_DailyLimitExceeded() {
        // Arrange: 限额超限
        RetrieveTransferLimitResponse limitResponse = createLimitResponseWithAvailableAmount(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        when(tradeLimitService.retrieveLimitations(anyMap())).thenReturn(limitResponse);

        CreateTransferRequest request = new CreateTransferRequest();
        ReceiverInfo receiver = new ReceiverInfo();
        receiver.setTransferQuantity(new BigDecimal("100000.00")); // 超过限额
        request.getData().setReceiverLists(Collections.singletonList(receiver));
        request.getData().setRequestPriceValue(new BigDecimal("1000.00"));
        request.getData().setSenderInvestmentAccountChecksumIdentifier("chk1");

        // Act & Assert
        TransferLimitExceededException exception = assertThrows(
                TransferLimitExceededException.class,
                () -> tradeTransferService.createTransfers(requestHeader, request)
        );

        assertEquals("Daily limit exceeded: 10000.00", exception.getMessage());
    }

    @Test
    void testCreateTransfers_MonthlyLimitExceeded() {
        // Arrange: 月限额超限
        RetrieveTransferLimitResponse limitResponse = createLimitResponseWithAvailableAmount(
                new BigDecimal("10000.00"), BigDecimal.ZERO, new BigDecimal("200000.00"));
        when(tradeLimitService.retrieveLimitations(anyMap())).thenReturn(limitResponse);

        CreateTransferRequest request = new CreateTransferRequest();
        ReceiverInfo receiver = new ReceiverInfo();
        receiver.setTransferQuantity(new BigDecimal("500000.00")); // 超过月限额
        request.getData().setReceiverLists(Collections.singletonList(receiver));
        request.getData().setRequestPriceValue(new BigDecimal("1000.00"));
        request.getData().setSenderInvestmentAccountChecksumIdentifier("chk1");

        // Act & Assert
        TransferLimitExceededException exception = assertThrows(
                TransferLimitExceededException.class,
                () -> tradeTransferService.createTransfers(requestHeader, request)
        );

        assertEquals("Monthly limit exceeded: 50000.00", exception.getMessage());
    }

    @Test
    void testCreateTransfers_YearlyLimitExceeded() {
        // Arrange: 年限额超限
        RetrieveTransferLimitResponse limitResponse = createLimitResponseWithAvailableAmount(
                new BigDecimal("10000.00"), new BigDecimal("50000.00"), BigDecimal.ZERO);
        when(tradeLimitService.retrieveLimitations(anyMap())).thenReturn(limitResponse);

        CreateTransferRequest request = new CreateTransferRequest();
        ReceiverInfo receiver = new ReceiverInfo();
        receiver.setTransferQuantity(new BigDecimal("2000000.00")); // 超过年限额
        request.getData().setReceiverLists(Collections.singletonList(receiver));
        request.getData().setRequestPriceValue(new BigDecimal("1000.00"));
        request.getData().setSenderInvestmentAccountChecksumIdentifier("chk1");

        // Act & Assert
        TransferLimitExceededException exception = assertThrows(
                TransferLimitExceededException.class,
                () -> tradeTransferService.createTransfers(requestHeader, request)
        );

        assertEquals("Yearly limit exceeded: 200000.00", exception.getMessage());
    }

    @Test
    void testCreateTransfers_GoldPriceMissing_DOperation() {
        // Arrange: D 操作但金价为空
        when(restClientService.get(anyString(), anyMap(), eq(GoldPriceResponse.class), anyInt(), anyBoolean()))
                .thenReturn(new GoldPriceResponse());

        CreateTransferRequest request = new CreateTransferRequest();
        ReceiverInfo receiver = new ReceiverInfo();
        receiver.setTransferQuantity(new BigDecimal("100.00"));
        request.getData().setReceiverLists(Collections.singletonList(receiver));
        request.getData().setRequestPriceValue(new BigDecimal("50.00"));
        request.getData().setActionRequestCode(ActionRequestCode.D);
        request.getData().setSenderInvestmentAccountChecksumIdentifier("chk1");

        // Act
        CreateTransferResponse response = tradeTransferService.createTransfers(requestHeader, request);

        // Assert: 未设置金价，但不报错
        assertNotNull(response);
        assertNull(response.getData().getTransferOrderLists().get(0).getRequestPriceValue());
        assertEquals("unique-key-123", response.getData().getRequestUniqueKey());
    }

    @Test
    void testCreateTransfers_SreValidationFailed() {
        // Arrange: SRE 验证抛异常
        when(sreValidationService.callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap()))
                .thenThrow(new RuntimeException("SRE service down"));

        CreateTransferRequest request = new CreateTransferRequest();
        ReceiverInfo receiver = new ReceiverInfo();
        receiver.setReceiverCustomerNumber("CIN999");
        receiver.setTransferQuantity(new BigDecimal("100.00"));
        request.getData().setReceiverLists(Collections.singletonList(receiver));
        request.getData().setRequestPriceValue(new BigDecimal("50.00"));
        request.getData().setSenderInvestmentAccountChecksumIdentifier("chk1");

        // Act & Assert
        InternalServerErrorException exception = assertThrows(
                InternalServerErrorException.class,
                () -> tradeTransferService.createTransfers(requestHeader, request)
        );

        assertEquals("UNEXPECTED_RESULT_SRBP_ONLINE_ERROR", exception.getMessage());
    }

    @Test
    void testCreateTransfers_NullReceiverList() {
        // Arrange
        CreateTransferRequest request = new CreateTransferRequest();
        request.getData().setReceiverLists(null);
        request.getData().setSenderInvestmentAccountChecksumIdentifier("chk1");

        // Act
        CreateTransferResponse response = tradeTransferService.createTransfers(requestHeader, request);

        // Assert: 无异常，继续执行
        assertNotNull(response);
        verify(sreValidationService, times(0)).callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap());
    }

    // ==================== 测试 modifyTransfers ====================

    @Test
    void testModifyTransfers_Accept_Success() {
        // Arrange
        UpdateTransferRequest request = new UpdateTransferRequest();
        request.getData().setTransferActionCode(TransferActionCode.A);
        request.getData().setReceiverInvestmentAccountChecksumIdentifier("chk1");
        request.getData().setReceiverCustomerInternalNumber("CIN999");

        // Act
        UpdateTransferResponse response = tradeTransferService.modifyTransfers(requestHeader, request);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getData());
        assertEquals("CIN12345", request.getData().getReceiverCustomerInternalNumber()); // 被覆盖
        assertEquals(new BigDecimal("1234.56"), request.getData().getReceivePriceValue());
        assertEquals("HKD", request.getData().getReceivePriceCurrencyCode());

        verify(sreValidationService, times(1)).callSreForTransferValidation(
                eq("dac_tokenized_gold_transfer_receiver_rule"), anyString(), eq("CIN999"), anyMap());
        verify(restClientService, times(1)).get(anyString(), anyMap(), eq(GoldPriceResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void testModifyTransfers_Reject_Success() {
        // Arrange
        UpdateTransferRequest request = new UpdateTransferRequest();
        request.getData().setTransferActionCode(TransferActionCode.R);
        request.getData().setReceiverInvestmentAccountChecksumIdentifier("chk1");

        // Act
        UpdateTransferResponse response = tradeTransferService.modifyTransfers(requestHeader, request);

        // Assert
        assertNotNull(response);
        assertEquals("CIN12345", request.getData().getReceiverCustomerInternalNumber()); // 被覆盖
        verify(sreValidationService, times(0)).callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void testModifyTransfers_OtherAction_NoValidation() {
        // Arrange
        UpdateTransferRequest request = new UpdateTransferRequest();
        request.getData().setTransferActionCode(TransferActionCode.C); // 其他操作
        request.getData().setReceiverInvestmentAccountChecksumIdentifier("chk1");

        // Act
        UpdateTransferResponse response = tradeTransferService.modifyTransfers(requestHeader, request);

        // Assert
        assertNotNull(response);
        verify(sreValidationService, times(0)).callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap());
    }

    // ==================== 测试私有方法 ====================

    @Test
    void testExtractAccountIdMap_Empty() {
        CustomerAccounts accounts = new CustomerAccounts();
        accounts.setInvestmentAccountList(null);
        Map<String, String> result = tradeTransferService.extractAccountIdMap(accounts);
        assertTrue(result.isEmpty());
    }

    @Test
    void testExtractAccountIdMap_Valid() {
        InvestmentAccount acc = new InvestmentAccount();
        AccountId id = new AccountId();
        id.setCountryAccountCode("HK");
        id.setGroupMemberAccountCode("GRP");
        id.setAccountNumber("ACC123");
        id.setAccountProductTypeCode("INV");
        id.setAccountTypeCode("SAV");
        id.setAccountCurrencyCode("HKD");
        acc.setInvestmentAccountId(id);
        acc.setChecksum("chk1");

        CustomerAccounts accounts = new CustomerAccounts();
        accounts.setInvestmentAccountList(Collections.singletonList(acc));

        Map<String, String> result = tradeTransferService.extractAccountIdMap(accounts);
        assertEquals(1, result.size());
        assertEquals("countryAccountCode=HK;groupMemberAccountCode=GRP;accountNumber=ACC123;accountProductTypeCode=INV;accountTypeCode=SAV;accountCurrencyCode=HKD",
                result.get("chk1"));
    }

    @Test
    void testMaskNamesInResponse_TransferList_Success() {
        RetrieveTransferListResponseData data = new RetrieveTransferListResponseData();
        TransferListItemInfo item = new TransferListItemInfo();
        item.setSenderCustomerFirstName("John");
        item.setSenderCustomerMiddleName("Michael");
        item.setTransferSideCode(TransferSideCode.RECEIVER);
        data.setTransferLists(Collections.singletonList(item));

        tradeTransferService.maskNamesInResponse(data);

        assertEquals("J****", item.getSenderCustomerFirstName());
        assertEquals("M******", item.getSenderCustomerMiddleName());
    }

    @Test
    void testMaskNamesInResponse_TransferDetail_Success() {
        RetrieveTransferDetailResponseData data = new RetrieveTransferDetailResponseData();
        data.setSenderCustomerFirstName("Alice");
        data.setSenderCustomerMiddleName("Brown");
        data.setTransferSideCode(TransferSideCode.RECEIVER);
        data.setIsReceiverBankCustomer("Y");

        tradeTransferService.maskNamesInResponse(data);

        assertEquals("A****", data.getReceiverCustomerFirstName());
        assertEquals("B****", data.getReceiverCustomerMiddleName());
    }

    @Test
    void testMaskNamesInResponse_ReflectionException() {
        // 模拟反射找不到方法
        Object mockObj = new Object() {
            public String getNonExistentField() { return "test"; }
        };

        // 无异常，日志记录即可
        tradeTransferService.maskFirstNameAndMiddleName(mockObj, "nonExistentField", "alsoNonExistent");
        // 验证无异常抛出
    }

    @Test
    void testFindAccountChecksumForAccountNumber_Found() {
        InvestmentAccount acc = new InvestmentAccount();
        AccountId id = new AccountId().setAccountNumber("ACC123");
        acc.setInvestmentAccountId(id);
        acc.setChecksum("chk1");

        CustomerAccounts accounts = new CustomerAccounts();
        accounts.setInvestmentAccountList(Collections.singletonList(acc));

        String result = tradeTransferService.findAccountChecksumForAccountNumber(accounts, "ACC123");
        assertEquals("chk1", result);
    }

    @Test
    void testFindAccountChecksumForAccountNumber_NotFound() {
        InvestmentAccount acc = new InvestmentAccount();
        acc.setInvestmentAccountId(new AccountId().setAccountNumber("OTHER"));
        CustomerAccounts accounts = new CustomerAccounts();
        accounts.setInvestmentAccountList(Collections.singletonList(acc));

        String result = tradeTransferService.findAccountChecksumForAccountNumber(accounts, "ACC123");
        assertNull(result);
    }

    @Test
    void testValidateTransferLimits_DailyExceeded() {
        RetrieveTransferLimitResponse limitResponse = createLimitResponseWithAvailableAmount(
                new BigDecimal("5000.00"), new BigDecimal("50000.00"), new BigDecimal("200000.00"));

        BigDecimal totalAmount = new BigDecimal("6000.00"); // 超过可用日限额

        TransferLimitExceededException exception = assertThrows(
                TransferLimitExceededException.class,
                () -> tradeTransferService.validateTransferLimits(totalAmount, limitResponse)
        );

        assertEquals("Daily limit exceeded: 10000.00", exception.getMessage());
    }

    @Test
    void testValidateTransferLimits_MonthlyExceeded() {
        RetrieveTransferLimitResponse limitResponse = createLimitResponseWithAvailableAmount(
                new BigDecimal("10000.00"), new BigDecimal("40000.00"), new BigDecimal("200000.00"));

        BigDecimal totalAmount = new BigDecimal("50000.00"); // 超过可用月限额

        TransferLimitExceededException exception = assertThrows(
                TransferLimitExceededException.class,
                () -> tradeTransferService.validateTransferLimits(totalAmount, limitResponse)
        );

        assertEquals("Monthly limit exceeded: 50000.00", exception.getMessage());
    }

    @Test
    void testValidateTransferLimits_YearlyExceeded() {
        RetrieveTransferLimitResponse limitResponse = createLimitResponseWithAvailableAmount(
                new BigDecimal("10000.00"), new BigDecimal("50000.00"), new BigDecimal("150000.00"));

        BigDecimal totalAmount = new BigDecimal("250000.00"); // 超过可用年限额

        TransferLimitExceededException exception = assertThrows(
                TransferLimitExceededException.class,
                () -> tradeTransferService.validateTransferLimits(totalAmount, limitResponse)
        );

        assertEquals("Yearly limit exceeded: 200000.00", exception.getMessage());
    }

    // ==================== 辅助方法 ====================

    private RetrieveTransferLimitResponse createDefaultLimitResponse() {
        RetrieveTransferLimitResponse response = new RetrieveTransferLimitResponse();
        RetrieveTransferLimitResponse.Data data = new RetrieveTransferLimitResponse.Data();
        data.setMaxDailyLimitedAmount(new BigDecimal("10000.00"));
        data.setMaxMonthlyLimitedAmount(new BigDecimal("50000.00"));
        data.setMaxYearlyLimitedAmount(new BigDecimal("200000.00"));
        data.setAvailableTodayAmount(new BigDecimal("10000.00"));
        data.setAvailableMonthToDateAmount(new BigDecimal("50000.00"));
        data.setAvailableYearToDateAmount(new BigDecimal("200000.00"));
        data.setMaxMonthlyTransferCount("5");
        data.setAvailableMonthlyTransferCount("4");
        response.setData(data);
        return response;
    }

    private RetrieveTransferLimitResponse createLimitResponseWithAvailableAmount(BigDecimal daily, BigDecimal monthly, BigDecimal yearly) {
        RetrieveTransferLimitResponse response = new RetrieveTransferLimitResponse();
        RetrieveTransferLimitResponse.Data data = new RetrieveTransferLimitResponse.Data();
        data.setMaxDailyLimitedAmount(new BigDecimal("10000.00"));
        data.setMaxMonthlyLimitedAmount(new BigDecimal("50000.00"));
        data.setMaxYearlyLimitedAmount(new BigDecimal("200000.00"));
        data.setAvailableTodayAmount(daily);
        data.setAvailableMonthToDateAmount(monthly);
        data.setAvailableYearToDateAmount(yearly);
        response.setData(data);
        return response;
    }

    private GoldPriceResponse createGoldPriceResponse() {
        GoldPriceResponse response = new GoldPriceResponse();
        GoldPriceResponseData data = new GoldPriceResponseData();
        data.setGoldPriceAmount(new BigDecimal("1234.56"));
        data.setPublishTime("2025-11-20T10:00:00Z");
        response.setData(data);
        return response;
    }

    private PartyNameResponse createPartyNameResponse() {
        PartyNameResponse response = new PartyNameResponse();
        PartyNameResponse.Name name = new PartyNameResponse.Name();
        name.setLastName("Smith");
        name.setGivenName("John");
        name.setCustomerChristianName("A");
        response.setName(name);
        return response;
    }

    private PartyContactResponse createPartyContactResponse() {
        PartyContactResponse response = new PartyContactResponse();
        PartyContactResponse.Contact contact = new PartyContactResponse.Contact();
        contact.setMobileNumber1("+85291234567");
        response.setContact(contact);
        return response;
    }

    private CustomerAccounts createCustomerAccounts() {
        CustomerAccounts accounts = new CustomerAccounts();
        InvestmentAccount acc = new InvestmentAccount();
        AccountId id = new AccountId();
        id.setAccountNumber("ACC123");
        acc.setInvestmentAccountId(id);
        acc.setChecksum("chk1");
        accounts.setInvestmentAccountList(Collections.singletonList(acc));
        return accounts;
    }

    private RetrieveTransferListResponse createRetrieveTransferListResponse() {
        RetrieveTransferListResponse response = new RetrieveTransferListResponse();
        RetrieveTransferListResponseData data = new RetrieveTransferListResponseData();
        TransferListItemInfo item = new TransferListItemInfo();
        item.setSenderCustomerFirstName("John");
        item.setSenderCustomerMiddleName("Michael");
        item.setTransferSideCode(TransferSideCode.RECEIVER);
        data.setTransferLists(Collections.singletonList(item));
        response.setData(data);
        return response;
    }

    private RetrieveTransferDetailResponse createRetrieveTransferDetailResponse() {
        RetrieveTransferDetailResponse response = new RetrieveTransferDetailResponse();
        RetrieveTransferDetailResponseData data = new RetrieveTransferDetailResponseData();
        data.setInvestmentAccount(new InvestmentAccount().setAccountNumber("ACC123"));
        data.setTransferSideCode(TransferSideCode.RECEIVER);
        data.setIsReceiverBankCustomer("Y");
        response.setData(data);
        return response;
    }

    private CreateTransferResponse createCreateTransferResponse() {
        CreateTransferResponse response = new CreateTransferResponse();
        CreateTransferResponse.Data data = new CreateTransferResponse.Data();
        List<TransferOrderInfo> orders = new ArrayList<>();
        TransferOrderInfo order = new TransferOrderInfo();
        orders.add(order);
        data.setTransferOrderLists(orders);
        response.setData(data);
        return response;
    }

    private UpdateTransferResponse createUpdateTransferResponse() {
        UpdateTransferResponse response = new UpdateTransferResponse();
        UpdateTransferResponse.Data data = new UpdateTransferResponse.Data();
        response.setData(data);
        return response;
    }

    private AccountId createAccountId() {
        AccountId id = new AccountId();
        id.setAccountNumber("ACC123");
        return id;
    }
}
