import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.hsbc.trade.HTTPRequestHeaderConstants;
import com.hsbc.trade.common.AccountId;
import com.hsbc.trade.service.RestClientService;
import com.hsbc.trade.transfer.constant.TransferQueryParameterConstant;
import com.hsbc.trade.transfer.domain.RetrieveCustomerAccountsIdListResponse;
import com.hsbc.trade.transfer.domain.account.AccountIdList;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class AbstractRestServiceRetrieveAccountIdWithCheckSumBranchesTest {

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

        // 设置 URL
        Field customerAccountUrlField = AbstractRestService.class.getDeclaredField("customerAccountUrl");
        customerAccountUrlField.setAccessible(true);
        customerAccountUrlField.set(abstractRestServiceUnderTest, TEST_CUSTOMER_ACCOUNT_URL);

        abstractRestServiceUnderTest.tradeOnlineUrl = "http://test-trade-online";
        abstractRestServiceUnderTest.accountsMapUrl = "http://test-accounts-map";
        abstractRestServiceUnderTest.cepPartyNameUrl = "http://test-cep-party-name";
        abstractRestServiceUnderTest.cepPartyContactUrl = "http://test-cep-party-contact";
        abstractRestServiceUnderTest.mdsGoldQuotesUrl = "http://test-mds-gold";
    }

    @Test
    void testRetrieveAccountIdWithCheckSum_ListHasNullElement_ThrowsException() {
        // Arrange
        Map<String, String> requestHeaders = Map.of(
            HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, TEST_CIN,
            TransferQueryParameterConstant.CHECKSUM, TEST_CHECKSUM
        );

        // 创建一个包含 null 元素的列表
        List<AccountIdList> accountIdListWithNull = new ArrayList<>();
        AccountIdList validAccount = new AccountIdList(); // 假设构造函数可用
        // 设置 validAccount 的 accountId 属性...
        AccountId innerAccountId = new AccountId(); // 假设构造函数可用
        // 设置 innerAccountId 的属性...
        validAccount.setAccountId(innerAccountId);
        accountIdListWithNull.add(validAccount);
        accountIdListWithNull.add(null); // 添加 null 元素

        RetrieveCustomerAccountsIdListResponse mockResponse = new RetrieveCustomerAccountsIdListResponse();
        mockResponse.setAccountIdList(accountIdListWithNull);

        when(mockRestClientService.get(
                any(String.class),
                any(Map.class),
                eq(RetrieveCustomerAccountsIdListResponse.class),
                anyInt(),
                anyBoolean()))
                .thenReturn(mockResponse);

        // Act & Assert
        BadRequestException thrown = assertThrows(BadRequestException.class, () -> {
            abstractRestServiceUnderTest.retrieveAccountIdWithCheckSum(requestHeaders);
        });

        // 验证抛出的异常消息是否为预期的错误码 (对应 account == null 的情况)
        // 注意：根据代码， account == null 和 account.getAccountId() == null 抛出的是同一个错误码
        assertTrue(thrown.getMessage().contains(ExceptionMessageEnum.INVESTMENT_CHECKSUM_GET_ACCOUNT_ID_NULL_CHECKSUM.getCode()));

        verify(mockRestClientService, times(1)).get(
                any(String.class),
                any(Map.class),
                eq(RetrieveCustomerAccountsIdListResponse.class),
                anyInt(),
                anyBoolean());
    }

    @Test
    void testRetrieveAccountIdWithCheckSum_ListElementHasNullAccountId_ThrowsException() {
        // Arrange
        Map<String, String> requestHeaders = Map.of(
            HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, TEST_CIN,
            TransferQueryParameterConstant.CHECKSUM, TEST_CHECKSUM
        );

        // 创建一个包含 AccountIdList 对象，但其 accountId 为 null 的列表
        List<AccountIdList> accountIdListWithNullAccountId = new ArrayList<>();
        AccountIdList accountWithNullId = new AccountIdList(); // 假设构造函数可用
        accountWithNullId.setAccountId(null); // 关键：设置 accountId 为 null
        accountIdListWithNullAccountId.add(accountWithNullId);

        RetrieveCustomerAccountsIdListResponse mockResponse = new RetrieveCustomerAccountsIdListResponse();
        mockResponse.setAccountIdList(accountIdListWithNullAccountId);

        when(mockRestClientService.get(
                any(String.class),
                any(Map.class),
                eq(RetrieveCustomerAccountsIdListResponse.class),
                anyInt(),
                anyBoolean()))
                .thenReturn(mockResponse);

        // Act & Assert
        BadRequestException thrown = assertThrows(BadRequestException.class, () -> {
            abstractRestServiceUnderTest.retrieveAccountIdWithCheckSum(requestHeaders);
        });

        // 验证抛出的异常消息是否为预期的错误码 (对应 account.getAccountId() == null 的情况)
        assertTrue(thrown.getMessage().contains(ExceptionMessageEnum.INVESTMENT_CHECKSUM_GET_ACCOUNT_ID_NULL_CHECKSUM.getCode()));

        verify(mockRestClientService, times(1)).get(
                any(String.class),
                any(Map.class),
                eq(RetrieveCustomerAccountsIdListResponse.class),
                anyInt(),
                anyBoolean());
    }

    @Test
    void testRetrieveAccountIdWithCheckSum_ListEmpty_ThrowsException() {
        // Arrange
        Map<String, String> requestHeaders = Map.of(
            HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, TEST_CIN,
            TransferQueryParameterConstant.CHECKSUM, TEST_CHECKSUM
        );

        // 创建一个空列表
        List<AccountIdList> emptyAccountIdList = new ArrayList<>(); // 空列表

        RetrieveCustomerAccountsIdListResponse mockResponse = new RetrieveCustomerAccountsIdListResponse();
        mockResponse.setAccountIdList(emptyAccountIdList);

        when(mockRestClientService.get(
                any(String.class),
                any(Map.class),
                eq(RetrieveCustomerAccountsIdListResponse.class),
                anyInt(),
                anyBoolean()))
                .thenReturn(mockResponse);

        // Act & Assert
        BadRequestException thrown = assertThrows(BadRequestException.class, () -> {
            abstractRestServiceUnderTest.retrieveAccountIdWithCheckSum(requestHeaders);
        });

        // 验证抛出的异常消息是否为预期的错误码 (对应 findFirst().orElseThrow 的情况)
        assertTrue(thrown.getMessage().contains(ExceptionMessageEnum.INVESTMENT_CHECKSUM_GET_ACCOUNT_ID_NULL_CHECKSUM.getCode()));

        verify(mockRestClientService, times(1)).get(
                any(String.class),
                any(Map.class),
                eq(RetrieveCustomerAccountsIdListResponse.class),
                anyInt(),
                anyBoolean());
    }

    @Test
    void testRetrieveAccountIdWithCheckSum_Success() {
        // Arrange
        Map<String, String> requestHeaders = Map.of(
            HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, TEST_CIN,
            TransferQueryParameterConstant.CHECKSUM, TEST_CHECKSUM
        );

        // 创建一个包含有效 AccountIdList 对象的列表
        List<AccountIdList> validAccountIdList = new ArrayList<>();
        AccountIdList validAccount = new AccountIdList(); // 假设构造函数可用
        AccountId innerAccountId = new AccountId(); // 假设构造函数可用
        // 设置 innerAccountId 的具体属性...
        innerAccountId.setAccountNumber("12345678");
        innerAccountId.setCountryAccountCode("HK");
        // ... 设置其他必要属性 ...
        validAccount.setAccountId(innerAccountId);
        validAccountIdList.add(validAccount);

        RetrieveCustomerAccountsIdListResponse mockResponse = new RetrieveCustomerAccountsIdListResponse();
        mockResponse.setAccountIdList(validAccountIdList);

        when(mockRestClientService.get(
                any(String.class),
                any(Map.class),
                eq(RetrieveCustomerAccountsIdListResponse.class),
                anyInt(),
                anyBoolean()))
                .thenReturn(mockResponse);

        // Act
        AccountId result = abstractRestServiceUnderTest.retrieveAccountIdWithCheckSum(requestHeaders);

        // Assert
        assertNotNull(result);
        assertEquals("12345678", result.getAccountNumber()); // 假设 AccountId 有 getAccountNumber
        assertEquals("HK", result.getCountryAccountCode()); // 假设 AccountId 有 getCountryAccountCode

        verify(mockRestClientService, times(1)).get(
                any(String.class),
                any(Map.class),
                eq(RetrieveCustomerAccountsIdListResponse.class),
                anyInt(),
                anyBoolean());
    }
}
