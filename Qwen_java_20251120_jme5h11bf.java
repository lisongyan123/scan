package com.hsbc.trade.transfer.service.impl;

import com.hsbc.trade.ErrorCodes;
import com.hsbc.trade.constant.HTTPRequestHeaderConstants;
import com.hsbc.trade.transfer.config.CustomerLimitConfig;
import com.hsbc.trade.transfer.domain.limit.ContactEnquiryParams;
import com.hsbc.trade.transfer.domain.limit.LimitEnquiryResponse;
import com.hsbc.trade.transfer.domain.limit.TransactionLimitDetailList;
import com.hsbc.trade.transfer.exception.TransferLimitExceededException;
import com.hsbc.trade.transfer.retrievetransferamount.RetrieveTransferAmountResponse;
import com.hsbc.trade.transfer.retrievetransferlimit.RetrieveTransferLimitResponse;
import com.hsbc.trade.transfer.retrievetransferlimit.RetrieveTransferLimitResponseData;
import com.hsbc.trade.utils.E2ETrustTokenUtil;
import com.hsbc.trade.service.RestClientService;
import com.hsbc.trade.utils.JacksonUtil;
import jakarta.ws.rs.InternalServerErrorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TradeLimitServiceImplTest {

    @Mock
    private RestClientService restClientService;

    @Mock
    private E2ETrustTokenUtil e2ETrustTokenUtil;

    @Mock
    private CustomerLimitConfig customerLimitConfig;

    @InjectMocks
    private TradeLimitServiceImpl tradeLimitService;

    private final Map<String, String> requestHeader = new HashMap<>();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // 初始化请求头
        requestHeader.put(HTTPRequestHeaderConstants.X_HSBC_Customer_Id, "CUST123");

        // Mock 配置值
        when(customerLimitConfig.getDailyAmount()).thenReturn(new BigDecimal("10000.00"));
        when(customerLimitConfig.getMonthlyAmount()).thenReturn(new BigDecimal("50000.00"));
        when(customerLimitConfig.getMonthlyCount()).thenReturn(5);
        when(customerLimitConfig.getYearlyAmount()).thenReturn(new BigDecimal("200000.00"));

        // Mock E2E Trust Token
        when(e2ETrustTokenUtil.getE2ETrustToken()).thenReturn("mock-e2e-token");

        // Mock URL 配置（实际测试中不调用真实服务）
        when(restClientService.get(anyString(), anyMap(), eq(LimitEnquiryResponse.class), anyInt(), anyBoolean()))
                .thenAnswer(invocation -> {
                    String url = invocation.getArgument(0);
                    Map<String, String> headers = invocation.getArgument(1);
                    // 可选：验证 headers 是否包含正确 token
                    assertEquals("mock-e2e-token", headers.get(HTTPRequestHeaderConstants.X_HSBC_E2E_Trust_Token));
                    return new LimitEnquiryResponse(); // 默认空响应
                });

        when(restClientService.get(anyString(), anyMap(), eq(RetrieveTransferAmountResponse.class), anyInt(), anyBoolean()))
                .thenReturn(createDefaultTransferAmountResponse());
    }

    // ==================== 测试 retrieveLimitations ====================

    @Test
    void testRetrieveLimitations_Success() {
        // Arrange
        LimitEnquiryResponse limitResponse = createLimitEnquiryResponse("P2PS", 2000, 10000); // 已用2000，上限10000
        RetrieveTransferAmountResponse transferResponse = createDefaultTransferAmountResponse();

        when(restClientService.get(anyString(), anyMap(), eq(LimitEnquiryResponse.class), anyInt(), anyBoolean()))
                .thenReturn(limitResponse);
        when(restClientService.get(anyString(), anyMap(), eq(RetrieveTransferAmountResponse.class), anyInt(), anyBoolean()))
                .thenReturn(transferResponse);

        // Act
        RetrieveTransferLimitResponse response = tradeLimitService.retrieveLimitations(requestHeader);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getData());
        RetrieveTransferLimitResponseData data = response.getData();

        assertEquals(new BigDecimal("10000.00"), data.getMaxDailyLimitedAmount());
        assertEquals(new BigDecimal("8000.00"), data.getAvailableTodayAmount()); // 10000 - 2000

        assertEquals(new BigDecimal("50000.00"), data.getMaxMonthlyLimitedAmount());
        assertEquals(new BigDecimal("40000.00"), data.getAvailableMonthToDateAmount()); // 50000 - 10000

        assertEquals("5", data.getMaxMonthlyTransferCount());
        assertEquals("4", data.getAvailableMonthlyTransferCount()); // 5 - 1

        assertEquals(new BigDecimal("200000.00"), data.getMaxYearlyLimitedAmount());
        assertEquals(new BigDecimal("150000.00"), data.getAvailableYearToDateAmount()); // 200000 - 50000

        assertEquals(ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")),
                data.getAsofDateTime());

        verify(restClientService, times(1)).get(anyString(), anyMap(), eq(LimitEnquiryResponse.class), anyInt(), anyBoolean());
        verify(restClientService, times(1)).get(anyString(), anyMap(), eq(RetrieveTransferAmountResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void testRetrieveLimitations_DailyLimitExceeded() {
        // Arrange: 已用金额 > 最大金额
        LimitEnquiryResponse limitResponse = createLimitEnquiryResponse("P2PS", 11000, 10000); // 超限

        when(restClientService.get(anyString(), anyMap(), eq(LimitEnquiryResponse.class), anyInt(), anyBoolean()))
                .thenReturn(limitResponse);

        // Act & Assert
        TransferLimitExceededException exception = assertThrows(
                TransferLimitExceededException.class,
                () -> tradeLimitService.retrieveLimitations(requestHeader)
        );

        assertEquals("Daily limit exceeded: 10000.00", exception.getMessage());
    }

    @Test
    void testRetrieveLimitations_DailyLimitZeroAvailable() {
        // Arrange: 已用金额 = 最大金额
        LimitEnquiryResponse limitResponse = createLimitEnquiryResponse("P2PS", 10000, 10000);

        when(restClientService.get(anyString(), anyMap(), eq(LimitEnquiryResponse.class), anyInt(), anyBoolean()))
                .thenReturn(limitResponse);

        // Act & Assert
        TransferLimitExceededException exception = assertThrows(
                TransferLimitExceededException.class,
                () -> tradeLimitService.retrieveLimitations(requestHeader)
        );

        assertEquals("Daily limit exceeded: 10000.00", exception.getMessage());
    }

    @Test
    void testRetrieveLimitations_MonthlyCountExceeded() {
        // Arrange: 本月已用次数 = 最大次数
        RetrieveTransferAmountResponse transferResponse = createTransferAmountResponse(
                new BigDecimal("10000"), // monthlyUsed
                new BigDecimal("50000"), // yearlyUsed
                5 // usedMonthlyCount
        );

        when(restClientService.get(anyString(), anyMap(), eq(RetrieveTransferAmountResponse.class), anyInt(), anyBoolean()))
                .thenReturn(transferResponse);

        // Act & Assert
        TransferLimitExceededException exception = assertThrows(
                TransferLimitExceededException.class,
                () -> tradeLimitService.retrieveLimitations(requestHeader)
        );

        assertEquals("Monthly transfer count exceeded: 5", exception.getMessage());
    }

    @Test
    void testRetrieveLimitations_MonthlyAmountExceeded() {
        // Arrange: 本月已用金额 = 月限额
        RetrieveTransferAmountResponse transferResponse = createTransferAmountResponse(
                new BigDecimal("50000"), // monthlyUsed
                new BigDecimal("50000"), // yearlyUsed
                1 // usedMonthlyCount
        );

        when(restClientService.get(anyString(), anyMap(), eq(RetrieveTransferAmountResponse.class), anyInt(), anyBoolean()))
                .thenReturn(transferResponse);

        // Act & Assert
        TransferLimitExceededException exception = assertThrows(
                TransferLimitExceededException.class,
                () -> tradeLimitService.retrieveLimitations(requestHeader)
        );

        assertEquals("Monthly limit exceeded: 50000.00", exception.getMessage());
    }

    @Test
    void testRetrieveLimitations_YearlyAmountExceeded() {
        // Arrange: 年限额已用完
        RetrieveTransferAmountResponse transferResponse = createTransferAmountResponse(
                new BigDecimal("10000"), // monthlyUsed
                new BigDecimal("200000"), // yearlyUsed
                1 // usedMonthlyCount
        );

        when(restClientService.get(anyString(), anyMap(), eq(RetrieveTransferAmountResponse.class), anyInt(), anyBoolean()))
                .thenReturn(transferResponse);

        // Act & Assert
        TransferLimitExceededException exception = assertThrows(
                TransferLimitExceededException.class,
                () -> tradeLimitService.retrieveLimitations(requestHeader)
        );

        assertEquals("Yearly limit exceeded: 200000.00", exception.getMessage());
    }

    @Test
    void testRetrieveLimitations_LimitEnquiryResponseNull() {
        // Arrange: 服务返回 null
        when(restClientService.get(anyString(), anyMap(), eq(LimitEnquiryResponse.class), anyInt(), anyBoolean()))
                .thenReturn(null);

        // Act
        RetrieveTransferLimitResponse response = tradeLimitService.retrieveLimitations(requestHeader);

        // Assert
        assertNotNull(response);
        assertEquals(BigDecimal.ZERO, response.getData().getAvailableTodayAmount());
    }

    @Test
    void testRetrieveLimitations_P2PSDetailNotFound() {
        // Arrange: 返回的响应中没有 P2PS 类型的限额
        LimitEnquiryResponse limitResponse = new LimitEnquiryResponse();
        // 没有设置任何 TransactionLimitDetailList

        when(restClientService.get(anyString(), anyMap(), eq(LimitEnquiryResponse.class), anyInt(), anyBoolean()))
                .thenReturn(limitResponse);

        // Act
        RetrieveTransferLimitResponse response = tradeLimitService.retrieveLimitations(requestHeader);

        // Assert
        assertNotNull(response);
        assertEquals(BigDecimal.ZERO, response.getData().getAvailableTodayAmount());
    }

    @Test
    void testRetrieveLimitations_RetrieveTransferAmountException() {
        // Arrange: 调用 SRBP 失败
        when(restClientService.get(anyString(), anyMap(), eq(RetrieveTransferAmountResponse.class), anyInt(), anyBoolean()))
                .thenThrow(new RuntimeException("SRBP service down"));

        // Act & Assert
        InternalServerErrorException exception = assertThrows(
                InternalServerErrorException.class,
                () -> tradeLimitService.retrieveLimitations(requestHeader)
        );

        assertEquals(ErrorCodes.UNEXPECTED_RESULT_SRBP_ONLINE_ERROR.getValue(), exception.getMessage());
    }

    @Test
    void testRetrieveLimitations_LimitEnquiryCallException() {
        // Arrange: 获取限额查询失败
        when(restClientService.get(anyString(), anyMap(), eq(LimitEnquiryResponse.class), anyInt(), anyBoolean()))
                .thenThrow(new RuntimeException("CLC service down"));

        // Act & Assert
        InternalServerErrorException exception = assertThrows(
                InternalServerErrorException.class,
                () -> tradeLimitService.retrieveLimitations(requestHeader)
        );

        assertEquals("CLC_UNEXPECTED_ERROR", exception.getMessage());
    }

    // ==================== 测试辅助方法 ====================

    @Test
    void testExtractTransactionDetails_NullResponse() {
        List<TransactionLimitDetailList> result = tradeLimitService.extractTransactionDetails(null);
        assertNull(result);
    }

    @Test
    void testExtractTransactionDetails_EmptyResponse() {
        LimitEnquiryResponse response = new LimitEnquiryResponse();
        List<TransactionLimitDetailList> result = tradeLimitService.extractTransactionDetails(response);
        assertNull(result);
    }

    @Test
    void testExtractTransactionDetails_ValidResponse() {
        TransactionLimitDetailList detail = new TransactionLimitDetailList();
        detail.setLimitType("P2PS");

        LimitEnquiryResponse response = new LimitEnquiryResponse();
        response.setCbHkHbapObsShrdClcTranLmtEnqWpbSrvOperationResponse(
                new com.hsbc.trade.transfer.domain.limit.CbHkHbapObsShrdClcTranLmtEnqWpbSrvOperationResponse()
                        .withResponsePayload(
                                new com.hsbc.trade.transfer.domain.limit.ResponsePayload()
                                        .withResponseWork(
                                                new com.hsbc.trade.transfer.domain.limit.ResponseWork()
                                                        .withResponseWorkRecord(
                                                                new com.hsbc.trade.transfer.domain.limit.ResponseWorkRecord()
                                                                        .withTransactionLimitDetail(Collections.singletonList(detail))
                                                        )
                                        )
                        )
        );

        List<TransactionLimitDetailList> result = tradeLimitService.extractTransactionDetails(response);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("P2PS", result.get(0).getLimitType());
    }

    @Test
    void testFindP2PSLimitDetail_Found() {
        TransactionLimitDetailList p2ps = new TransactionLimitDetailList();
        p2ps.setLimitType("P2PS");

        TransactionLimitDetailList other = new TransactionLimitDetailList();
        other.setLimitType("OTHER");

        List<TransactionLimitDetailList> details = Arrays.asList(p2ps, other);

        TransactionLimitDetailList result = tradeLimitService.findP2PSLimitDetail(details);
        assertEquals(p2ps, result);
    }

    @Test
    void testFindP2PSLimitDetail_NotFound() {
        List<TransactionLimitDetailList> details = Collections.singletonList(
                new TransactionLimitDetailList().setLimitType("OTHER")
        );

        TransactionLimitDetailList result = tradeLimitService.findP2PSLimitDetail(details);
        assertNull(result);
    }

    @Test
    void testExtractUtilizedAmount_Null() {
        TransactionLimitDetailList detail = new TransactionLimitDetailList();
        // 不设置 utilizedLimitAmount
        BigDecimal result = tradeLimitService.extractUtilizedAmount(detail);
        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void testExtractUtilizedAmount_Valid() {
        TransactionLimitDetailList detail = new TransactionLimitDetailList();
        detail.setUtilizedLimitAmount(
                new com.hsbc.trade.transfer.domain.limit.UtilizedLimitAmount()
                        .withUtilizedLimitAmountValue(12345)
                        .withUtilizedLimitAmountDecimal(2)
        );

        BigDecimal result = tradeLimitService.extractUtilizedAmount(detail);
        assertEquals(new BigDecimal("123.45"), result);
    }

    @Test
    void testExtractMaxAmount_Null() {
        TransactionLimitDetailList detail = new TransactionLimitDetailList();
        BigDecimal result = tradeLimitService.extractMaxAmount(detail);
        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void testExtractMaxAmount_Valid() {
        TransactionLimitDetailList detail = new TransactionLimitDetailList();
        detail.setCurrentLimitAmount(
                new com.hsbc.trade.transfer.domain.limit.CurrentLimitAmount()
                        .withCurrentLimitAmountValue(99999)
                        .withCurrentLimitAmountDecimal(2)
        );

        BigDecimal result = tradeLimitService.extractMaxAmount(detail);
        assertEquals(new BigDecimal("999.99"), result);
    }

    @Test
    void testGetValueDate() {
        String result = tradeLimitService.getValueDate();
        assertEquals(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), result);
    }

    @Test
    void testBuildBaseHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put(HTTPRequestHeaderConstants.X_HSBC_Saml, "saml1");
        headers.put(HTTPRequestHeaderConstants.X_HSBC_Saml3, "saml3");

        Map<String, String> result = tradeLimitService.buildBaseHeaders(headers);

        // 应该移除 SAML
        assertFalse(result.containsKey(HTTPRequestHeaderConstants.X_HSBC_Saml));
        assertFalse(result.containsKey(HTTPRequestHeaderConstants.X_HSBC_Saml3));

        // 应该包含 E2E Token
        assertEquals("mock-e2e-token", result.get(HTTPRequestHeaderConstants.X_HSBC_E2E_Trust_Token));
        assertEquals("sourceSystemId", result.get(HTTPRequestHeaderConstants.X_HSBC_Source_System_Id));
        assertEquals("clientId", result.get(HTTPRequestHeaderConstants.X_HSBC_Client_Id));
        assertEquals("clientSecret", result.get(HTTPRequestHeaderConstants.X_HSBC_Client_Secret));
        assertEquals("targetSystemEnvironmentId", result.get(HTTPRequestHeaderConstants.X_HSBC_Target_System_Environment_Id));
        assertEquals("sessionCorrelationId", result.get(HTTPRequestHeaderConstants.X_HSBC_Session_Correlation_Id));
        assertEquals("gbgf", result.get(HTTPRequestHeaderConstants.X_HSBC_GBGF));
        assertEquals("clientIp", result.get(HTTPRequestHeaderConstants.HSBC_Client_Ip));
        assertTrue(result.containsKey(HTTPRequestHeaderConstants.X_HSBC_Request_Correlation_Id)); // UUID
    }

    @Test
    void testBuildContactEnquiryUri() {
        ContactEnquiryParams params = new ContactEnquiryParams.Builder()
                .bankNumber("BANK1")
                .channelIndicator("C")
                .enquiryChannel("EC")
                .customerId("CUST")
                .customerIdType("T")
                .valueDate("2025-11-20")
                .limitType("L")
                .sequentIndicator("S")
                .build();

        URI uri = tradeLimitService.buildContactEnquiryUri(params);

        assertEquals("https://example.com/contact-enquiry?bankNumber=BANK1&channelIndicator=C&enquiryChannel=EC&customerId=CUST&customerIdType=T&valueDate=2025-11-20&limitType=L&sequentIndicator=S", uri.toString());
    }

    // ==================== 辅助方法 ====================

    private LimitEnquiryResponse createLimitEnquiryResponse(String limitType, long utilizedValue, long maxValue) {
        TransactionLimitDetailList detail = new TransactionLimitDetailList();
        detail.setLimitType(limitType);
        detail.setUtilizedLimitAmount(
                new com.hsbc.trade.transfer.domain.limit.UtilizedLimitAmount()
                        .withUtilizedLimitAmountValue(utilizedValue)
                        .withUtilizedLimitAmountDecimal(2)
        );
        detail.setCurrentLimitAmount(
                new com.hsbc.trade.transfer.domain.limit.CurrentLimitAmount()
                        .withCurrentLimitAmountValue(maxValue)
                        .withCurrentLimitAmountDecimal(2)
        );

        LimitEnquiryResponse response = new LimitEnquiryResponse();
        response.setCbHkHbapObsShrdClcTranLmtEnqWpbSrvOperationResponse(
                new com.hsbc.trade.transfer.domain.limit.CbHkHbapObsShrdClcTranLmtEnqWpbSrvOperationResponse()
                        .withResponsePayload(
                                new com.hsbc.trade.transfer.domain.limit.ResponsePayload()
                                        .withResponseWork(
                                                new com.hsbc.trade.transfer.domain.limit.ResponseWork()
                                                        .withResponseWorkRecord(
                                                                new com.hsbc.trade.transfer.domain.limit.ResponseWorkRecord()
                                                                        .withTransactionLimitDetail(Collections.singletonList(detail))
                                                        )
                                        )
                        )
        );

        return response;
    }

    private RetrieveTransferAmountResponse createDefaultTransferAmountResponse() {
        return createTransferAmountResponse(
                new BigDecimal("10000"), // monthlyUsed
                new BigDecimal("50000"), // yearlyUsed
                1 // usedMonthlyCount
        );
    }

    private RetrieveTransferAmountResponse createTransferAmountResponse(BigDecimal monthlyUsed, BigDecimal yearlyUsed, int usedMonthlyCount) {
        RetrieveTransferAmountResponse response = new RetrieveTransferAmountResponse();
        RetrieveTransferAmountResponse.Data data = new RetrieveTransferAmountResponse.Data();
        data.setMonthlyTransferAmount(monthlyUsed);
        data.setYearlyTransferAmount(yearlyUsed);
        data.setMonthlyTransferCount(usedMonthlyCount);
        response.setData(data);
        return response;
    }
}