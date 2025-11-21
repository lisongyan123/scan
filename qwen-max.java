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
import com.hsbc.trade.transfer.domain.account.CustomerAccounts;
import com.hsbc.trade.transfer.domain.account.InvestmentAccount;
import com.hsbc.trade.transfer.domain.mds.GoldPriceResponse;
import com.hsbc.trade.transfer.domain.mds.GoldPriceResponseData;
import com.hsbc.trade.transfer.domain.cep.PartyContactResponse;
import com.hsbc.trade.transfer.domain.cep.PartyNameResponse;
import com.hsbc.trade.transfer.domain.cep.Name;
import com.hsbc.trade.transfer.domain.cep.Contact;
import com.hsbc.trade.utils.E2ETrustTokenUtil;
import com.hsbc.trade.service.DuplicateSubmitPreventionService;
import com.hsbc.trade.service.impl.RetrieveCustomerProfilesServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

// 假设这个类是 AbstractRestService 的具体实现以便测试，或者使用反射/匿名类实例化
// 这里我们假设有一个 TestableAbstractRestService 类来实例化和测试
// 如果没有具体实现，可以考虑使用 PowerMock 或反射来处理 final/method 依赖注入问题
// 为了演示，这里创建一个内部类来测试抽象方法
@ExtendWith(MockitoExtension.class)
class AbstractRestServiceTest {

    @Mock
    private RestClientService mockRestClientService;

    @Mock
    private E2ETrustTokenUtil mockE2ETrustTokenUtil;

    @Mock
    private DuplicateSubmitPreventionService mockDuplicateSubmitPreventionService;

    @Mock
    private RetrieveCustomerProfilesServiceImpl mockRetrieveCustomerProfilesService;

    private TestableAbstractRestService abstractRestServiceUnderTest; // 需要一个具体实现或测试辅助类

    private final String TEST_CIN = "TEST_CIN_12345";
    private final String TEST_CHECKSUM = "TEST_CHECKSUM_67890";

    // 一个用于测试的内部类，继承 AbstractRestService
    static class TestableAbstractRestService extends AbstractRestService {
        // 在测试类中设置依赖
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

        // 简单构造函数，或者通过 setter 注入
        public TestableAbstractRestService() {
             // 设置默认值，这些值会在测试中被 Mock 覆盖
        }
    }

    @BeforeEach
    void setUp() {
        abstractRestServiceUnderTest = new TestableAbstractRestService();
        abstractRestServiceUnderTest.setRestClientService(mockRestClientService);
        abstractRestServiceUnderTest.setE2ETrustTokenUtil(mockE2ETrustTokenUtil);
        abstractRestServiceUnderTest.setDuplicateSubmitPreventionService(mockDuplicateSubmitPreventionService);
        abstractRestServiceUnderTest.setRetrieveCustomerProfilesService(mockRetrieveCustomerProfilesService);

        // 设置一些默认 URL 值，防止空指针
        abstractRestServiceUnderTest.tradeOnlineUrl = "http://test-trade-online";
        abstractRestServiceUnderTest.accountsMapUrl = "http://test-accounts-map";
        abstractRestServiceUnderTest.cepPartyNameUrl = "http://test-cep-party-name";
        abstractRestServiceUnderTest.cepPartyContactUrl = "http://test-cep-party-contact";
        abstractRestServiceUnderTest.mdsGoldQuotesUrl = "http://test-mds-gold";
        abstractRestServiceUnderTest.customerAccountUrl = "http://test-customer-account";
    }

    @Test
    void testRetrieveCustomerAccounts_Success() {
        // Arrange
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, TEST_CIN);

        CustomerAccounts mockResponse = new CustomerAccounts(); // 假设构造函数或 setter 可用
        List<InvestmentAccount> accountList = new ArrayList<>();
        // 添加一些模拟账户数据...
        InvestmentAccount mockAccount = new InvestmentAccount();
        // ... 设置 mockAccount 属性 ...
        accountList.add(mockAccount);
        mockResponse.setInvestmentAccountList(accountList);

        when(mockRestClientService.get(any(String.class), any(Map.class), eq(CustomerAccounts.class), anyInt(), anyBoolean()))
                .thenReturn(mockResponse);

        // Act
        CustomerAccounts result = abstractRestServiceUnderTest.retrieveCustomerAccounts(requestHeaders);

        // Assert
        assertNotNull(result);
        assertEquals(accountList, result.getInvestmentAccountList());
        verify(mockRestClientService, times(1)).get(any(String.class), any(Map.class), eq(CustomerAccounts.class), anyInt(), anyBoolean());
    }

    @Test
    void testRetrieveCustomerNamesWithCinNumber_Success() {
        // Arrange
        String cinNumber = TEST_CIN;
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, cinNumber);

        Name mockName = new Name(); // 假设 Name 类有 setter
        mockName.setGivenName("John");
        mockName.setLastName("Doe");
        PartyNameResponse mockResponse = new PartyNameResponse();
        mockResponse.setName(mockName);

        when(mockRestClientService.get(any(String.class), any(Map.class), eq(PartyNameResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockResponse);

        // Act
        PartyNameResponse result = abstractRestServiceUnderTest.retrieveCustomerNamesWithCinNumber(cinNumber, requestHeaders);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getName());
        assertEquals("John", result.getName().getGivenName());
        assertEquals("Doe", result.getName().getLastName());
        verify(mockRestClientService, times(1)).get(any(String.class), any(Map.class), eq(PartyNameResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void testRetrieveCustomerPhoneNumberWithCinNumber_Success() {
        // Arrange
        String cinNumber = TEST_CIN;
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, cinNumber);

        Contact mockContact = new Contact(); // 假设 Contact 类有 setter
        mockContact.setMobileNumber1("123456789");
        PartyContactResponse mockResponse = new PartyContactResponse();
        mockResponse.setContact(mockContact);

        when(mockRestClientService.get(any(String.class), any(Map.class), eq(PartyContactResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockResponse);

        // Act
        PartyContactResponse result = abstractRestServiceUnderTest.retrieveCustomerPhoneNumberWithCinNumber(cinNumber, requestHeaders);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getContact());
        assertEquals("123456789", result.getContact().getMobileNumber1());
        verify(mockRestClientService, times(1)).get(any(String.class), any(Map.class), eq(PartyContactResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void testRetrieveGoldPrice_Success() {
        // Arrange
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, TEST_CIN);

        GoldPriceResponseData mockData = new GoldPriceResponseData(); // 假设 setter 可用
        mockData.setGoldPriceAmount(new BigDecimal("2000.00"));
        mockData.setPublishTime(LocalDateTime.now());
        GoldPriceResponse mockResponse = new GoldPriceResponse();
        mockResponse.setData(mockData);

        when(mockRestClientService.get(any(String.class), any(Map.class), eq(GoldPriceResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockResponse);

        // Act
        GoldPriceResponse result = abstractRestServiceUnderTest.retrieveGoldPrice(requestHeaders);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getData());
        assertEquals(new BigDecimal("2000.00"), result.getData().getGoldPriceAmount());
        verify(mockRestClientService, times(1)).get(any(String.class), any(Map.class), eq(GoldPriceResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void testRetrieveCustomerAccountIdsList_Success() {
         // Arrange
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, TEST_CIN);
        requestHeaders.put(TransferQueryParameterConstant.CHECKSUM, TEST_CHECKSUM);

        RetrieveCustomerAccountsIdListResponse mockResponse = new RetrieveCustomerAccountsIdListResponse(); // 假设构造或 setter
        List<com.hsbc.trade.transfer.domain.account.AccountId> accountIdList = new ArrayList<>();
        com.hsbc.trade.transfer.domain.account.AccountId mockAccountId = new com.hsbc.trade.transfer.domain.account.AccountId();
        // ... 设置 mockAccountId 属性 ...
        accountIdList.add(mockAccountId);
        mockResponse.setAccountIdList(accountIdList);

        when(mockRestClientService.get(any(String.class), any(Map.class), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockResponse);

        // Act
        RetrieveCustomerAccountsIdListResponse result = abstractRestServiceUnderTest.retrieveCustomerAccountIdsList(requestHeaders);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getAccountIdList());
        assertEquals(accountIdList, result.getAccountIdList());
        verify(mockRestClientService, times(1)).get(any(String.class), any(Map.class), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void testRetrieveAccountIdWithCheckSum_Success() {
        // Arrange
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, TEST_CIN);
        requestHeaders.put(TransferQueryParameterConstant.CHECKSUM, TEST_CHECKSUM);

        // Mock the call to retrieveCustomerAccountIdsList
        RetrieveCustomerAccountsIdListResponse mockListResponse = new RetrieveCustomerAccountsIdListResponse();
        List<com.hsbc.trade.transfer.domain.account.AccountId> accountIdList = new ArrayList<>();
        com.hsbc.trade.transfer.domain.account.AccountId mockSourceAccountId = new com.hsbc.trade.transfer.domain.account.AccountId();
        // ... 设置 mockSourceAccountId 属性 ...
        mockSourceAccountId.setAccountNumber("12345678"); // Example
        accountIdList.add(mockSourceAccountId);
        mockListResponse.setAccountIdList(accountIdList);

        when(mockRestClientService.get(any(String.class), any(Map.class), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockListResponse);

        // Act
        AccountId result = abstractRestServiceUnderTest.retrieveAccountIdWithCheckSum(requestHeaders);

        // Assert
        assertNotNull(result);
        assertEquals("12345678", result.getAccountNumber()); // Example assertion based on mock data
        verify(mockRestClientService, times(1)).get(any(String.class), any(Map.class), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void testRetrieveAccountIdWithCheckSum_EmptyList_ThrowsException() {
        // Arrange
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, TEST_CIN);
        requestHeaders.put(TransferQueryParameterConstant.CHECKSUM, TEST_CHECKSUM);

        RetrieveCustomerAccountsIdListResponse mockListResponse = new RetrieveCustomerAccountsIdListResponse();
        // accountIdList is null or empty
        mockListResponse.setAccountIdList(null); // or Collections.emptyList()

        when(mockRestClientService.get(any(String.class), any(Map.class), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockListResponse);

        // Act & Assert
        assertThrows(BadRequestException.class, () -> {
            abstractRestServiceUnderTest.retrieveAccountIdWithCheckSum(requestHeaders);
        });
        verify(mockRestClientService, times(1)).get(any(String.class), any(Map.class), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void testRetrieveCustomerNamesWithCinNumber_ServiceError_ThrowsException() {
        // Arrange
        String cinNumber = TEST_CIN;
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, cinNumber);

        when(mockRestClientService.get(any(String.class), any(Map.class), eq(PartyNameResponse.class), anyInt(), anyBoolean()))
                .thenThrow(new RuntimeException("Service Error"));

        // Act & Assert
        assertThrows(InternalServerErrorException.class, () -> {
            abstractRestServiceUnderTest.retrieveCustomerNamesWithCinNumber(cinNumber, requestHeaders);
        });
        verify(mockRestClientService, times(1)).get(any(String.class), any(Map.class), eq(PartyNameResponse.class), anyInt(), anyBoolean());
    }

    // 类似地，可以为 retrieveCustomerPhoneNumberWithCinNumber 和 retrieveGoldPrice 添加错误测试用例
}
