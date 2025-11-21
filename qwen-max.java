import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.hsbc.trade.HTTPRequestHeaderConstants;
import com.hsbc.trade.common.AccountId;
import com.hsbc.trade.service.DuplicateSubmitPreventionService;
import com.hsbc.trade.service.RestClientService;
import com.hsbc.trade.transfer.common.ActionRequestCode;
import com.hsbc.trade.transfer.common.ReceiverInfo;
import com.hsbc.trade.transfer.constant.TransferQueryParameterConstant;
import com.hsbc.trade.transfer.createtransfer.CreateTransferRequest;
import com.hsbc.trade.transfer.createtransfer.CreateTransferResponse;
import com.hsbc.trade.transfer.createtransfer.Data;
import com.hsbc.trade.transfer.domain.RetrieveCustomerAccountsIdListResponse;
import com.hsbc.trade.transfer.domain.cep.PartyNameResponse;
import com.hsbc.trade.transfer.domain.cep.Name;
import com.hsbc.trade.transfer.domain.mds.GoldPriceResponse;
import com.hsbc.trade.transfer.domain.mds.GoldPriceResponseData;
import com.hsbc.trade.transfer.enums.ExceptionMessageEnum;
import com.hsbc.trade.transfer.service.TradeTransferService;
import com.hsbc.trade.transfer.service.impl.SreValidationServiceImpl;
import com.hsbc.trade.transfer.service.impl.TradeLimitServiceImpl;
import com.hsbc.trade.utils.E2ETrustTokenUtil;
import com.hsbc.trade.utils.JacksonUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class TradeTransferServiceImplCreateTransfersTest {

    @Mock
    private RestClientService mockRestClientService;

    @Mock
    private E2ETrustTokenUtil mockE2ETrustTokenUtil;

    @Mock
    private DuplicateSubmitPreventionService mockDuplicateSubmitPreventionService;

    @Mock
    private SreValidationServiceImpl mockSreValidationService; // Mock SreValidationService

    @Mock
    private TradeLimitServiceImpl mockTradeLimitService; // Mock TradeLimitService

    private TradeTransferServiceImpl tradeTransferServiceUnderTest;

    private final String TEST_CIN = "TEST_CIN_12345";
    private final String TEST_CHECKSUM = "TEST_CHECKSUM_67890";
    private final String TEST_CUSTOMER_ACCOUNT_URL = "http://test-customer-account";
    private final String TEST_TRADE_ONLINE_URL = "http://test-trade-online";

    @BeforeEach
    void setUp() throws Exception {
        // 创建被测试的服务实例
        tradeTransferServiceUnderTest = new TradeTransferServiceImpl(
                mockRestClientService, mockE2ETrustTokenUtil, mockDuplicateSubmitPreventionService, mockTradeLimitService);

        // 注入其他 Mock 依赖项 (这些不是通过构造函数注入的)
        Field sreValidationServiceField = TradeTransferServiceImpl.class.getDeclaredField("sreValidationService");
        sreValidationServiceField.setAccessible(true);
        sreValidationServiceField.set(tradeTransferServiceUnderTest, mockSreValidationService);

        // 设置 URL (这些通常通过 @Value 注入)
        Field customerAccountUrlField = TradeTransferServiceImpl.class.getDeclaredField("customerAccountUrl");
        customerAccountUrlField.setAccessible(true);
        customerAccountUrlField.set(tradeTransferServiceUnderTest, TEST_CUSTOMER_ACCOUNT_URL);

        Field tradeOnlineUrlField = TradeTransferServiceImpl.class.getDeclaredField("tradeOnlineUrl");
        tradeOnlineUrlField.setAccessible(true);
        tradeOnlineUrlField.set(tradeTransferServiceUnderTest, TEST_TRADE_ONLINE_URL);

        // 设置其他可能需要的 URL
        Field accountsMapUrlField = TradeTransferServiceImpl.class.getDeclaredField("accountsMapUrl");
        accountsMapUrlField.setAccessible(true);
        accountsMapUrlField.set(tradeTransferServiceUnderTest, "http://test-accounts-map");

        Field cepPartyNameUrlField = TradeTransferServiceImpl.class.getDeclaredField("cepPartyNameUrl");
        cepPartyNameUrlField.setAccessible(true);
        cepPartyNameUrlField.set(tradeTransferServiceUnderTest, "http://test-cep-party-name");

        Field cepPartyContactUrlField = TradeTransferServiceImpl.class.getDeclaredField("cepPartyContactUrl");
        cepPartyContactUrlField.setAccessible(true);
        cepPartyContactUrlField.set(tradeTransferServiceUnderTest, "http://test-cep-party-contact");

        Field mdsGoldQuotesUrlField = TradeTransferServiceImpl.class.getDeclaredField("mdsGoldQuotesUrl");
        mdsGoldQuotesUrlField.setAccessible(true);
        mdsGoldQuotesUrlField.set(tradeTransferServiceUnderTest, "http://test-mds-gold");
    }

    @Test
    void testCreateTransfers_RetrieveAccountIdWithCheckSum_Success() {
        // Arrange
        Map<String, String> inputHeaders = Map.of(
            HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, TEST_CIN
            // ... 其他必要的输入头 ...
        );

        CreateTransferRequest createTransferRequest = new CreateTransferRequest(); // 假设构造函数可用
        Data requestData = new Data(); // 假设构造函数可用
        requestData.setActionRequestCode(ActionRequestCode.D); // 设置为 D 操作，以便触发 retrieveGoldPrice
        requestData.setSenderInvestmentAccountChecksumIdentifier(TEST_CHECKSUM);

        List<ReceiverInfo> receivers = new ArrayList<>();
        ReceiverInfo receiver = new ReceiverInfo(); // 假设构造函数可用
        receiver.setTransferQuantity(new BigDecimal("1.5"));
        receivers.add(receiver);
        requestData.setReceiverLists(receivers);

        // 设置请求数据的其他必要字段...
        createTransferRequest.setData(requestData);

        // --- Mock TradeLimitService 调用 ---
        // 需要先通过 TradeLimitServiceImplTest 来 Mock retrieveLimitations
        // 这里假设 retrieveLimitations 返回一个有效的限制响应
        // mockTradeLimitService.retrieveLimitations(any(Map.class)); // 需要配置返回值

        // --- Mock retrieveCustomerAccountIdsList 的底层调用 ---
        // 创建一个内部 AccountId 对象，代表 checksum 对应的真实账户信息
        AccountId innerAccountId = new AccountId(); // 假设构造函数可用
        innerAccountId.setCountryAccountCode("HK");
        innerAccountId.setGroupMemberAccountCode("HSBC");
        innerAccountId.setAccountNumber("12345678");
        innerAccountId.setAccountProductTypeCode("GOLD");
        innerAccountId.setAccountTypeCode("INVEST");
        innerAccountId.setAccountCurrencyCode("HKD");

        // 创建一个外部 AccountId 对象，其 accountId 字段是上面的 innerAccountId
        AccountId outerAccountId = new AccountId(); // 假设构造函数可用
        // 注意：这里的结构是关键，outerAccountId.getAccountId() 应该返回 innerAccountId
        // 但是 AccountId 类通常不会有 getAccountId() 方法，它自己就是账户信息。
        // 仔细看 AbstractRestService.retrieveAccountIdWithCheckSum 方法：
        // account.getAccountId().getCountryAccountCode()
        // 这意味着 List<AccountId> 中的每个 AccountId 对象本身，其 getAccountId() 方法又返回了另一个 AccountId 对象。
        // 这在代码中看起来像是一个嵌套结构。
        // 重新分析代码：RetrieveCustomerAccountsIdListResponse 包含 List<com.hsbc.trade.common.AccountId>
        // 而 AbstractRestService.retrieveAccountIdWithCheckSum 代码是：
        // account.getAccountId().getCountryAccountCode() ...
        // 这意味着 List<com.hsbc.trade.common.AccountId> 中的 AccountId 对象必须有一个 getAccountId() 方法，
        // 该方法返回 *另一个* AccountId 对象，然后从这个 *另一个* 对象中获取字段。
        // 这似乎不太符合常规的 POJO 设计。让我们再次检查代码片段。

        // **重新分析 AbstractRestService.retrieveAccountIdWithCheckSum:**
        // Optional<AccountId> accountIdOpt = response.getAccountIdList().stream().map(account -> {
        //     if (account == null || account.getAccountId() == null) { // <-- account 是 List<AccountId> 中的一个元素
        //         throw new BadRequestException(INVESTMENT_CHECKSUM_GET_ACCOUNT_ID_NULL_CHECKSUM.getCode());
        //     }
        //     AccountId accountId = new AccountId(); // <-- 创建新的 AccountId 对象
        //     accountId.setCountryAccountCode(account.getAccountId().getCountryAccountCode()); // <-- 从 account.getAccountId() 获取字段
        //     // ... 其他 set 方法 ...
        //     return accountId; // <-- 返回新创建的对象
        // }).findFirst();

        // 这确实表明 List<AccountId> 中的 AccountId 对象必须有一个 getAccountId() 方法。
        // 这意味着 List 中的 AccountId 对象内部持有一个 AccountId 类型的字段。
        // 由于 AccountId 类是我们无法修改的 common 类，我们需要假设它确实有一个 getAccountId() 方法。
        // 让我们创建一个 AccountId 对象，并 Mock 其 getAccountId() 方法。

        AccountId mockOuterAccountId = mock(AccountId.class); // Mock 外层 AccountId
        AccountId innerAccountIdForMock = new AccountId(); // 这个是实际要复制的内部信息
        innerAccountIdForMock.setCountryAccountCode("HK");
        innerAccountIdForMock.setGroupMemberAccountCode("HSBC");
        innerAccountIdForMock.setAccountNumber("12345678");
        innerAccountIdForMock.setAccountProductTypeCode("GOLD");
        innerAccountIdForMock.setAccountTypeCode("INVEST");
        innerAccountIdForMock.setAccountCurrencyCode("HKD");

        // Mock mockOuterAccountId.getAccountId() 返回 innerAccountIdForMock
        when(mockOuterAccountId.getAccountId()).thenReturn(innerAccountIdForMock);

        // 创建 List<AccountId> 并添加 Mock 对象
        List<AccountId> validAccountIdList = List.of(mockOuterAccountId);

        // 创建 RetrieveCustomerAccountsIdListResponse 并 Mock 其 getAccountIdList()
        RetrieveCustomerAccountsIdListResponse mockAccountIdsResponse = new RetrieveCustomerAccountsIdListResponse();
        // Mock response 对象的 getter，使其返回我们准备的列表
        // 这里需要假设 RetrieveCustomerAccountsIdListResponse 有 setter 或者我们可以通过反射设置
        // 或者更直接地，Mock retrieveCustomerAccountIdsList 方法本身（这是更好的方式）
        // 因为 retrieveCustomerAccountIdsList 也是 AbstractRestService 的 public 方法，内部调用 restClientService.get

        // **更好的方式是 Mock retrieveCustomerAccountIdsList 方法本身**
        // 但这意味着我们需要使用 Spy 或者在 Testable 类中暴露方法。
        // 或者，我们可以 Mock restClientService.get，这是 retrieveCustomerAccountIdsList 内部调用的。

        // 构建 retrieveCustomerAccountIdsList 方法内部会调用的 URL
        String expectedAccountIdsUrl = TEST_CUSTOMER_ACCOUNT_URL + "/accounts-ids?body=%7B%22checksumList%22%3A%5B%22TEST_CHECKSUM_67890%22%5D%7D";
        // 注意：URL 中的 body 是 URL 编码的 JSON {"checksumList":["TEST_CHECKSUM_67890"]}

        // Mock restClientService.get 调用 (这是 retrieveCustomerAccountIdsList 内部的调用)
        when(mockRestClientService.get(
                eq(expectedAccountIdsUrl),
                any(Map.class),
                eq(RetrieveCustomerAccountsIdListResponse.class),
                anyInt(),
                anyBoolean()))
                .thenReturn(mockAccountIdsResponse);

        // 现在 Mock mockAccountIdsResponse.getAccountIdList() 返回我们的有效列表
        // 由于 mockAccountIdsResponse 是一个 POJO，我们不能直接 Mock 其 getter，除非我们创建一个子类或使用 PowerMock
        // 更实际的做法是让 POJO 有 setter，或者我们再次使用反射，或者使用 Spy
        // 假设 RetrieveCustomerAccountsIdListResponse 有 setter
        mockAccountIdsResponse.setAccountIdList(validAccountIdList); // 假设存在 setter

        // --- Mock SRE Validation (for receiver validation loop) ---
        // Mock the call and response handling for SRE
        when(mockSreValidationService.callSreForTransferValidation(anyString(), anyString(), any(), any(Map.class)))
                .thenReturn(new com.hsbc.rtp.client.sre.domain.SreResponse()); // 假设 SreResponse 构造函数可用
        // Mock the handleSreValidateResponse method to do nothing (or pass)
        doNothing().when(mockSreValidationService).handleSreValidateResponse(any());

        // --- Mock CEP Party Name Call ---
        PartyNameResponse mockPartyNameResponse = new PartyNameResponse(); // 假设构造函数可用
        Name mockName = new Name(); // 假设构造函数可用
        mockName.setGivenName("John");
        mockName.setCustomerChristianName("Smith");
        mockName.setLastName("Doe");
        mockPartyNameResponse.setName(mockName);
        // Mock updateHeaderforCEP to return a modified map (simplified)
        // Mock the CEP call itself
        when(mockRestClientService.get(anyString(), any(Map.class), eq(PartyNameResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockPartyNameResponse);

        // --- Mock MDS Gold Price Call ---
        GoldPriceResponse mockGoldPriceResponse = new GoldPriceResponse(); // 假设构造函数可用
        GoldPriceResponseData mockGoldPriceData = new GoldPriceResponseData(); // 假设构造函数可用
        mockGoldPriceData.setGoldPriceAmount(new BigDecimal("2000.00"));
        mockGoldPriceData.setPublishTime(LocalDateTime.now());
        mockGoldPriceResponse.setData(mockGoldPriceData);
        when(mockRestClientService.get(anyString(), any(Map.class), eq(GoldPriceResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockGoldPriceResponse);

        // --- Mock Final Post Call ---
        CreateTransferResponse mockCreateResponse = new CreateTransferResponse(); // 假设构造函数可用
        // 设置 mockCreateResponse 的内容...
        when(mockRestClientService.post(anyString(), any(Map.class), any(), eq(CreateTransferResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockCreateResponse);

        // Act
        CreateTransferResponse result = tradeTransferServiceUnderTest.createTransfers(inputHeaders, createTransferRequest);

        // Assert
        assertNotNull(result);
        // 验证 retrieveCustomerAccountIdsList 的内部调用 (间接验证 retrieveAccountIdWithCheckSum 成功执行)
        verify(mockRestClientService, times(1)).get(eq(expectedAccountIdsUrl), any(Map.class), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean());
        // 验证最终的 post 调用
        verify(mockRestClientService, times(1)).post(anyString(), any(Map.class), any(), eq(CreateTransferResponse.class), anyInt(), anyBoolean());
        // 验证 SRE 调用
        verify(mockSreValidationService, times(1)).callSreForTransferValidation(anyString(), anyString(), any(), any(Map.class));
        verify(mockSreValidationService, times(1)).handleSreValidateResponse(any());
        // 验证 CEP 调用
        verify(mockRestClientService, times(1)).get(anyString(), any(Map.class), eq(PartyNameResponse.class), anyInt(), anyBoolean());
        // 验证 MDS 调用
        verify(mockRestClientService, times(1)).get(anyString(), any(Map.class), eq(GoldPriceResponse.class), anyInt(), anyBoolean());
    }
}
