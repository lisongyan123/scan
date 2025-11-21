package com.hsbc.trade.transfer.service.impl;

import com.hsbc.trade.HTTPRequestHeaderConstants;
import com.hsbc.trade.common.AccountId;
import com.hsbc.trade.service.DuplicateSubmitPreventionService;
import com.hsbc.trade.service.RestClientService;
import com.hsbc.trade.transfer.constant.TransferQueryParameterConstant;
import com.hsbc.trade.transfer.createtransfer.CreateTransferRequest;
import com.hsbc.trade.transfer.createtransfer.CreateTransferResponse;
import com.hsbc.trade.transfer.domain.RetrieveCustomerAccountsIdListResponse;
import com.hsbc.trade.transfer.domain.account.CustomerAccounts;
import com.hsbc.trade.transfer.domain.account.InvestmentAccount;
import com.hsbc.trade.transfer.domain.cep.PartyContactResponse;
import com.hsbc.trade.transfer.domain.cep.PartyNameResponse;
import com.hsbc.trade.transfer.domain.mds.GoldPriceResponse;
import com.hsbc.trade.transfer.domain.mds.GoldPriceResponseData;
import com.hsbc.trade.transfer.retrievetransferdetail.RetrieveTransferDetailResponse;
import com.hsbc.trade.transfer.retrievetransferlist.RetrieveTransferListResponse;
import com.hsbc.trade.transfer.updatetransfer.UpdateTransferRequest;
import com.hsbc.trade.transfer.updatetransfer.UpdateTransferResponse;
import com.hsbc.trade.utils.E2ETrustTokenUtil;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.net.URI;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeTransferServiceImplTest {

    @Mock
    private RestClientService mockRestClientService;

    @Mock
    private E2ETrustTokenUtil mockE2ETrustTokenUtil;

    @Mock
    private DuplicateSubmitPreventionService mockDuplicateSubmitPreventionService;

    @Mock
    private TradeLimitServiceImpl mockTradeLimitService;

    @Mock
    private SreValidationServiceImpl mockSreValidationService;

    private TradeTransferServiceImpl tradeTransferService;

    private Map<String, String> baseRequestHeaders;

    @BeforeEach
    void setUp() {
        tradeTransferService = new TradeTransferServiceImpl(mockRestClientService, mockE2ETrustTokenUtil, mockDuplicateSubmitPreventionService, mockTradeLimitService);
        tradeTransferService.sreValidationService = mockSreValidationService; // Inject mock via setter if needed

        baseRequestHeaders = new HashMap<>();
        baseRequestHeaders.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "testCIN123");
        baseRequestHeaders.put(HTTPRequestHeaderConstants.X_HSBC_USER_ID, "testUser");
    }

    // Test for retrieveTransferList
    @Test
    void testRetrieveTransferList_Success() {
        // Arrange
        String transferStatusCode = "ACTIVE";
        List<String> checksumIdentifiers = Arrays.asList("chk1", "chk2");
        String pagination = "{}";
        String productId = "GOLD";
        String sParameterType = "SENS";

        RetrieveTransferListResponse mockResponse = new RetrieveTransferListResponse();
        when(mockRestClientService.get(anyString(), anyMap(), eq(RetrieveTransferListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockResponse);

        // Act
        RetrieveTransferListResponse result = tradeTransferService.retrieveTransferList(
                baseRequestHeaders, transferStatusCode, checksumIdentifiers, pagination, productId, sParameterType);

        // Assert
        assertNotNull(result);
        verify(mockRestClientService, times(1)).get(anyString(), anyMap(), eq(RetrieveTransferListResponse.class), anyInt(), anyBoolean());
    }

    // Test for retrieveTransferDetail
    @Test
    void testRetrieveTransferDetail_Success() {
        // Arrange
        String transferReferenceNumber = "ref123";
        RetrieveTransferDetailResponse mockResponse = new RetrieveTransferDetailResponse();

        when(mockRestClientService.get(anyString(), anyMap(), eq(RetrieveTransferDetailResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockResponse);

        // Act
        RetrieveTransferDetailResponse result = tradeTransferService.retrieveTransferDetail(baseRequestHeaders, transferReferenceNumber);

        // Assert
        assertNotNull(result);
        verify(mockRestClientService, times(1)).get(anyString(), anyMap(), eq(RetrieveTransferDetailResponse.class), anyInt(), anyBoolean());
    }

    // Test for createTransfers - Basic Success Path
    @Test
    void testCreateTransfers_Success() {
        // Arrange
        CreateTransferRequest request = new CreateTransferRequest(); // Assume data is set correctly
        // ... Set up request data (senderInvestmentAccountChecksumIdentifier, receiverLists, etc.) ...
        // For example:
        // CreateTransferRequest.Data requestData = new CreateTransferRequest.Data();
        // requestData.setSenderInvestmentAccountChecksumIdentifier("chk123");
        // List<ReceiverInfo> receivers = new ArrayList<>();
        // ReceiverInfo receiver = new ReceiverInfo();
        // receiver.setTransferQuantity(new BigDecimal("1.0"));
        // receivers.add(receiver);
        // requestData.setReceiverLists(receivers);
        // request.setData(requestData);

        AccountId mockAccountId = new AccountId();
        // ... set fields on mockAccountId ...
        RetrieveCustomerAccountsIdListResponse mockAccountListResponse = new RetrieveCustomerAccountsIdListResponse();
        // ... set fields on mockAccountListResponse with mockAccountId ...

        GoldPriceResponse mockGoldPriceResponse = new GoldPriceResponse();
        GoldPriceResponseData mockGoldData = new GoldPriceResponseData();
        mockGoldData.setGoldPriceAmount(new BigDecimal("2000")); // Example price
        mockGoldPriceResponse.setData(mockGoldData);

        CreateTransferResponse mockCreateResponse = new CreateTransferResponse();
        // ... set fields on mockCreateResponse ...

        when(mockTradeLimitService.retrieveLimitations(anyMap())).thenReturn(null); // Assume limits are OK for this test
        when(mockRestClientService.get(anyString(), anyMap(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockAccountListResponse);
        when(mockRestClientService.get(anyString(), anyMap(), eq(GoldPriceResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockGoldPriceResponse);
        when(mockRestClientService.post(anyString(), anyMap(), eq(request), eq(CreateTransferResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockCreateResponse);
        when(mockE2ETrustTokenUtil.getE2ETrustToken()).thenReturn("mockToken");
        // Mock SRE validation response
        // RuleResponse mockSreResponse = new RuleResponse(); // Assume success
        // when(mockSreValidationService.callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap())).thenReturn(mockSreResponse);

        // Act
        CreateTransferResponse result = tradeTransferService.createTransfers(baseRequestHeaders, request);

        // Assert
        assertNotNull(result);
        // Verify interactions
        verify(mockRestClientService, times(1)).get(anyString(), anyMap(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean());
        verify(mockRestClientService, times(1)).get(anyString(), anyMap(), eq(GoldPriceResponse.class), anyInt(), anyBoolean());
        verify(mockRestClientService, times(1)).post(anyString(), anyMap(), eq(request), eq(CreateTransferResponse.class), anyInt(), anyBoolean());
        // verify(mockSreValidationService, times(1)).callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap());
    }

    // Test for createTransfers - Exception during REST call
    @Test
    void testCreateTransfers_RestClientException() {
        // Arrange
        CreateTransferRequest request = new CreateTransferRequest();
        // ... Set up request data ...

        when(mockTradeLimitService.retrieveLimitations(anyMap())).thenReturn(null); // Assume limits are OK
        // ... Mock other necessary calls before the POST ...
        when(mockRestClientService.post(anyString(), anyMap(), eq(request), eq(CreateTransferResponse.class), anyInt(), anyBoolean()))
                .thenThrow(new RuntimeException("Network Error"));

        // Act & Assert
        assertThrows(InternalServerErrorException.class, () -> {
            tradeTransferService.createTransfers(baseRequestHeaders, request);
        });
        verify(mockRestClientService, times(1)).post(anyString(), anyMap(), eq(request), eq(CreateTransferResponse.class), anyInt(), anyBoolean());
    }

    // Test for modifyTransfers - Basic Success Path for ACCEPT (A)
    @Test
    void testModifyTransfers_AcceptSuccess() {
        // Arrange
        UpdateTransferRequest request = new UpdateTransferRequest(); // Assume data is set for ACCEPT action
        // ... Set up request data (transferActionCode = A, receiverInvestmentAccountChecksumIdentifier, etc.) ...
        // UpdateTransferRequest.Data reqData = new UpdateTransferRequest.Data();
        // reqData.setTransferActionCode(TransferActionCode.A);
        // reqData.setReceiverInvestmentAccountChecksumIdentifier("chk456");
        // request.setData(reqData);

        AccountId mockAccountId = new AccountId();
        // ... set fields ...
        RetrieveCustomerAccountsIdListResponse mockAccountListResponse = new RetrieveCustomerAccountsIdListResponse();
        // ... set fields with mockAccountId ...

        GoldPriceResponse mockGoldPriceResponse = new GoldPriceResponse();
        GoldPriceResponseData mockGoldData = new GoldPriceResponseData();
        mockGoldData.setGoldPriceAmount(new BigDecimal("2000"));
        mockGoldPriceResponse.setData(mockGoldData);

        UpdateTransferResponse mockUpdateResponse = new UpdateTransferResponse();
        // ... set fields ...

        when(mockRestClientService.get(anyString(), anyMap(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockAccountListResponse);
        when(mockRestClientService.get(anyString(), anyMap(), eq(GoldPriceResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockGoldPriceResponse);
        when(mockRestClientService.put(anyString(), anyMap(), eq(request), eq(UpdateTransferResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockUpdateResponse);

        // Act
        UpdateTransferResponse result = tradeTransferService.modifyTransfers(baseRequestHeaders, request);

        // Assert
        assertNotNull(result);
        verify(mockRestClientService, times(1)).get(anyString(), anyMap(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean());
        verify(mockRestClientService, times(1)).get(anyString(), anyMap(), eq(GoldPriceResponse.class), anyInt(), anyBoolean());
        verify(mockRestClientService, times(1)).put(anyString(), anyMap(), eq(request), eq(UpdateTransferResponse.class), anyInt(), anyBoolean());
    }

    // Test for modifyTransfers - Basic Success Path for REJECT (R) - No account lookup needed
    @Test
    void testModifyTransfers_RejectSuccess() {
        // Arrange
        UpdateTransferRequest request = new UpdateTransferRequest(); // Assume data is set for REJECT action
        // ... Set up request data (transferActionCode = R) ...
        // UpdateTransferRequest.Data reqData = new UpdateTransferRequest.Data();
        // reqData.setTransferActionCode(TransferActionCode.R);
        // request.setData(reqData);

        UpdateTransferResponse mockUpdateResponse = new UpdateTransferResponse();
        // ... set fields ...

        when(mockRestClientService.put(anyString(), anyMap(), eq(request), eq(UpdateTransferResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockUpdateResponse);

        // Act
        UpdateTransferResponse result = tradeTransferService.modifyTransfers(baseRequestHeaders, request);

        // Assert
        assertNotNull(result);
        // Verify only PUT is called, not GET for account or gold price
        verify(mockRestClientService, times(1)).put(anyString(), anyMap(), eq(request), eq(UpdateTransferResponse.class), anyInt(), anyBoolean());
        verify(mockRestClientService, never()).get(anyString(), anyMap(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean());
        verify(mockRestClientService, never()).get(anyString(), anyMap(), eq(GoldPriceResponse.class), anyInt(), anyBoolean());
    }

    // Test for extractAccountIdMap
    @Test
    void testExtractAccountIdMap_ValidInput() {
        // Arrange
        CustomerAccounts customerAccounts = new CustomerAccounts();
        List<InvestmentAccount> accountList = new ArrayList<>();
        InvestmentAccount acc1 = new InvestmentAccount();
        acc1.setChecksum("chk1");
        AccountId id1 = new AccountId();
        id1.setCountryAccountCode("HK");
        id1.setAccountNumber("12345");
        // ... set other fields on id1 ...
        acc1.setInvestmentAccountId(id1);

        InvestmentAccount acc2 = new InvestmentAccount();
        acc2.setChecksum("chk2");
        AccountId id2 = new AccountId();
        id2.setCountryAccountCode("US");
        id2.setAccountNumber("67890");
        // ... set other fields on id2 ...
        acc2.setInvestmentAccountId(id2);

        accountList.add(acc1);
        accountList.add(acc2);
        customerAccounts.setInvestmentAccountList(accountList);

        // Act
        Map<String, String> result = tradeTransferService.extractAccountIdMap(customerAccounts);

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.containsKey("chk1"));
        assertTrue(result.containsKey("chk2"));
        // Verify the format of the value string (basic check)
        String value1 = result.get("chk1");
        assertTrue(value1.contains("HK") && value1.contains("12345"));
        String value2 = result.get("chk2");
        assertTrue(value2.contains("US") && value2.contains("67890"));
    }

    @Test
    void testExtractAccountIdMap_EmptyInput() {
        // Arrange
        CustomerAccounts customerAccounts = new CustomerAccounts();
        customerAccounts.setInvestmentAccountList(null); // Or empty list

        // Act
        Map<String, String> result = tradeTransferService.extractAccountIdMap(customerAccounts);

        // Assert
        assertTrue(result.isEmpty());
    }

    // Test for retrieveAccountIdWithCheckSum - Success
    @Test
    void testRetrieveAccountIdWithCheckSum_Success() {
        // Arrange
        String checksum = "testChecksum";
        Map<String, String> headersWithChecksum = new HashMap<>(baseRequestHeaders);
        headersWithChecksum.put(TransferQueryParameterConstant.CHECKSUM, checksum);

        RetrieveCustomerAccountsIdListResponse mockResponse = new RetrieveCustomerAccountsIdListResponse();
        List<com.hsbc.trade.transfer.domain.account.AccountId> idList = new ArrayList<>();
        com.hsbc.trade.transfer.domain.account.AccountId rawId = new com.hsbc.trade.transfer.domain.account.AccountId();
        // ... set fields on rawId ...
        rawId.setAccountNumber("99999");
        idList.add(rawId);
        mockResponse.setAccountIdList(idList);

        when(mockRestClientService.get(anyString(), anyMap(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockResponse);

        // Act
        AccountId result = tradeTransferService.retrieveAccountIdWithCheckSum(headersWithChecksum);

        // Assert
        assertNotNull(result);
        assertEquals("99999", result.getAccountNumber()); // Or check other fields
        verify(mockRestClientService, times(1)).get(anyString(), anyMap(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean());
    }

    // Test for retrieveAccountIdWithCheckSum - Null List
    @Test
    void testRetrieveAccountIdWithCheckSum_NullList_ThrowsException() {
        // Arrange
        Map<String, String> headersWithChecksum = new HashMap<>(baseRequestHeaders);
        headersWithChecksum.put(TransferQueryParameterConstant.CHECKSUM, "dummy");

        RetrieveCustomerAccountsIdListResponse mockResponse = new RetrieveCustomerAccountsIdListResponse();
        mockResponse.setAccountIdList(null); // This should trigger the exception

        when(mockRestClientService.get(anyString(), anyMap(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockResponse);

        // Act & Assert
        assertThrows(BadRequestException.class, () -> {
            tradeTransferService.retrieveAccountIdWithCheckSum(headersWithChecksum);
        });
        verify(mockRestClientService, times(1)).get(anyString(), anyMap(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean());
    }

    // Test for buildRequestHeaders from AbstractRestService (assuming it's accessible or tested via inheritance)
    // This test is based on the logic in AbstractRestService
    @Test
    void testBuildRequestHeaders_BasicMapping() {
        // Arrange
        Map<String, String> sourceHeaders = new HashMap<>();
        sourceHeaders.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CIN123");
        sourceHeaders.put(HTTPRequestHeaderConstants.X_HSBC_USER_ID, "USER456");
        sourceHeaders.put("SomeOtherHeader", "value");

        // Act
        Map<String, String> builtHeaders = tradeTransferService.buildRequestHeaders(sourceHeaders); // Assuming method is accessible or mocked setup for RetrieveCustomerProfilesService handles CIN

        // Assert
        assertEquals("application/json", builtHeaders.get("Content-Type"));
        assertEquals("CIN123", builtHeaders.get(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID));
        assertEquals("N", builtHeaders.get(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID_TYPE));
        assertEquals("USER456", builtHeaders.get(HTTPRequestHeaderConstants.X_HSBC_USER_ID));
        // Note: Other headers like SAML, SAML3, etc., are added conditionally based on sourceHeaders presence.
        // This test checks basic mapping and defaults.
    }

    // Test for retrieveCustomerAccountIdsList
    @Test
    void testRetrieveCustomerAccountIdsList_Success() {
        // Arrange
        String checksum = "testChecksum";
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put(TransferQueryParameterConstant.CHECKSUM, checksum);

        RetrieveCustomerAccountsIdListResponse mockResponse = new RetrieveCustomerAccountsIdListResponse();
        // ... set fields ...

        when(mockRestClientService.get(anyString(), anyMap(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockResponse);

        // Act
        RetrieveCustomerAccountsIdListResponse result = tradeTransferService.retrieveCustomerAccountIdsList(requestHeaders);

        // Assert
        assertNotNull(result);
        verify(mockRestClientService, times(1)).get(anyString(), anyMap(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean());
    }

    // Test for updateHeaderforCEP
    @Test
    void testUpdateHeaderforCEP() {
        // Arrange
        String cepToken = "mockE2ETrustToken";
        String gbGF = "GBGFValue";
        String sourceSystemId = "SourceSysId";
        tradeTransferService.cepHeaderGBGF = gbGF;
        tradeTransferService.cepHeaderSourceSystemId = sourceSystemId;

        Map<String, String> sourceHeaders = new HashMap<>();
        sourceHeaders.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CIN123");
        sourceHeaders.put(HTTPRequestHeaderConstants.X_HSBC_SAML, "samlValue"); // Should be removed
        sourceHeaders.put(HTTPRequestHeaderConstants.X_HSBC_USER_ID, "USER456");

        when(mockE2ETrustTokenUtil.getE2ETrustToken()).thenReturn(cepToken);

        // Act
        Map<String, String> updatedHeaders = tradeTransferService.updateHeaderforCEP(sourceHeaders);

        // Assert
        // Check SAML headers are removed
        assertFalse(updatedHeaders.containsKey(HTTPRequestHeaderConstants.X_HSBC_SAML));
        assertFalse(updatedHeaders.containsKey(HTTPRequestHeaderConstants.X_HSBC_SAML3));
        // Check E2E token is added
        assertEquals(cepToken, updatedHeaders.get(HTTPRequestHeaderConstants.X_HSBC_E2E_TRUST_TOKEN));
        // Check CEP headers are added
        assertEquals(gbGF, updatedHeaders.get(HTTPRequestHeaderConstants.X_HSBC_GBGF));
        assertEquals(sourceSystemId, updatedHeaders.get(HTTPRequestHeaderConstants.X_HSBC_SOURCE_SYSTEM_ID));
        // Check other headers are preserved
        assertEquals("CIN123", updatedHeaders.get(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID));
        assertEquals("USER456", updatedHeaders.get(HTTPRequestHeaderConstants.X_HSBC_USER_ID));
    }

    // Test for maskNamesInResponse (List version)
    @Test
    void testMaskNamesInResponse_List() {
        // This test requires the actual response data classes to be mockable or instantiable easily.
        // For simplicity, we'll assume a basic structure or use reflection if fields are public/setter is available.
        // Let's assume we have a mockable response object or use a helper to create one.
        // The logic involves reflection to access firstName and middleName fields.
        // A full test would involve creating a RetrieveTransferListResponseData object,
        // adding TransferListItemInfo objects with names, calling the method, and asserting the names are masked.
        // Given the complexity of the actual domain objects, a simpler check for method execution is shown here.
        // A more thorough test would require the full domain model in the test classpath or mocking strategies for the nested objects.
        // For now, just verify the method doesn't throw an exception with null input.
        assertDoesNotThrow(() -> tradeTransferService.maskNamesInResponse(null));
        // A real test would create a non-null object and verify the masking logic.
    }

    // Test for maskNamesInResponse (Detail version)
    @Test
    void testMaskNamesInResponse_Detail() {
        assertDoesNotThrow(() -> tradeTransferService.maskNamesInResponse(null));
        // Similar to list version, a real test would involve creating a non-null object and verifying masking.
    }

    // Test for validateTransferLimits - Within Limits
    @Test
    void testValidateTransferLimits_WithinLimits() {
        // Arrange
        BigDecimal totalAmount = new BigDecimal("500");
        // Mock RetrieveTransferLimitResponse with limits higher than totalAmount
        // RetrieveTransferLimitResponse limitResponse = new RetrieveTransferLimitResponse();
        // RetrieveTransferLimitResponse.Data limitData = new RetrieveTransferLimitResponse.Data();
        // limitData.setAvailableTodayAmount(new BigDecimal("1000"));
        // limitData.setAvailableMonthToDateAmount(new BigDecimal("2000"));
        // limitData.setAvailableYearToDateAmount(new BigDecimal("5000"));
        // limitResponse.setData(limitData);

        // Act & Assert (Should not throw)
        assertDoesNotThrow(() -> tradeTransferService.validateTransferLimits(totalAmount, null)); // Pass actual limitResponse object in real scenario
    }

    // Test for validateTransferLimits - Exceeds Daily Limit
    @Test
    void testValidateTransferLimits_ExceedsDailyLimit() {
        // Arrange
        BigDecimal totalAmount = new BigDecimal("1500");
        // Mock RetrieveTransferLimitResponse with daily limit lower than totalAmount
        // RetrieveTransferLimitResponse limitResponse = new RetrieveTransferLimitResponse();
        // RetrieveTransferLimitResponse.Data limitData = new RetrieveTransferLimitResponse.Data();
        // limitData.setAvailableTodayAmount(new BigDecimal("1000"));
        // limitData.setMaxDailyLimitedAmount(new BigDecimal("1000"));
        // limitResponse.setData(limitData);

        // Act & Assert
        assertThrows(com.hsbc.trade.transfer.exception.TransferLimitExceededException.class, () ->
                tradeTransferService.validateTransferLimits(totalAmount, null) // Pass actual limitResponse object in real scenario
        );
    }

    // Test for findAccountChecksumForAccountNumber
    @Test
    void testFindAccountChecksumForAccountNumber_Found() {
        // Arrange
        CustomerAccounts customerAccounts = new CustomerAccounts();
        List<InvestmentAccount> list = new ArrayList<>();
        InvestmentAccount acc1 = new InvestmentAccount();
        acc1.setChecksum("chk1");
        AccountId id1 = new AccountId();
        id1.setAccountNumber("12345");
        acc1.setInvestmentAccountId(id1);

        InvestmentAccount acc2 = new InvestmentAccount();
        acc2.setChecksum("chk2");
        AccountId id2 = new AccountId();
        id2.setAccountNumber("67890");
        acc2.setInvestmentAccountId(id2);

        list.add(acc1);
        list.add(acc2);
        customerAccounts.setInvestmentAccountList(list);

        // Act
        String result = tradeTransferService.findAccountChecksumForAccountNumber(customerAccounts, "67890");

        // Assert
        assertEquals("chk2", result);
    }

    @Test
    void testFindAccountChecksumForAccountNumber_NotFound() {
        // Arrange
        CustomerAccounts customerAccounts = new CustomerAccounts();
        List<InvestmentAccount> list = new ArrayList<>();
        InvestmentAccount acc1 = new InvestmentAccount();
        acc1.setChecksum("chk1");
        AccountId id1 = new AccountId();
        id1.setAccountNumber("12345");
        acc1.setInvestmentAccountId(id1);
        list.add(acc1);
        customerAccounts.setInvestmentAccountList(list);

        // Act
        String result = tradeTransferService.findAccountChecksumForAccountNumber(customerAccounts, "99999");

        // Assert
        assertNull(result);
    }
}
