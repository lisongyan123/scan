package com.hsbc.trade.transfer.service.impl;

import com.hsbc.trade.HTTPRequestHeaderConstants;
import com.hsbc.trade.service.DuplicateSubmitPreventionService;
import com.hsbc.trade.service.RestClientService;
import com.hsbc.trade.transfer.common.ReceiverInfo;
import com.hsbc.trade.transfer.constant.TransferQueryParameterConstant;
import com.hsbc.trade.transfer.createtransfer.CreateTransferRequest;
import com.hsbc.trade.transfer.createtransfer.CreateTransferResponse;
import com.hsbc.trade.transfer.domain.account.CustomerAccounts;
import com.hsbc.trade.transfer.domain.account.InvestmentAccount;
import com.hsbc.trade.transfer.domain.cep.PartyContactResponse;
import com.hsbc.trade.transfer.domain.cep.PartyNameResponse;
import com.hsbc.trade.transfer.domain.mds.GoldPriceResponse;
import com.hsbc.trade.transfer.domain.mds.GoldPriceResponseData;
import com.hsbc.trade.transfer.exception.TransferLimitExceededException;
import com.hsbc.trade.transfer.retrievetransferdetail.RetrieveTransferDetailResponse;
import com.hsbc.trade.transfer.retrievetransferdetail.RetrieveTransferDetailResponseData;
import com.hsbc.trade.transfer.retrievetransferlimit.RetrieveTransferLimitResponse;
import com.hsbc.trade.transfer.retrievetransferlist.RetrieveTransferListResponse;
import com.hsbc.trade.transfer.retrievetransferlist.RetrieveTransferListResponseData;
import com.hsbc.trade.transfer.updatetransfer.UpdateTransferRequest;
import com.hsbc.trade.transfer.updatetransfer.UpdateTransferResponse;
import com.hsbc.trade.utils.E2ETrustTokenUtil;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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

    private Map<String, String> headers;
    private final String CUSTOMER_CIN = "CUST123";
    private final String ACCOUNT_CHECKSUM = "CHK123";
    private final String TRANSFER_REF = "REF456";
    private final String TRADE_ONLINE_URL = "https://trade-online.example.com";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        headers = new HashMap<>();
        headers.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, CUSTOMER_CIN);

        // 使用 ReflectionTestUtils 设置 TradeTransferServiceImpl 的依赖和属性
        ReflectionTestUtils.setField(tradeTransferService, "restClientService", restClientService);
        ReflectionTestUtils.setField(tradeTransferService, "e2ETrustTokenUtil", e2ETrustTokenUtil);
        ReflectionTestUtils.setField(tradeTransferService, "duplicateSubmitPreventionService", duplicateSubmitPreventionService);
        ReflectionTestUtils.setField(tradeTransferService, "tradeLimitService", tradeLimitService);
        ReflectionTestUtils.setField(tradeTransferService, "sreValidationService", sreValidationService);

        // 设置 TradeTransferServiceImpl 的配置属性
        ReflectionTestUtils.setField(tradeTransferService, "tradeOnlineUrl", TRADE_ONLINE_URL);
        ReflectionTestUtils.setField(tradeTransferService, "timeout", 5000);
        ReflectionTestUtils.setField(tradeTransferService, "printMessageLog", false);

        lenient().when(e2ETrustTokenUtil.getE2ETrustToken()).thenReturn("dummy-e2e-token");
    }

    @Test
    void testRetrieveTransferList_success() {
        RetrieveTransferListResponse mockResponse = new RetrieveTransferListResponse();
        RetrieveTransferListResponseData mockData = new RetrieveTransferListResponseData();
        mockResponse.setData(mockData);
        lenient().when(restClientService.get(anyString(), anyMap(), eq(RetrieveTransferListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockResponse);

        // Mock customer accounts
        CustomerAccounts mockAccounts = new CustomerAccounts();
        InvestmentAccount mockAccount = new InvestmentAccount();
        mockAccount.setChecksum(ACCOUNT_CHECKSUM);
        // Mock InvestmentAccountId fields if needed
        // mockAccount.setInvestmentAccountId(...);
        mockAccounts.setInvestmentAccountList(Collections.singletonList(mockAccount));
        lenient().when(tradeTransferService.retrieveCustomerAccounts(anyMap())).thenReturn(mockAccounts);

        // Mock customer name and contact
        PartyNameResponse mockName = new PartyNameResponse();
        lenient().when(tradeTransferService.retrieveCustomerNamesWithCinNumber(anyString(), anyMap())).thenReturn(mockName);
        PartyContactResponse mockContact = new PartyContactResponse();
        lenient().when(tradeTransferService.retrieveCustomerPhoneNumberWithCinNumber(anyString(), anyMap())).thenReturn(mockContact);

        RetrieveTransferListResponse response = tradeTransferService.retrieveTransferList(
                headers, "PENDING", Collections.singletonList(ACCOUNT_CHECKSUM), "{}", "PROD1", "SENS");

        assertThat(response).isNotNull();
        assertThat(response.getData()).isEqualTo(mockData);
        // Verify internal calls
        verify(tradeTransferService).retrieveCustomerAccounts(anyMap());
        verify(tradeTransferService).retrieveCustomerNamesWithCinNumber(eq(CUSTOMER_CIN), anyMap());
        verify(tradeTransferService).retrieveCustomerPhoneNumberWithCinNumber(eq(CUSTOMER_CIN), anyMap());
        verify(tradeTransferService).maskNamesInResponse(any(RetrieveTransferListResponseData.class));
    }

    @Test
    void testRetrieveTransferDetail_success() {
        RetrieveTransferDetailResponse mockResponse = new RetrieveTransferDetailResponse();
        RetrieveTransferDetailResponseData mockData = new RetrieveTransferDetailResponseData();
        mockData.setInvestmentAccount(new com.hsbc.trade.transfer.domain.cep.InvestmentAccount());
        mockData.getInvestmentAccount().setAccountNumber("ACC987");
        mockResponse.setData(mockData);
        lenient().when(restClientService.get(anyString(), anyMap(), eq(RetrieveTransferDetailResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockResponse);

        // Mock customer accounts
        CustomerAccounts mockAccounts = new CustomerAccounts();
        InvestmentAccount mockAccount = new InvestmentAccount();
        mockAccount.setChecksum(ACCOUNT_CHECKSUM);
        mockAccount.setInvestmentAccountId(new com.hsbc.trade.common.AccountId());
        mockAccount.getInvestmentAccountId().setAccountNumber("ACC987");
        mockAccounts.setInvestmentAccountList(Collections.singletonList(mockAccount));
        lenient().when(tradeTransferService.retrieveCustomerAccounts(anyMap())).thenReturn(mockAccounts);

        // Mock customer name and contact
        PartyNameResponse mockName = new PartyNameResponse();
        lenient().when(tradeTransferService.retrieveCustomerNamesWithCinNumber(anyString(), anyMap())).thenReturn(mockName);
        PartyContactResponse mockContact = new PartyContactResponse();
        lenient().when(tradeTransferService.retrieveCustomerPhoneNumberWithCinNumber(anyString(), anyMap())).thenReturn(mockContact);

        RetrieveTransferDetailResponse response = tradeTransferService.retrieveTransferDetail(headers, TRANSFER_REF);

        assertThat(response).isNotNull();
        assertThat(response.getData()).isEqualTo(mockData);
        assertThat(response.getData().getAccountChecksumIdentifier()).isEqualTo(ACCOUNT_CHECKSUM);
        // Verify internal calls
        verify(tradeTransferService).retrieveCustomerAccounts(anyMap());
        verify(tradeTransferService).retrieveCustomerNamesWithCinNumber(eq(CUSTOMER_CIN), anyMap());
        verify(tradeTransferService).retrieveCustomerPhoneNumberWithCinNumber(eq(CUSTOMER_CIN), anyMap());
        verify(tradeTransferService).maskNamesInResponse(any(RetrieveTransferDetailResponseData.class));
        verify(tradeTransferService).findAccountChecksumForAccountNumber(any(), eq("ACC987"));
    }

    @Test
    void testRetrieveTransferDetail_accountNotFound_throwsBadRequest() {
        RetrieveTransferDetailResponse mockResponse = new RetrieveTransferDetailResponse();
        RetrieveTransferDetailResponseData mockData = new RetrieveTransferDetailResponseData();
        mockData.setInvestmentAccount(new com.hsbc.trade.transfer.domain.cep.InvestmentAccount());
        mockData.getInvestmentAccount().setAccountNumber("ACC987");
        mockResponse.setData(mockData);
        lenient().when(restClientService.get(anyString(), anyMap(), eq(RetrieveTransferDetailResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockResponse);

        // Mock customer accounts with a different account number
        CustomerAccounts mockAccounts = new CustomerAccounts();
        InvestmentAccount mockAccount = new InvestmentAccount();
        mockAccount.setChecksum(ACCOUNT_CHECKSUM);
        mockAccount.setInvestmentAccountId(new com.hsbc.trade.common.AccountId());
        mockAccount.getInvestmentAccountId().setAccountNumber("ACC_DIFFERENT");
        mockAccounts.setInvestmentAccountList(Collections.singletonList(mockAccount));
        lenient().when(tradeTransferService.retrieveCustomerAccounts(anyMap())).thenReturn(mockAccounts);

        // Mock customer name and contact
        PartyNameResponse mockName = new PartyNameResponse();
        lenient().when(tradeTransferService.retrieveCustomerNamesWithCinNumber(anyString(), anyMap())).thenReturn(mockName);
        PartyContactResponse mockContact = new PartyContactResponse();
        lenient().when(tradeTransferService.retrieveCustomerPhoneNumberWithCinNumber(anyString(), anyMap())).thenReturn(mockContact);

        assertThrows(BadRequestException.class, () -> tradeTransferService.retrieveTransferDetail(headers, TRANSFER_REF));
    }

    @Test
    void testCreateTransfers_success() {
        CreateTransferRequest request = new CreateTransferRequest();
        // Assuming CreateTransferRequest has a getData() method returning a mutable object
        // You need to populate the request object according to your data structure
        // For example:
        // CreateTransferRequestData requestData = new CreateTransferRequestData();
        // ReceiverInfo receiver = new ReceiverInfo();
        // receiver.setTransferQuantity(BigDecimal.ONE);
        // requestData.setReceiverLists(Collections.singletonList(receiver));
        // requestData.setRequestPriceValue(BigDecimal.TEN);
        // requestData.setSenderInvestmentAccountChecksumIdentifier(ACCOUNT_CHECKSUM);
        // request.setData(requestData);

        CreateTransferResponse mockResponse = new CreateTransferResponse();
        lenient().when(restClientService.post(anyString(), anyMap(), eq(request), eq(CreateTransferResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockResponse);

        RetrieveTransferLimitResponse mockLimitResponse = new RetrieveTransferLimitResponse();
        // Mock limit data to pass validation
        lenient().when(tradeLimitService.retrieveLimitations(anyMap())).thenReturn(mockLimitResponse);

        // Mock SRE validation
        lenient().doNothing().when(sreValidationService).handleSreValidateResponse(any());

        // Mock account ID retrieval
        lenient().when(tradeTransferService.retrieveAccountIdWithCheckSum(anyMap())).thenReturn(new com.hsbc.trade.common.AccountId());

        // Mock customer name retrieval
        PartyNameResponse mockName = new PartyNameResponse();
        lenient().when(tradeTransferService.retrieveCustomerNamesWithCinNumber(anyString(), anyMap())).thenReturn(mockName);

        // Mock gold price retrieval
        GoldPriceResponse mockGoldPrice = new GoldPriceResponse();
        GoldPriceResponseData mockGoldPriceData = new GoldPriceResponseData();
        mockGoldPriceData.setGoldPriceAmount(BigDecimal.valueOf(2000));
        mockGoldPriceData.setPublishTime("2023-10-27T10:00:00Z");
        mockGoldPrice.setData(mockGoldPriceData);
        lenient().when(tradeTransferService.retrieveGoldPrice(anyMap())).thenReturn(mockGoldPrice);

        CreateTransferResponse response = tradeTransferService.createTransfers(headers, request);

        assertThat(response).isNotNull();
        assertThat(response).isEqualTo(mockResponse);
        // Verify internal calls
        verify(tradeLimitService).retrieveLimitations(anyMap());
        verify(tradeTransferService).retrieveAccountIdWithCheckSum(anyMap());
        verify(sreValidationService).handleSreValidateResponse(any());
        verify(tradeTransferService).retrieveCustomerNamesWithCinNumber(eq(CUSTOMER_CIN), anyMap());
        verify(tradeTransferService).retrieveGoldPrice(anyMap());
        verify(restClientService).post(anyString(), anyMap(), eq(request), eq(CreateTransferResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void testCreateTransfers_limitExceeded_throwsException() {
        CreateTransferRequest request = new CreateTransferRequest();
        // Populate request as needed

        RetrieveTransferLimitResponse mockLimitResponse = new RetrieveTransferLimitResponse();
        // Mock limit data to fail validation
        lenient().when(tradeLimitService.retrieveLimitations(anyMap())).thenReturn(mockLimitResponse);
        // You need to mock the response data inside mockLimitResponse to make totalTranAmount > availableAmount
        // For example, if totalTranAmount is 100, mock available amounts to be less than 100

        assertThrows(TransferLimitExceededException.class, () -> tradeTransferService.createTransfers(headers, request));
    }

    @Test
    void testModifyTransfers_success() {
        UpdateTransferRequest request = new UpdateTransferRequest();
        // Assuming UpdateTransferRequest has a getData() method returning a mutable object
        // For example:
        // UpdateTransferRequestData requestData = new UpdateTransferRequestData();
        // requestData.setTransferActionCode(com.hsbc.trade.transfer.common.TransferActionCode.C); // or another code that doesn't trigger special logic
        // request.setData(requestData);

        UpdateTransferResponse mockResponse = new UpdateTransferResponse();
        lenient().when(restClientService.put(anyString(), anyMap(), eq(request), eq(UpdateTransferResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockResponse);

        UpdateTransferResponse response = tradeTransferService.modifyTransfers(headers, request);

        assertThat(response).isNotNull();
        assertThat(response).isEqualTo(mockResponse);
        verify(restClientService).put(anyString(), anyMap(), eq(request), eq(UpdateTransferResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void testModifyTransfers_AcceptAction_success() {
        UpdateTransferRequest request = new UpdateTransferRequest();
        // Assuming UpdateTransferRequest has a getData() method returning a mutable object
        // For example:
        // UpdateTransferRequestData requestData = new UpdateTransferRequestData();
        // requestData.setTransferActionCode(com.hsbc.trade.transfer.common.TransferActionCode.A); // ACCEPT
        // requestData.setReceiverInvestmentAccountChecksumIdentifier(ACCOUNT_CHECKSUM);
        // requestData.setReceiverCustomerInternalNumber("RECEIVER_CIN");
        // request.setData(requestData);

        UpdateTransferResponse mockResponse = new UpdateTransferResponse();
        lenient().when(restClientService.put(anyString(), anyMap(), eq(request), eq(UpdateTransferResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockResponse);

        // Mock SRE validation for ACCEPT
        lenient().doNothing().when(sreValidationService).handleSreValidateResponse(any());

        // Mock account ID retrieval for ACCEPT
        lenient().when(tradeTransferService.retrieveAccountIdWithCheckSum(anyMap())).thenReturn(new com.hsbc.trade.common.AccountId());

        // Mock gold price retrieval for ACCEPT
        GoldPriceResponse mockGoldPrice = new GoldPriceResponse();
        GoldPriceResponseData mockGoldPriceData = new GoldPriceResponseData();
        mockGoldPriceData.setGoldPriceAmount(BigDecimal.valueOf(2000));
        mockGoldPriceData.setPublishTime("2023-10-27T10:00:00Z");
        mockGoldPrice.setData(mockGoldPriceData);
        lenient().when(tradeTransferService.retrieveGoldPrice(anyMap())).thenReturn(mockGoldPrice);

        UpdateTransferResponse response = tradeTransferService.modifyTransfers(headers, request);

        assertThat(response).isNotNull();
        assertThat(response).isEqualTo(mockResponse);
        // Verify calls specific to ACCEPT action
        verify(sreValidationService).handleSreValidateResponse(any());
        verify(tradeTransferService).retrieveAccountIdWithCheckSum(anyMap());
        verify(tradeTransferService).retrieveGoldPrice(anyMap());
        verify(restClientService).put(anyString(), anyMap(), eq(request), eq(UpdateTransferResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void testModifyTransfers_RejectAction_success() {
        UpdateTransferRequest request = new UpdateTransferRequest();
        // Assuming UpdateTransferRequest has a getData() method returning a mutable object
        // For example:
        // UpdateTransferRequestData requestData = new UpdateTransferRequestData();
        // requestData.setTransferActionCode(com.hsbc.trade.transfer.common.TransferActionCode.R); // REJECT
        // request.setData(requestData);

        UpdateTransferResponse mockResponse = new UpdateTransferResponse();
        lenient().when(restClientService.put(anyString(), anyMap(), eq(request), eq(UpdateTransferResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockResponse);

        UpdateTransferResponse response = tradeTransferService.modifyTransfers(headers, request);

        assertThat(response).isNotNull();
        assertThat(response).isEqualTo(mockResponse);
        // Verify call to restClientService.put, receiver CIN might be set
        verify(restClientService).put(anyString(), anyMap(), eq(request), eq(UpdateTransferResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void testExtractAccountIdMap_emptyAccounts_returnsEmptyMap() {
        CustomerAccounts accounts = new CustomerAccounts();
        accounts.setInvestmentAccountList(null); // or an empty list

        Map<String, String> result = tradeTransferService.extractAccountIdMap(accounts);

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void testExtractAccountIdMap_validAccounts_returnsMap() {
        CustomerAccounts accounts = new CustomerAccounts();
        InvestmentAccount account1 = new InvestmentAccount();
        account1.setChecksum("CHK1");
        com.hsbc.trade.common.AccountId id1 = new com.hsbc.trade.common.AccountId();
        id1.setCountryAccountCode("HK");
        id1.setGroupMemberAccountCode("004");
        id1.setAccountNumber("12345678");
        id1.setAccountProductTypeCode("GOLD");
        id1.setAccountTypeCode("INVEST");
        id1.setAccountCurrencyCode("HKD");
        account1.setInvestmentAccountId(id1);

        InvestmentAccount account2 = new InvestmentAccount();
        account2.setChecksum("CHK2");
        com.hsbc.trade.common.AccountId id2 = new com.hsbc.trade.common.AccountId();
        id2.setCountryAccountCode("US");
        id2.setGroupMemberAccountCode("001");
        id2.setAccountNumber("87654321");
        id2.setAccountProductTypeCode("SILVER");
        id2.setAccountTypeCode("INVEST");
        id2.setAccountCurrencyCode("USD");
        account2.setInvestmentAccountId(id2);

        accounts.setInvestmentAccountList(Arrays.asList(account1, account2));

        Map<String, String> result = tradeTransferService.extractAccountIdMap(accounts);

        assertThat(result).hasSize(2);
        assertThat(result).containsEntry("CHK1", "countryAccountCode=HK;groupMemberAccountCode=004;accountNumber=12345678;accountProductTypeCode=GOLD;accountTypeCode=INVEST;accountCurrencyCode=HKD");
        assertThat(result).containsEntry("CHK2", "countryAccountCode=US;groupMemberAccountCode=001;accountNumber=87654321;accountProductTypeCode=SILVER;accountTypeCode=INVEST;accountCurrencyCode=USD");
    }

    @Test
    void testFindAccountChecksumForAccountNumber_found_returnsChecksum() {
        CustomerAccounts accounts = new CustomerAccounts();
        InvestmentAccount account1 = new InvestmentAccount();
        account1.setChecksum(ACCOUNT_CHECKSUM);
        com.hsbc.trade.common.AccountId id1 = new com.hsbc.trade.common.AccountId();
        id1.setAccountNumber("TARGET_ACC");
        account1.setInvestmentAccountId(id1);

        InvestmentAccount account2 = new InvestmentAccount();
        account2.setChecksum("CHK2");
        com.hsbc.trade.common.AccountId id2 = new com.hsbc.trade.common.AccountId();
        id2.setAccountNumber("OTHER_ACC");
        account2.setInvestmentAccountId(id2);

        accounts.setInvestmentAccountList(Arrays.asList(account1, account2));

        String result = tradeTransferService.findAccountChecksumForAccountNumber(accounts, "TARGET_ACC");

        assertThat(result).isEqualTo(ACCOUNT_CHECKSUM);
    }

    @Test
    void testFindAccountChecksumForAccountNumber_notFound_returnsNull() {
        CustomerAccounts accounts = new CustomerAccounts();
        InvestmentAccount account1 = new InvestmentAccount();
        account1.setChecksum(ACCOUNT_CHECKSUM);
        com.hsbc.trade.common.AccountId id1 = new com.hsbc.trade.common.AccountId();
        id1.setAccountNumber("OTHER_ACC");
        account1.setInvestmentAccountId(id1);

        accounts.setInvestmentAccountList(Collections.singletonList(account1));

        String result = tradeTransferService.findAccountChecksumForAccountNumber(accounts, "TARGET_ACC");

        assertThat(result).isNull();
    }

    @Test
    void testValidateTransferLimits_exceedsDaily_throwsException() {
        RetrieveTransferLimitResponse limitResponse = new RetrieveTransferLimitResponse();
        // Mock data: available < total
        lenient().when(limitResponse.getData().getAvailableTodayAmount()).thenReturn(BigDecimal.valueOf(50));
        lenient().when(limitResponse.getData().getMaxDailyLimitedAmount()).thenReturn(BigDecimal.valueOf(100));

        assertThrows(TransferLimitExceededException.class, () -> tradeTransferService.validateTransferLimits(BigDecimal.valueOf(100), limitResponse));
    }

    @Test
    void testValidateTransferLimits_exceedsMonthly_throwsException() {
        RetrieveTransferLimitResponse limitResponse = new RetrieveTransferLimitResponse();
        // Mock data: available < total, daily passes
        lenient().when(limitResponse.getData().getAvailableTodayAmount()).thenReturn(BigDecimal.valueOf(200));
        lenient().when(limitResponse.getData().getMaxDailyLimitedAmount()).thenReturn(BigDecimal.valueOf(200));
        lenient().when(limitResponse.getData().getAvailableMonthToDateAmount()).thenReturn(BigDecimal.valueOf(50));
        lenient().when(limitResponse.getData().getMaxMonthlyLimitedAmount()).thenReturn(BigDecimal.valueOf(100));

        assertThrows(TransferLimitExceededException.class, () -> tradeTransferService.validateTransferLimits(BigDecimal.valueOf(100), limitResponse));
    }

    @Test
    void testValidateTransferLimits_exceedsYearly_throwsException() {
        RetrieveTransferLimitResponse limitResponse = new RetrieveTransferLimitResponse();
        // Mock data: available < total, daily and monthly pass
        lenient().when(limitResponse.getData().getAvailableTodayAmount()).thenReturn(BigDecimal.valueOf(200));
        lenient().when(limitResponse.getData().getMaxDailyLimitedAmount()).thenReturn(BigDecimal.valueOf(200));
        lenient().when(limitResponse.getData().getAvailableMonthToDateAmount()).thenReturn(BigDecimal.valueOf(200));
        lenient().when(limitResponse.getData().getMaxMonthlyLimitedAmount()).thenReturn(BigDecimal.valueOf(200));
        lenient().when(limitResponse.getData().getAvailableYearToDateAmount()).thenReturn(BigDecimal.valueOf(50));
        lenient().when(limitResponse.getData().getMaxYearlyLimitedAmount()).thenReturn(BigDecimal.valueOf(100));

        assertThrows(TransferLimitExceededException.class, () -> tradeTransferService.validateTransferLimits(BigDecimal.valueOf(100), limitResponse));
    }

    @Test
    void testValidateTransferLimits_withinLimits_noException() {
        RetrieveTransferLimitResponse limitResponse = new RetrieveTransferLimitResponse();
        // Mock data: available > total for all limits
        lenient().when(limitResponse.getData().getAvailableTodayAmount()).thenReturn(BigDecimal.valueOf(200));
        lenient().when(limitResponse.getData().getMaxDailyLimitedAmount()).thenReturn(BigDecimal.valueOf(200));
        lenient().when(limitResponse.getData().getAvailableMonthToDateAmount()).thenReturn(BigDecimal.valueOf(200));
        lenient().when(limitResponse.getData().getMaxMonthlyLimitedAmount()).thenReturn(BigDecimal.valueOf(200));
        lenient().when(limitResponse.getData().getAvailableYearToDateAmount()).thenReturn(BigDecimal.valueOf(200));
        lenient().when(limitResponse.getData().getMaxYearlyLimitedAmount()).thenReturn(BigDecimal.valueOf(200));

        // Should not throw
        tradeTransferService.validateTransferLimits(BigDecimal.valueOf(100), limitResponse);
    }

    // Note: Masking methods (maskNamesInResponse, maskFirstNameAndMiddleName) are complex due to reflection.
    // It's often better to test them indirectly through the methods that call them (retrieveTransferList, retrieveTransferDetail)
    // or refactor the masking logic into a separate, more testable class/service.
    // For now, we'll focus on the other public methods.
}
