import com.hsbc.trade.ErrorCodes;
import com.hsbc.trade.constant.HTTPRequestHeaderConstants;
import com.hsbc.trade.service.RestClientService;
import com.hsbc.trade.transfer.config.CustomerLimitConfig;
import com.hsbc.trade.transfer.domain.limit.*;
import com.hsbc.trade.transfer.enums.ExceptionMessageEnum;
import com.hsbc.trade.transfer.exception.TransferLimitExceededException;
import com.hsbc.trade.transfer.retrievetransferamount.RetrieveTransferAmountResponse;
import com.hsbc.trade.transfer.retrievetransferamount.RetrieveTransferAmountResponseData;
import com.hsbc.trade.transfer.retrievetransferlimit.RetrieveTransferLimitResponse;
import com.hsbc.trade.transfer.retrievetransferlimit.RetrieveTransferLimitResponseData;
import com.hsbc.trade.utils.E2ETrustTokenUtil;
import com.hsbc.trade.service.impl.RetrieveCustomerProfilesServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.ws.rs.InternalServerErrorException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeLimitServiceImplTest {

    @Mock
    private RestClientService restClientService;

    @Mock
    private CustomerLimitConfig customerLimitConfig;

    @Mock
    private E2ETrustTokenUtil e2ETrustTokenUtil;

    @Mock
    private RetrieveCustomerProfilesServiceImpl retrieveCustomerProfilesService;

    @InjectMocks
    private TradeLimitServiceImpl tradeLimitService;

    private Map<String, String> headers;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        headers = new HashMap<>();
        headers.put("X-HSBC-User-Id", "PERM123");
        // 设置依赖项
        ReflectionTestUtils.setField(tradeLimitService, "e2ETrustTokenUtil", e2ETrustTokenUtil);
        ReflectionTestUtils.setField(tradeLimitService, "retrieveCustomerProfilesService", retrieveCustomerProfilesService);
        // 设置其他字段的值...
        ReflectionTestUtils.setField(tradeLimitService, "gbgf", "GBGF");
        ReflectionTestUtils.setField(tradeLimitService, "sourceSystemId", "SRC");
        ReflectionTestUtils.setField(tradeLimitService, "clientIp", "127.0.0.1");
        ReflectionTestUtils.setField(tradeLimitService, "clientId", "CID");
        ReflectionTestUtils.setField(tradeLimitService, "clientSecret", "CSECRET");
        ReflectionTestUtils.setField(tradeLimitService, "targetSystemEnvironmentId", "DEV");
        ReflectionTestUtils.setField(tradeLimitService, "sessionCorrelationId", "SESSION");
        ReflectionTestUtils.setField(tradeLimitService, "bankNumber", "004");
        ReflectionTestUtils.setField(tradeLimitService, "channelIndicator", "I");
        ReflectionTestUtils.setField(tradeLimitService, "enquiryChannel", "O");
        ReflectionTestUtils.setField(tradeLimitService, "customerId", "CUST123");
        ReflectionTestUtils.setField(tradeLimitService, "customerIdType", "CIN");
        ReflectionTestUtils.setField(tradeLimitService, "limitType", "P2PS");
        ReflectionTestUtils.setField(tradeLimitService, "sequentIndicator", "N");
        ReflectionTestUtils.setField(tradeLimitService, "contactEnquiryUrl", "https://enquiry.example.com/limit");
        ReflectionTestUtils.setField(tradeLimitService, "srbpOnlineUrl", "https://data.example.com");

        lenient().when(e2ETrustTokenUtil.getE2ETrustToken()).thenReturn("dummy-token");
        lenient().when(retrieveCustomerProfilesService.getCIN(any())).thenReturn("dummy-cin");
    }

    // --- 现有的测试方法 ---
    @Test
    void testRetrieveLimitations_success() {
        lenient().when(customerLimitConfig.getDailyAmount()).thenReturn(BigDecimal.valueOf(1000));
        lenient().when(customerLimitConfig.getYearlyAmount()).thenReturn(BigDecimal.valueOf(10000));
        lenient().when(customerLimitConfig.getMonthlyAmount()).thenReturn(BigDecimal.valueOf(5000));
        lenient().when(customerLimitConfig.getMonthlyCount()).thenReturn(10);

        LimitEnquiryResponse limitEnquiryResponse = mock(LimitEnquiryResponse.class);
        LimitEnquiryResponsePayload limitEnquiryResponseDataWrapper = new LimitEnquiryResponsePayload();
        LimitEnquiryResponseDataWrapper responsePayload = new LimitEnquiryResponseDataWrapper();
        LimitEnquiryResponseRecord responseWork = new LimitEnquiryResponseRecord();
        LimitEnquiryResponseData responseWorkRecord = new LimitEnquiryResponseData();
        List<TransactionLimitDetailList> transactionLimitDetailList = new ArrayList<>();
        TransactionLimitDetailList detail = new TransactionLimitDetailList();
        detail.setLimitType("P2PS");
        CurrentLimitAmount currentLimit = new CurrentLimitAmount();
        currentLimit.setCurrentLimitAmountValue(100000L); // 1000.00
        currentLimit.setCurrentLimitAmountDecimal(2);
        detail.setCurrentLimitAmount(currentLimit);
        UtilizedLimitAmount utilizedLimit = new UtilizedLimitAmount();
        utilizedLimit.setUtilizedLimitAmountValue(20000L); // 200.00
        utilizedLimit.setUtilizedLimitAmountDecimal(2);
        detail.setUtilizedLimitAmount(utilizedLimit);
        transactionLimitDetailList.add(detail);
        responseWorkRecord.setTransactionLimitDetail(transactionLimitDetailList);
        responseWork.setResponseWorkRecord(responseWorkRecord);
        responsePayload.setResponseWork(responseWork);
        limitEnquiryResponseDataWrapper.setResponsePayload(responsePayload);
        lenient().when(limitEnquiryResponse.getCbHkHbapObsShrdClcTranLmtEnqWpbSrvOperationResponse()).thenReturn(limitEnquiryResponseDataWrapper);
        lenient().when(restClientService.get(anyString(), anyMap(), eq(LimitEnquiryResponse.class), anyInt(), anyBoolean()))
                .thenReturn(limitEnquiryResponse);

        RetrieveTransferAmountResponse transferAmountResponse = new RetrieveTransferAmountResponse();
        RetrieveTransferAmountResponseData data = new RetrieveTransferAmountResponseData();
        data.setMonthlyTransferCount(2);
        data.setMonthlyTransferAmount(BigDecimal.valueOf(100));
        data.setYearlyTransferAmount(BigDecimal.valueOf(200));
        transferAmountResponse.setData(data);
        lenient().when(restClientService.get(contains("/trade-amount"), anyMap(), eq(RetrieveTransferAmountResponse.class), anyInt(), anyBoolean()))
                .thenReturn(transferAmountResponse);

        RetrieveTransferLimitResponse response = tradeLimitService.retrieveLimitations(headers);

        assertThat(response).isNotNull();
        assertThat(response.getData().getMaxDailyLimitedAmount()).isEqualTo(BigDecimal.valueOf(1000));
        assertThat(response.getData().getAvailableTodayAmount()).isEqualTo(BigDecimal.valueOf(800)); // 1000 - 200
        assertThat(response.getData().getAvailableMonthlyTransferCount()).isEqualTo("8"); // 10 - 2
        assertThat(response.getData().getAvailableMonthToDateAmount()).isEqualTo(BigDecimal.valueOf(4900)); // 5000 - 100
        assertThat(response.getData().getAvailableYearToDateAmount()).isEqualTo(BigDecimal.valueOf(9800)); // 10000 - 200
        assertThat(response.getData().getAsofDateTime()).isNotNull();
    }

    @Test
    void testRetrieveLimitations_dailyOverLimit_throwsException() {
        lenient().when(customerLimitConfig.getDailyAmount()).thenReturn(BigDecimal.valueOf(100));
        lenient().when(customerLimitConfig.getYearlyAmount()).thenReturn(BigDecimal.valueOf(10000));
        lenient().when(customerLimitConfig.getMonthlyAmount()).thenReturn(BigDecimal.valueOf(5000));
        lenient().when(customerLimitConfig.getMonthlyCount()).thenReturn(10);

        LimitEnquiryResponse limitEnquiryResponse = mock(LimitEnquiryResponse.class);
        LimitEnquiryResponsePayload limitEnquiryResponseDataWrapper = new LimitEnquiryResponsePayload();
        LimitEnquiryResponseDataWrapper responsePayload = new LimitEnquiryResponseDataWrapper();
        LimitEnquiryResponseRecord responseWork = new LimitEnquiryResponseRecord();
        LimitEnquiryResponseData responseWorkRecord = new LimitEnquiryResponseData();
        List<TransactionLimitDetailList> transactionLimitDetailList = new ArrayList<>();
        TransactionLimitDetailList detail = new TransactionLimitDetailList();
        detail.setLimitType("P2PS");
        CurrentLimitAmount currentLimit = new CurrentLimitAmount();
        currentLimit.setCurrentLimitAmountValue(10000L); // 100.00
        currentLimit.setCurrentLimitAmountDecimal(2);
        detail.setCurrentLimitAmount(currentLimit);
        UtilizedLimitAmount utilizedLimit = new UtilizedLimitAmount();
        utilizedLimit.setUtilizedLimitAmountValue(15000L); // 150.00
        utilizedLimit.setUtilizedLimitAmountDecimal(2);
        detail.setUtilizedLimitAmount(utilizedLimit);
        transactionLimitDetailList.add(detail);
        responseWorkRecord.setTransactionLimitDetail(transactionLimitDetailList);
        responseWork.setResponseWorkRecord(responseWorkRecord);
        responsePayload.setResponseWork(responseWork);
        limitEnquiryResponseDataWrapper.setResponsePayload(responsePayload);
        lenient().when(limitEnquiryResponse.getCbHkHbapObsShrdClcTranLmtEnqWpbSrvOperationResponse()).thenReturn(limitEnquiryResponseDataWrapper);
        lenient().when(restClientService.get(anyString(), anyMap(), eq(LimitEnquiryResponse.class), anyInt(), anyBoolean()))
                .thenReturn(limitEnquiryResponse);

        RetrieveTransferAmountResponse transferAmountResponse = new RetrieveTransferAmountResponse();
        RetrieveTransferAmountResponseData data = new RetrieveTransferAmountResponseData();
        data.setMonthlyTransferCount(0);
        data.setMonthlyTransferAmount(BigDecimal.ZERO);
        data.setYearlyTransferAmount(BigDecimal.ZERO);
        transferAmountResponse.setData(data);
        lenient().when(restClientService.get(contains("/trade-amount"), anyMap(), eq(RetrieveTransferAmountResponse.class), anyInt(), anyBoolean()))
                .thenReturn(transferAmountResponse);

        assertThrows(TransferLimitExceededException.class, () -> tradeLimitService.retrieveLimitations(headers));
    }

    // --- 新增的测试方法 ---

    @Test
    void testRetrieveLimitations_dailyLimitZeroAvailableButMaxIsZero_throwsException() {
        lenient().when(customerLimitConfig.getDailyAmount()).thenReturn(BigDecimal.valueOf(100));
        lenient().when(customerLimitConfig.getYearlyAmount()).thenReturn(BigDecimal.valueOf(10000));
        lenient().when(customerLimitConfig.getMonthlyAmount()).thenReturn(BigDecimal.valueOf(5000));
        lenient().when(customerLimitConfig.getMonthlyCount()).thenReturn(10);

        LimitEnquiryResponse limitEnquiryResponse = mock(LimitEnquiryResponse.class);
        LimitEnquiryResponsePayload limitEnquiryResponseDataWrapper = new LimitEnquiryResponsePayload();
        LimitEnquiryResponseDataWrapper responsePayload = new LimitEnquiryResponseDataWrapper();
        LimitEnquiryResponseRecord responseWork = new LimitEnquiryResponseRecord();
        LimitEnquiryResponseData responseWorkRecord = new LimitEnquiryResponseData();
        List<TransactionLimitDetailList> transactionLimitDetailList = new ArrayList<>();
        TransactionLimitDetailList detail = new TransactionLimitDetailList();
        detail.setLimitType("P2PS");
        // 模拟已用金额等于最大金额，导致可用金额为0，但最大金额本身不为0
        CurrentLimitAmount currentLimit = new CurrentLimitAmount();
        currentLimit.setCurrentLimitAmountValue(10000L); // 100.00
        currentLimit.setCurrentLimitAmountDecimal(2);
        detail.setCurrentLimitAmount(currentLimit);
        UtilizedLimitAmount utilizedLimit = new UtilizedLimitAmount();
        utilizedLimit.setUtilizedLimitAmountValue(10000L); // 100.00
        utilizedLimit.setUtilizedLimitAmountDecimal(2);
        detail.setUtilizedLimitAmount(utilizedLimit);
        transactionLimitDetailList.add(detail);
        responseWorkRecord.setTransactionLimitDetail(transactionLimitDetailList);
        responseWork.setResponseWorkRecord(responseWorkRecord);
        responsePayload.setResponseWork(responseWork);
        limitEnquiryResponseDataWrapper.setResponsePayload(responsePayload);
        lenient().when(limitEnquiryResponse.getCbHkHbapObsShrdClcTranLmtEnqWpbSrvOperationResponse()).thenReturn(limitEnquiryResponseDataWrapper);
        lenient().when(restClientService.get(anyString(), anyMap(), eq(LimitEnquiryResponse.class), anyInt(), anyBoolean()))
                .thenReturn(limitEnquiryResponse);

        RetrieveTransferAmountResponse transferAmountResponse = new RetrieveTransferAmountResponse();
        RetrieveTransferAmountResponseData data = new RetrieveTransferAmountResponseData();
        data.setMonthlyTransferCount(0);
        data.setMonthlyTransferAmount(BigDecimal.ZERO);
        data.setYearlyTransferAmount(BigDecimal.ZERO);
        transferAmountResponse.setData(data);
        lenient().when(restClientService.get(contains("/trade-amount"), anyMap(), eq(RetrieveTransferAmountResponse.class), anyInt(), anyBoolean()))
                .thenReturn(transferAmountResponse);

        assertThrows(TransferLimitExceededException.class, () -> tradeLimitService.retrieveLimitations(headers));
    }

    @Test
    void testRetrieveLimitations_dailyLimitNoP2PSRecord() {
        lenient().when(customerLimitConfig.getDailyAmount()).thenReturn(BigDecimal.valueOf(1000));
        lenient().when(customerLimitConfig.getYearlyAmount()).thenReturn(BigDecimal.valueOf(10000));
        lenient().when(customerLimitConfig.getMonthlyAmount()).thenReturn(BigDecimal.valueOf(5000));
        lenient().when(customerLimitConfig.getMonthlyCount()).thenReturn(10);

        LimitEnquiryResponse limitEnquiryResponse = mock(LimitEnquiryResponse.class);
        LimitEnquiryResponsePayload limitEnquiryResponseDataWrapper = new LimitEnquiryResponsePayload();
        LimitEnquiryResponseDataWrapper responsePayload = new LimitEnquiryResponseDataWrapper();
        LimitEnquiryResponseRecord responseWork = new LimitEnquiryResponseRecord();
        LimitEnquiryResponseData responseWorkRecord = new LimitEnquiryResponseData();
        // 模拟返回列表，但没有P2PS类型的记录
        List<TransactionLimitDetailList> transactionLimitDetailList = new ArrayList<>();
        TransactionLimitDetailList otherDetail = new TransactionLimitDetailList();
        otherDetail.setLimitType("OTHER");
        transactionLimitDetailList.add(otherDetail);
        responseWorkRecord.setTransactionLimitDetail(transactionLimitDetailList);
        responseWork.setResponseWorkRecord(responseWorkRecord);
        responsePayload.setResponseWork(responseWork);
        limitEnquiryResponseDataWrapper.setResponsePayload(responsePayload);
        lenient().when(limitEnquiryResponse.getCbHkHbapObsShrdClcTranLmtEnqWpbSrvOperationResponse()).thenReturn(limitEnquiryResponseDataWrapper);
        lenient().when(restClientService.get(anyString(), anyMap(), eq(LimitEnquiryResponse.class), anyInt(), anyBoolean()))
                .thenReturn(limitEnquiryResponse);

        RetrieveTransferAmountResponse transferAmountResponse = new RetrieveTransferAmountResponse();
        RetrieveTransferAmountResponseData data = new RetrieveTransferAmountResponseData();
        data.setMonthlyTransferCount(2);
        data.setMonthlyTransferAmount(BigDecimal.valueOf(100));
        data.setYearlyTransferAmount(BigDecimal.valueOf(200));
        transferAmountResponse.setData(data);
        lenient().when(restClientService.get(contains("/trade-amount"), anyMap(), eq(RetrieveTransferAmountResponse.class), anyInt(), anyBoolean()))
                .thenReturn(transferAmountResponse);

        RetrieveTransferLimitResponse response = tradeLimitService.retrieveLimitations(headers);

        assertThat(response).isNotNull();
        // 没有找到P2PS记录，可用金额应为0
        assertThat(response.getData().getAvailableTodayAmount()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void testRetrieveLimitations_dailyLimitNullResponseDetails() {
        lenient().when(customerLimitConfig.getDailyAmount()).thenReturn(BigDecimal.valueOf(1000));
        lenient().when(customerLimitConfig.getYearlyAmount()).thenReturn(BigDecimal.valueOf(10000));
        lenient().when(customerLimitConfig.getMonthlyAmount()).thenReturn(BigDecimal.valueOf(5000));
        lenient().when(customerLimitConfig.getMonthlyCount()).thenReturn(10);

        // 模拟整个响应对象为null
        LimitEnquiryResponse limitEnquiryResponse = mock(LimitEnquiryResponse.class);
        lenient().when(limitEnquiryResponse.getCbHkHbapObsShrdClcTranLmtEnqWpbSrvOperationResponse()).thenReturn(null);
        lenient().when(restClientService.get(anyString(), anyMap(), eq(LimitEnquiryResponse.class), anyInt(), anyBoolean()))
                .thenReturn(limitEnquiryResponse);

        RetrieveTransferAmountResponse transferAmountResponse = new RetrieveTransferAmountResponse();
        RetrieveTransferAmountResponseData data = new RetrieveTransferAmountResponseData();
        data.setMonthlyTransferCount(2);
        data.setMonthlyTransferAmount(BigDecimal.valueOf(100));
        data.setYearlyTransferAmount(BigDecimal.valueOf(200));
        transferAmountResponse.setData(data);
        lenient().when(restClientService.get(contains("/trade-amount"), anyMap(), eq(RetrieveTransferAmountResponse.class), anyInt(), anyBoolean()))
                .thenReturn(transferAmountResponse);

        RetrieveTransferLimitResponse response = tradeLimitService.retrieveLimitations(headers);

        assertThat(response).isNotNull();
        // 响应为null，可用金额应为0
        assertThat(response.getData().getAvailableTodayAmount()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void testRetrieveLimitations_monthlyCountOverLimit_throwsException() {
        lenient().when(customerLimitConfig.getDailyAmount()).thenReturn(BigDecimal.valueOf(1000));
        lenient().when(customerLimitConfig.getYearlyAmount()).thenReturn(BigDecimal.valueOf(10000));
        lenient().when(customerLimitConfig.getMonthlyAmount()).thenReturn(BigDecimal.valueOf(5000));
        lenient().when(customerLimitConfig.getMonthlyCount()).thenReturn(5); // 设置为5次

        LimitEnquiryResponse limitEnquiryResponse = mock(LimitEnquiryResponse.class);
        LimitEnquiryResponsePayload limitEnquiryResponseDataWrapper = new LimitEnquiryResponsePayload();
        LimitEnquiryResponseDataWrapper responsePayload = new LimitEnquiryResponseDataWrapper();
        LimitEnquiryResponseRecord responseWork = new LimitEnquiryResponseRecord();
        LimitEnquiryResponseData responseWorkRecord = new LimitEnquiryResponseData();
        List<TransactionLimitDetailList> transactionLimitDetailList = new ArrayList<>();
        TransactionLimitDetailList detail = new TransactionLimitDetailList();
        detail.setLimitType("P2PS");
        CurrentLimitAmount currentLimit = new CurrentLimitAmount();
        currentLimit.setCurrentLimitAmountValue(100000L); // 1000.00
        currentLimit.setCurrentLimitAmountDecimal(2);
        detail.setCurrentLimitAmount(currentLimit);
        UtilizedLimitAmount utilizedLimit = new UtilizedLimitAmount();
        utilizedLimit.setUtilizedLimitAmountValue(0L);
        utilizedLimit.setUtilizedLimitAmountDecimal(2);
        detail.setUtilizedLimitAmount(utilizedLimit);
        transactionLimitDetailList.add(detail);
        responseWorkRecord.setTransactionLimitDetail(transactionLimitDetailList);
        responseWork.setResponseWorkRecord(responseWorkRecord);
        responsePayload.setResponseWork(responseWork);
        limitEnquiryResponseDataWrapper.setResponsePayload(responsePayload);
        lenient().when(limitEnquiryResponse.getCbHkHbapObsShrdClcTranLmtEnqWpbSrvOperationResponse()).thenReturn(limitEnquiryResponseDataWrapper);
        lenient().when(restClientService.get(anyString(), anyMap(), eq(LimitEnquiryResponse.class), anyInt(), anyBoolean()))
                .thenReturn(limitEnquiryResponse);

        RetrieveTransferAmountResponse transferAmountResponse = new RetrieveTransferAmountResponse();
        RetrieveTransferAmountResponseData data = new RetrieveTransferAmountResponseData();
        // 模拟已用次数等于配置的最大次数
        data.setMonthlyTransferCount(5); // 已用5次
        data.setMonthlyTransferAmount(BigDecimal.valueOf(100));
        data.setYearlyTransferAmount(BigDecimal.valueOf(200));
        transferAmountResponse.setData(data);
        lenient().when(restClientService.get(contains("/trade-amount"), anyMap(), eq(RetrieveTransferAmountResponse.class), anyInt(), anyBoolean()))
                .thenReturn(transferAmountResponse);

        assertThrows(TransferLimitExceededException.class, () -> tradeLimitService.retrieveLimitations(headers));
    }

    @Test
    void testRetrieveLimitations_monthlyAmountOverLimit_throwsException() {
        lenient().when(customerLimitConfig.getDailyAmount()).thenReturn(BigDecimal.valueOf(1000));
        lenient().when(customerLimitConfig.getYearlyAmount()).thenReturn(BigDecimal.valueOf(10000));
        lenient().when(customerLimitConfig.getMonthlyAmount()).thenReturn(BigDecimal.valueOf(500)); // 设置为500
        lenient().when(customerLimitConfig.getMonthlyCount()).thenReturn(10);

        LimitEnquiryResponse limitEnquiryResponse = mock(LimitEnquiryResponse.class);
        LimitEnquiryResponsePayload limitEnquiryResponseDataWrapper = new LimitEnquiryResponsePayload();
        LimitEnquiryResponseDataWrapper responsePayload = new LimitEnquiryResponseDataWrapper();
        LimitEnquiryResponseRecord responseWork = new LimitEnquiryResponseRecord();
        LimitEnquiryResponseData responseWorkRecord = new LimitEnquiryResponseData();
        List<TransactionLimitDetailList> transactionLimitDetailList = new ArrayList<>();
        TransactionLimitDetailList detail = new TransactionLimitDetailList();
        detail.setLimitType("P2PS");
        CurrentLimitAmount currentLimit = new CurrentLimitAmount();
        currentLimit.setCurrentLimitAmountValue(100000L); // 1000.00
        currentLimit.setCurrentLimitAmountDecimal(2);
        detail.setCurrentLimitAmount(currentLimit);
        UtilizedLimitAmount utilizedLimit = new UtilizedLimitAmount();
        utilizedLimit.setUtilizedLimitAmountValue(0L);
        utilizedLimit.setUtilizedLimitAmountDecimal(2);
        detail.setUtilizedLimitAmount(utilizedLimit);
        transactionLimitDetailList.add(detail);
        responseWorkRecord.setTransactionLimitDetail(transactionLimitDetailList);
        responseWork.setResponseWorkRecord(responseWorkRecord);
        responsePayload.setResponseWork(responseWork);
        limitEnquiryResponseDataWrapper.setResponsePayload(responsePayload);
        lenient().when(limitEnquiryResponse.getCbHkHbapObsShrdClcTranLmtEnqWpbSrvOperationResponse()).thenReturn(limitEnquiryResponseDataWrapper);
        lenient().when(restClientService.get(anyString(), anyMap(), eq(LimitEnquiryResponse.class), anyInt(), anyBoolean()))
                .thenReturn(limitEnquiryResponse);

        RetrieveTransferAmountResponse transferAmountResponse = new RetrieveTransferAmountResponse();
        RetrieveTransferAmountResponseData data = new RetrieveTransferAmountResponseData();
        data.setMonthlyTransferCount(2);
        // 模拟已用金额等于配置的最大金额
        data.setMonthlyTransferAmount(BigDecimal.valueOf(500)); // 已用500
        data.setYearlyTransferAmount(BigDecimal.valueOf(200));
        transferAmountResponse.setData(data);
        lenient().when(restClientService.get(contains("/trade-amount"), anyMap(), eq(RetrieveTransferAmountResponse.class), anyInt(), anyBoolean()))
                .thenReturn(transferAmountResponse);

        assertThrows(TransferLimitExceededException.class, () -> tradeLimitService.retrieveLimitations(headers));
    }

    @Test
    void testRetrieveLimitations_yearlyAmountOverLimit_throwsException() {
        lenient().when(customerLimitConfig.getDailyAmount()).thenReturn(BigDecimal.valueOf(1000));
        lenient().when(customerLimitConfig.getYearlyAmount()).thenReturn(BigDecimal.valueOf(500)); // 设置为500
        lenient().when(customerLimitConfig.getMonthlyAmount()).thenReturn(BigDecimal.valueOf(5000));
        lenient().when(customerLimitConfig.getMonthlyCount()).thenReturn(10);

        LimitEnquiryResponse limitEnquiryResponse = mock(LimitEnquiryResponse.class);
        LimitEnquiryResponsePayload limitEnquiryResponseDataWrapper = new LimitEnquiryResponsePayload();
        LimitEnquiryResponseDataWrapper responsePayload = new LimitEnquiryResponseDataWrapper();
        LimitEnquiryResponseRecord responseWork = new LimitEnquiryResponseRecord();
        LimitEnquiryResponseData responseWorkRecord = new LimitEnquiryResponseData();
        List<TransactionLimitDetailList> transactionLimitDetailList = new ArrayList<>();
        TransactionLimitDetailList detail = new TransactionLimitDetailList();
        detail.setLimitType("P2PS");
        CurrentLimitAmount currentLimit = new CurrentLimitAmount();
        currentLimit.setCurrentLimitAmountValue(100000L); // 1000.00
        currentLimit.setCurrentLimitAmountDecimal(2);
        detail.setCurrentLimitAmount(currentLimit);
        UtilizedLimitAmount utilizedLimit = new UtilizedLimitAmount();
        utilizedLimit.setUtilizedLimitAmountValue(0L);
        utilizedLimit.setUtilizedLimitAmountDecimal(2);
        detail.setUtilizedLimitAmount(utilizedLimit);
        transactionLimitDetailList.add(detail);
        responseWorkRecord.setTransactionLimitDetail(transactionLimitDetailList);
        responseWork.setResponseWorkRecord(responseWorkRecord);
        responsePayload.setResponseWork(responseWork);
        limitEnquiryResponseDataWrapper.setResponsePayload(responsePayload);
        lenient().when(limitEnquiryResponse.getCbHkHbapObsShrdClcTranLmtEnqWpbSrvOperationResponse()).thenReturn(limitEnquiryResponseDataWrapper);
        lenient().when(restClientService.get(anyString(), anyMap(), eq(LimitEnquiryResponse.class), anyInt(), anyBoolean()))
                .thenReturn(limitEnquiryResponse);

        RetrieveTransferAmountResponse transferAmountResponse = new RetrieveTransferAmountResponse();
        RetrieveTransferAmountResponseData data = new RetrieveTransferAmountResponseData();
        data.setMonthlyTransferCount(2);
        data.setMonthlyTransferAmount(BigDecimal.valueOf(100));
        // 模拟已用金额等于配置的最大金额
        data.setYearlyTransferAmount(BigDecimal.valueOf(500)); // 已用500
        transferAmountResponse.setData(data);
        lenient().when(restClientService.get(contains("/trade-amount"), anyMap(), eq(RetrieveTransferAmountResponse.class), anyInt(), anyBoolean()))
                .thenReturn(transferAmountResponse);

        assertThrows(TransferLimitExceededException.class, () -> tradeLimitService.retrieveLimitations(headers));
    }

    @Test
    void testRetrieveTransferAmount_serviceThrowsException() {
        lenient().when(restClientService.get(eq("https://data.example.com/trade-amount"), anyMap(), eq(RetrieveTransferAmountResponse.class), anyInt(), anyBoolean()))
                .thenThrow(new RuntimeException("Service Error"));

        assertThrows(InternalServerErrorException.class, () -> tradeLimitService.retrieveTransferAmount(headers));
        // 验证抛出的错误码
        try {
            tradeLimitService.retrieveTransferAmount(headers);
        } catch (InternalServerErrorException e) {
            // 检查错误消息是否包含期望的错误码
            assertThat(e.getMessage()).contains(ErrorCodes.UNEXPECTED_RESULT_SRBP_ONLINE_ERROR.getValue());
        }
    }

    @Test
    void testFetchLimitEnquiryResponse_serviceThrowsException() {
        lenient().when(restClientService.get(anyString(), anyMap(), eq(LimitEnquiryResponse.class), anyInt(), anyBoolean()))
                .thenThrow(new RuntimeException("CLC Error"));

        assertThrows(InternalServerErrorException.class, () -> tradeLimitService.retrieveLimitations(headers));
        // 验证抛出的错误码
        try {
            tradeLimitService.retrieveLimitations(headers);
        } catch (InternalServerErrorException e) {
            // 检查错误消息是否包含期望的错误码
            assertThat(e.getMessage()).contains(ExceptionMessageEnum.CLC_UNEXPECTED_ERROR.getCode());
        }
    }

    @Test
    void testGetValueDate_returnsCurrentDate() {
        String expectedDate = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String actualDate = tradeLimitService.getValueDate();
        assertThat(actualDate).isEqualTo(expectedDate);
    }

    @Test
    void testBuildBaseHeaders_constructsCorrectly() {
        Map<String, String> inputHeaders = new HashMap<>();
        inputHeaders.put(HTTPRequestHeaderConstants.X_HSBC_Customer_Id, "CUST123");
        inputHeaders.put("SomeOtherHeader", "SomeValue");

        Map<String, String> baseHeaders = tradeLimitService.buildBaseHeaders(inputHeaders);

        assertThat(baseHeaders).isNotNull();
        assertThat(baseHeaders).containsEntry(HTTPRequestHeaderConstants.X_HSBC_Customer_Id, "CUST123");
        assertThat(baseHeaders).containsEntry("SomeOtherHeader", "SomeValue");
        assertThat(baseHeaders).containsKey(HTTPRequestHeaderConstants.X_HSBC_E2E_Trust_Token);
        assertThat(baseHeaders).containsEntry(HTTPRequestHeaderConstants.X_HSBC_Source_System_Id, "SRC");
        // Add more assertions for other expected headers...
    }

    @Test
    void testBuildSensitiveHeaders_constructsCorrectly() {
        Map<String, String> baseHeaders = new HashMap<>();
        baseHeaders.put("ExistingHeader", "ExistingValue");

        String sensitiveKey = "secretKey";
        String sensitiveValue = "secretValue";
        Map<String, String> sensitiveHeaders = tradeLimitService.buildSensitiveHeaders(baseHeaders, sensitiveKey, sensitiveValue);

        assertThat(sensitiveHeaders).isNotSameAs(baseHeaders);
        assertThat(sensitiveHeaders).containsKey(HTTPRequestHeaderConstants.X_HSBC_Sensitive_Data);
        // You could add more specific assertions on the JSON content if needed.
        assertThat(sensitiveHeaders).containsEntry("ExistingHeader", "ExistingValue");
    }
}
