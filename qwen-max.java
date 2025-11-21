import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.hsbc.trade.HTTPRequestHeaderConstants;
import com.hsbc.trade.service.RestClientService;
import com.hsbc.trade.transfer.constant.TransferQueryParameterConstant;
import com.hsbc.trade.transfer.domain.RetrieveCustomerAccountsIdListResponse;
import com.hsbc.trade.transfer.enums.ExceptionMessageEnum;
import com.hsbc.trade.utils.E2ETrustTokenUtil;
import com.hsbc.trade.service.DuplicateSubmitPreventionService;
import com.hsbc.trade.service.impl.RetrieveCustomerProfilesServiceImpl;
import com.hsbc.trade.transfer.service.AbstractRestService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.BadRequestException;
import java.lang.reflect.Field;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class AbstractRestServiceRetrieveAccountIdWithCheckSumTest {

    @Mock
    private RestClientService mockRestClientService;

    @Mock
    private E2ETrustTokenUtil mockE2ETrustTokenUtil;

    @Mock
    private DuplicateSubmitPreventionService mockDuplicateSubmitPreventionService;

    @Mock
    private RetrieveCustomerProfilesServiceImpl mockRetrieveCustomerProfilesService;

    private TestableAbstractRestService abstractRestServiceUnderTest; // 使用之前的测试辅助类

    private final String TEST_CHECKSUM = "TEST_CHECKSUM_67890";
    private final String TEST_CIN = "TEST_CIN_12345";
    private final String TEST_CUSTOMER_ACCOUNT_URL = "http://test-customer-account";

    static class TestableAbstractRestService extends AbstractRestService {
        public void setRestClientService(RestClientService restClientService) {
            this.restClientService = restClientService;
        }

        public void setE2ETrustTokenUtil(E2ETrustTokenUtil e2ETrustTokenUtil) {
            this.e2ETrustTokenUtil = e2ETrustTokenUtil;
        }

        public void setDuplicateSubmitPreventionService(DuplicateSubmitPreventionService duplicateSubmitPreventionService) {
            this.duplicateSubmitPreventionService = duplicateSubmitPreventionService;
        }

         public void setRetrieveCustomerProfilesService(RetrieveCustomerProfilesServiceImpl retrieveCustomerProfilesService) {
            this.retrieveCustomerProfilesService = retrieveCustomerProfilesService;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        abstractRestServiceUnderTest = new TestableAbstractRestService();
        abstractRestServiceUnderTest.setRestClientService(mockRestClientService);
        abstractRestServiceUnderTest.setE2ETrustTokenUtil(mockE2ETrustTokenUtil);
        abstractRestServiceUnderTest.setDuplicateSubmitPreventionService(mockDuplicateSubmitPreventionService);
        abstractRestServiceUnderTest.setRetrieveCustomerProfilesService(mockRetrieveCustomerProfilesService);

        // 设置 URL，特别是 customerAccountUrl，因为 retrieveCustomerAccountIdsList 会用到
        Field customerAccountUrlField = AbstractRestService.class.getDeclaredField("customerAccountUrl");
        customerAccountUrlField.setAccessible(true);
        customerAccountUrlField.set(abstractRestServiceUnderTest, TEST_CUSTOMER_ACCOUNT_URL);

        // 设置其他可能需要的 URL
        abstractRestServiceUnderTest.tradeOnlineUrl = "http://test-trade-online";
        abstractRestServiceUnderTest.accountsMapUrl = "http://test-accounts-map";
        abstractRestServiceUnderTest.cepPartyNameUrl = "http://test-cep-party-name";
        abstractRestServiceUnderTest.cepPartyContactUrl = "http://test-cep-party-contact";
        abstractRestServiceUnderTest.mdsGoldQuotesUrl = "http://test-mds-gold";
    }

    @Test
    void testRetrieveAccountIdWithCheckSum_ResponseOrListNull_ThrowsException() {
        // Arrange
        Map<String, String> requestHeaders = Map.of(
            HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, TEST_CIN,
            TransferQueryParameterConstant.CHECKSUM, TEST_CHECKSUM
        );

        // 创建一个 RetrieveCustomerAccountsIdListResponse 对象，但其 accountIdList 为 null
        RetrieveCustomerAccountsIdListResponse mockResponseWithNullList = new RetrieveCustomerAccountsIdListResponse();
        // 注意：如果 RetrieveCustomerAccountsIdListResponse 没有 setter 且 accountIdList 默认为 null，
        // 那么这个对象的 getAccountIdList() 就会返回 null。
        // 如果有 setter，可以显式设置：mockResponseWithNullList.setAccountIdList(null);

        // Mock restClientService.get 方法，使其返回这个 accountIdList 为 null 的对象
        // 注意：retrieveCustomerAccountIdsList 方法内部构建了 URL，所以这里的 URL 参数需要匹配
        // 它会调用类似 customerAccountUrl + "/accounts-ids?checksumList=[...]" 的 URL
        // 这里的 URL 是 retrieveCustomerAccountIdsList 方法构建的最终 URL，你需要 Mock 这个具体的 URL
        // 为了简单起见，可以使用 anyString() 来匹配任何字符串，或者更精确地构建预期的 URL 模式
        when(mockRestClientService.get(
                any(String.class), // 这里是 retrieveCustomerAccountIdsList 构建的完整 URL
                any(Map.class),
                eq(RetrieveCustomerAccountsIdListResponse.class),
                anyInt(),
                anyBoolean()))
                .thenReturn(mockResponseWithNullList);

        // Act & Assert
        // 调用 retrieveAccountIdWithCheckSum，期望它抛出 BadRequestException
        BadRequestException thrown = assertThrows(BadRequestException.class, () -> {
            abstractRestServiceUnderTest.retrieveAccountIdWithCheckSum(requestHeaders);
        });

        // 验证抛出的异常消息是否为预期的错误码
        assertTrue(thrown.getMessage().contains(ExceptionMessageEnum.ACCOUNT_LIST_EMPTY_ERROR.getCode()));

        // 验证 restClientService.get 被调用了一次
        verify(mockRestClientService, times(1)).get(
                any(String.class),
                any(Map.class),
                eq(RetrieveCustomerAccountsIdListResponse.class),
                anyInt(),
                anyBoolean());
    }
}
