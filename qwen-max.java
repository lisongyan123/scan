import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.hsbc.rtp.client.sre.domain.SreRequest;
import com.hsbc.rtp.client.sre.domain.SreData;
import com.hsbc.trade.HTTPRequestHeaderConstants;
import com.hsbc.trade.service.RestClientService;
import com.hsbc.trade.transfer.domain.eligibility.RuleResponse;
import com.hsbc.trade.transfer.domain.eligibility.RuleResponseDTO;
import com.hsbc.trade.transfer.enums.ExceptionMessageEnum;
import com.hsbc.trade.transfer.service.impl.SreValidationServiceImpl;
import com.hsbc.trade.utils.E2ETrustTokenUtil;
import com.hsbc.trade.service.DuplicateSubmitPreventionService;
import com.hsbc.trade.service.impl.RetrieveCustomerProfilesServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.InternalServerErrorException;
import java.util.Collections;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class SreValidationServiceImplTest {

    @Mock
    private RestClientService mockRestClientService;

    @Mock
    private E2ETrustTokenUtil mockE2ETrustTokenUtil;

    @Mock
    private DuplicateSubmitPreventionService mockDuplicateSubmitPreventionService;

    @Mock
    private RetrieveCustomerProfilesServiceImpl mockRetrieveCustomerProfilesService;

    private SreValidationServiceImpl sreValidationServiceUnderTest;

    private final String TEST_RULE = "test_rule_name";
    private final String TEST_SENDER_CIN = "SENDER_CIN_123";
    private final String TEST_RECEIVER_CIN = "RECEIVER_CIN_456";
    private final String TEST_SRE_URL = "http://test-sre-service";

    @BeforeEach
    void setUp() {
        sreValidationServiceUnderTest = new SreValidationServiceImpl();
        // 注入 Mock 的依赖项
        sreValidationServiceUnderTest.restClientService = mockRestClientService;
        sreValidationServiceUnderTest.e2ETrustTokenUtil = mockE2ETrustTokenUtil;
        sreValidationServiceUnderTest.duplicateSubmitPreventionService = mockDuplicateSubmitPreventionService;
        sreValidationServiceUnderTest.retrieveCustomerProfilesService = mockRetrieveCustomerProfilesService;

        // 设置 SRE 服务的 URL (这通常通过 @Value 注解注入，在测试中需要手动设置)
        sreValidationServiceUnderTest.sreUrl = TEST_SRE_URL;
        // 可选：设置 targetSystemEnvironmentId，如果需要的话
        // sreValidationServiceUnderTest.targetSystemEnvironmentId = "TEST_ENV";
    }

    @Test
    void testCallSreForTransferValidation_Success() {
        // Arrange
        Map<String, String> inputHeaders = Map.of(
            HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "INPUT_CIN"
            // ... 其他可能的输入头 ...
        );
        Map<String, String> expectedHeaders = inputHeaders; // 如果 targetSystemEnvironmentId 为空，则 header 不变
        // 如果设置了 targetSystemEnvironmentId，expectedHeaders 应该包含该值
        // Map<String, String> expectedHeaders = new HashMap<>(inputHeaders);
        // expectedHeaders.put(HTTPRequestHeaderConstants.X_HSBC_Target_System_Environment_Id, "TEST_ENV");

        RuleResponse mockResponse = new RuleResponse(); // 假设构造函数可用
        RuleResponseDTO mockResult = new RuleResponseDTO(); // 假设构造函数可用
        // 设置 mockResponse 的内容，例如 mockResult.setReasonCodes(Collections.emptyList());
        mockResponse.setResults(mockResult);

        // Mock RestClientService.post 方法
        when(mockRestClientService.post(
                eq(TEST_SRE_URL),
                any(Map.class), // 验证时可以更具体，如 eq(expectedHeaders)
                any(SreRequest.class), // 验证时可以检查 SreRequest 对象的内容
                eq(RuleResponse.class),
                anyInt(),
                anyBoolean()))
                .thenReturn(mockResponse);

        // Act
        RuleResponse result = sreValidationServiceUnderTest.callSreForTransferValidation(
                TEST_RULE, TEST_SENDER_CIN, TEST_RECEIVER_CIN, inputHeaders);

        // Assert
        assertNotNull(result);
        assertEquals(mockResult, result.getResults()); // 验证返回的是我们 Mock 的对象
        // 验证 restClientService.post 被调用了一次
        verify(mockRestClientService, times(1)).post(
                eq(TEST_SRE_URL),
                any(Map.class),
                any(SreRequest.class),
                eq(RuleResponse.class),
                anyInt(),
                anyBoolean());

        // (可选) 验证 SreRequest 的内容是否正确构建
        // verify(mockRestClientService).post(
        //         eq(TEST_SRE_URL),
        //         any(Map.class),
        //         argThat(request -> {
        //             SreData data = request.getData();
        //             return TEST_RULE.equals(request.getRule()) &&
        //                    "N".equals(data.getCustomerIdType()) &&
        //                    TEST_SENDER_CIN.equals(data.getCustomerIdNumber()) &&
        //                    "N".equals(data.getReceiverCustomerIdType()) &&
        //                    TEST_RECEIVER_CIN.equals(data.getReceiverCustomerIdNumber()) &&
        //                    "I".equals(data.getInputChannel());
        //         }),
        //         eq(RuleResponse.class),
        //         anyInt(),
        //         anyBoolean());
    }

    @Test
    void testCallSreForTransferValidation_ServiceError_ThrowsException() {
        // Arrange
        Map<String, String> inputHeaders = Map.of(
            HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "INPUT_CIN"
        );

        // Mock RestClientService.post 方法抛出异常
        when(mockRestClientService.post(
                eq(TEST_SRE_URL),
                any(Map.class),
                any(SreRequest.class),
                eq(RuleResponse.class),
                anyInt(),
                anyBoolean()))
                .thenThrow(new RuntimeException("SRE Service Unavailable"));

        // Act & Assert
        InternalServerErrorException thrown = assertThrows(InternalServerErrorException.class, () -> {
            sreValidationServiceUnderTest.callSreForTransferValidation(
                    TEST_RULE, TEST_SENDER_CIN, TEST_RECEIVER_CIN, inputHeaders);
        });

        // 验证抛出的异常消息是否为预期的错误码
        assertTrue(thrown.getMessage().contains(ExceptionMessageEnum.SRE_CHECK_ERROR.getCode()));

        // 验证 restClientService.post 被调用了一次
        verify(mockRestClientService, times(1)).post(
                eq(TEST_SRE_URL),
                any(Map.class),
                any(SreRequest.class),
                eq(RuleResponse.class),
                anyInt(),
                anyBoolean());
    }
}
