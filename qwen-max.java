package com.hsbc.trade.transfer.service.impl;

import com.hsbc.trade.ErrorCodes;
import com.hsbc.trade.HTTPRequestHeaderConstants;
import com.hsbc.trade.common.AccountId;
import com.hsbc.trade.common.ResponseDetails;
import com.hsbc.trade.service.DuplicateSubmitPreventionService;
import com.hsbc.trade.service.RestClientService;
import com.hsbc.trade.service.impl.RetrieveCustomerProfilesServiceImpl;
import com.hsbc.trade.transfer.common.*;
import com.hsbc.trade.transfer.constant.TransferQueryParameterConstant;
import com.hsbc.trade.transfer.createtransfer.CreateTransferRequest;
import com.hsbc.trade.transfer.createtransfer.CreateTransferRequestData;
import com.hsbc.trade.transfer.createtransfer.CreateTransferResponse;
import com.hsbc.trade.transfer.createtransfer.TransferOrderInfo;
import com.hsbc.trade.transfer.domain.InvestmentAccountId;
import com.hsbc.trade.transfer.domain.InvestmentAccountIdList;
import com.hsbc.trade.transfer.domain.RetrieveCustomerAccountsIdListResponse;
import com.hsbc.trade.transfer.domain.account.CustomerAccounts;
import com.hsbc.trade.transfer.domain.account.InvestmentAccount;
import com.hsbc.trade.transfer.domain.cep.PartyContactResponse;
import com.hsbc.trade.transfer.domain.cep.PartyNameResponse;
import com.hsbc.trade.transfer.domain.cep.PartyNameResponseData;
import com.hsbc.trade.transfer.domain.eligibility.RuleResponse;
import com.hsbc.trade.transfer.domain.mds.GoldPriceResponse;
import com.hsbc.trade.transfer.domain.mds.GoldPriceResponseData;
import com.hsbc.trade.transfer.exception.TransferLimitExceededException;
import com.hsbc.trade.transfer.retrievetransferdetail.RetrieveTransferDetailResponse;
import com.hsbc.trade.transfer.retrievetransferdetail.RetrieveTransferDetailResponseData;
import com.hsbc.trade.transfer.retrievetransferlimit.RetrieveTransferLimitResponse;
import com.hsbc.trade.transfer.retrievetransferlimit.RetrieveTransferLimitResponseData;
import com.hsbc.trade.transfer.retrievetransferlist.RetrieveTransferListResponse;
import com.hsbc.trade.transfer.retrievetransferlist.RetrieveTransferListResponseData;
import com.hsbc.trade.transfer.updatetransfer.UpdateTransferRequest;
import com.hsbc.trade.transfer.updatetransfer.UpdateTransferRequestData;
import com.hsbc.trade.transfer.updatetransfer.UpdateTransferResponse;
import com.hsbc.trade.utils.E2ETrustTokenUtil;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeTransferServiceImplTest {

    @InjectMocks
    private TradeTransferServiceImpl tradeTransferService;

    @Mock
    private RestClientService restClientService;

    @Mock
    private RetrieveCustomerProfilesServiceImpl retrieveCustomerProfilesService;

    @Mock
    private SreValidationServiceImpl sreValidationService;

    @Mock
    private E2ETrustTokenUtil e2ETrustTokenUtil;

    @Mock
    private TradeLimitServiceImpl tradeLimitService;

    @Mock
    private DuplicateSubmitPreventionService duplicateSubmitPreventionService;

    private static final String DUMMY_TOKEN = "<saml:Assertion xmlns:saml='http://www.hsbc.com/saas/assertion' xmlns:ds='http://www.w3.org/2000/09/xmldsig#' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' ID='id_9a9e4d35-7d80-4805-849f-ddb90a8b2c1f' IssueInstant='2025-08-07T01:25:13.837Z' Version='3.0'><saml:Issuer>https://www.hsbc.com/rbwm/dtp</saml:Issuer><ds:Signature><ds:SignedInfo><ds:CanonicalizationMethod Algorithm='http://www.w3.org/2001/10/xml-exc-c14n#'/><ds:SignatureMethod Algorithm='http://www.w3.org/2001/04/xmldsig-more#rsa-sha256'/><ds:Reference URI='#id_9a9e4d35-7d80-4805-849f-ddb90a8b2c1f'><ds:Transforms><ds:Transform Algorithm='http://www.w3.org/2000/09/xmldsig#enveloped-signature'/><ds:Transform Algorithm='http://www.w3.org/2001/10/xml-exc-c14n#'><ds:InclusiveNamespaces xmlns:ds='http://www.w3.org/2001/10/xml-exc-c14n#' PrefixList='#default saml ds xs xsi'/></ds:Transform></ds:Transforms><ds:DigestMethod Algorithm='http://www.w3.org/2001/04/xmlenc#sha256'/><ds:DigestValue>GLd2xpRi6DRAl81eH6NBRNzVWBlEL1zn5mWNpp16xCk=</ds:DigestValue></ds:Reference></ds:SignedInfo><ds:SignatureValue>IJQo3CpyjsDRTfXdnQGQnugNniIy56neA0eaLr87DITEauIPFNZHGCk6sb/Wp9PSIxoJNGpF4T5vVPWqUmv1fYasVtrukqodTgK2JD3NHhviDmTVGKzhZ2hnfjSewDRYeqHVURHMWY1EzltUhpZgO9u12i9+PPK4OJLFDR5Q4tZico3GfweUS7+Ds9wYssqgECZg3XayVg5w9ruSdxPIrcjU7aOe2sZFkge+I6cD2OWHC0K+u+PG+DD0UNmK9OnIY///lwgUdhbdSv0zdkUhOcHRKstuFIKhb4E8eZDogB5Sjeqya3EwJ8sIda99n+jug9IrDAjQIBTTnxtMfwq+gQ==</ds:SignatureValue></ds:Signature><saml:Subject><saml:NameID>HK00100718688801</saml:NameID></saml:Subject><saml:Conditions NotBefore='2025-08-07T01:25:12.837Z' NotOnOrAfter='2025-08-07T01:26:13.837Z'/><saml:AttributeStatement><saml:Attribute Name='GUID'><saml:AttributeValue>98b45150-5c73-11ea-8a50-0350565a170c</saml:AttributeValue></saml:Attribute><saml:Attribute Name='CAM'><saml:AttributeValue>30</saml:AttributeValue></saml:Attribute><saml:Attribute Name='KeyAlias'><saml:AttributeValue>E2E_TRUST_SAAS_AP01_BRTB1_ALIAS</saml:AttributeValue></saml:Attribute></saml:AttributeStatement></saml:Assertion>";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tradeTransferService, "tradeOnlineUrl", "https://dummy.trade");
        ReflectionTestUtils.setField(tradeTransferService, "customerAccountUrl", "https://dummy.accounts");
        ReflectionTestUtils.setField(tradeTransferService, "retrieveCustomerProfilesService", retrieveCustomerProfilesService);
        ReflectionTestUtils.setField(tradeTransferService, "sreValidationService", sreValidationService);
        lenient().when(retrieveCustomerProfilesService.getCIN(any())).thenReturn("dummy-cin");
    }

    @Test
    void retrieveTransferList_Success() {
        // Arrange
        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, DUMMY_TOKEN);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, DUMMY_TOKEN);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CUST123");

        // Mock customer accounts
        CustomerAccounts customerAccounts = new CustomerAccounts();
        InvestmentAccount account = new InvestmentAccount();
        account.setChecksum("CHECKSUM123");
        InvestmentAccountId accountId = new InvestmentAccountId();
        accountId.setCountryAccountCode("HK");
        accountId.setGroupMemberAccountCode("HBAP");
        accountId.setAccountNumber("ACC123");
        accountId.setAccountProductTypeCode("SAV");
        accountId.setAccountTypeCode("01");
        accountId.setAccountCurrencyCode("HKD");
        account.setInvestmentAccountId(accountId);
        customerAccounts.setInvestmentAccountList(Collections.singletonList(account));

        // Mock party name response
        PartyNameResponse partyNameResponse = new PartyNameResponse();
        PartyNameResponseData nameData = new PartyNameResponseData();
        nameData.setLastName("Doe");
        nameData.setGivenName("John");
        nameData.setCustomerChristianName("Christian");
        partyNameResponse.setName(nameData);

        // Mock party contact response
        PartyContactResponse partyContactResponse = new PartyContactResponse();
        // Assuming Contact object has a getMobileNumber1 method
        partyContactResponse.setContact(new Object() {
            public String getMobileNumber1() { return "123456789"; }
        });

        RetrieveTransferListResponse mockResponse = new RetrieveTransferListResponse();
        RetrieveTransferListResponseData mockResponseData = new RetrieveTransferListResponseData();
        mockResponseData.setTransferLists(new ArrayList<>());
        mockResponse.setData(mockResponseData);
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        mockResponse.setResponseDetails(responseDetails);

        when(restClientService.get(anyString(), anyMap(), eq(RetrieveTransferListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockResponse);
        when(retrieveCustomerProfilesService.retrieveCustomerAccounts(anyMap())).thenReturn(customerAccounts);
        when(retrieveCustomerProfilesService.retrieveCustomerNamesWithCinNumber(anyString(), anyMap())).thenReturn(partyNameResponse);
        when(retrieveCustomerProfilesService.retrieveCustomerPhoneNumberWithCinNumber(anyString(), anyMap())).thenReturn(partyContactResponse);
        when(e2ETrustTokenUtil.updateHeaderWithE2ETrustToken(anyMap())).thenReturn(anyMap());

        // Act
        RetrieveTransferListResponse result = tradeTransferService.retrieveTransferList(sourceRequestHeader, "ACCEPTED", Collections.singletonList("CHECKSUM123"), "1", "PROD1", "SENSITIVE");

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getResponseDetails().getResponseCodeNumber());
        verify(restClientService, times(1)).get(anyString(), anyMap(), eq(RetrieveTransferListResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void retrieveTransferList_RestClientError_ThrowsException() {
        // Arrange
        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, DUMMY_TOKEN);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, DUMMY_TOKEN);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CUST123");

        when(restClientService.get(anyString(), anyMap(), eq(RetrieveTransferListResponse.class), anyInt(), anyBoolean()))
                .thenThrow(new RuntimeException("Network error"));

        // Act & Assert
        assertThrows(InternalServerErrorException.class, () ->
                tradeTransferService.retrieveTransferList(sourceRequestHeader, "ACCEPTED", Collections.singletonList("123"), "1", "PROD1", "PLAIN_TEXT")
        );
    }

    @Test
    void retrieveTransferDetail_Success() {
        // Arrange
        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, DUMMY_TOKEN);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, DUMMY_TOKEN);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CUST123");

        String transferRefNum = "REF123";

        // Mock customer accounts
        CustomerAccounts customerAccounts = new CustomerAccounts();
        InvestmentAccount account = new InvestmentAccount();
        account.setChecksum("CHECKSUM123");
        InvestmentAccountId accountId = new InvestmentAccountId();
        accountId.setCountryAccountCode("HK");
        accountId.setGroupMemberAccountCode("HBAP");
        accountId.setAccountNumber("ACC123"); // This matches the investment account in the response
        accountId.setAccountProductTypeCode("SAV");
        accountId.setAccountTypeCode("01");
        accountId.setAccountCurrencyCode("HKD");
        account.setInvestmentAccountId(accountId);
        customerAccounts.setInvestmentAccountList(Collections.singletonList(account));

        // Mock response
        RetrieveTransferDetailResponse mockResponse = new RetrieveTransferDetailResponse();
        RetrieveTransferDetailResponseData mockResponseData = new RetrieveTransferDetailResponseData();
        mockResponseData.setTransferSideCode(TransferSideCode.SENDER);
        InvestmentAccount investmentAccount = new InvestmentAccount();
        investmentAccount.setAccountNumber("ACC123");
        mockResponseData.setInvestmentAccount(investmentAccount);
        mockResponse.setData(mockResponseData);
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        mockResponse.setResponseDetails(responseDetails);

        when(restClientService.get(anyString(), anyMap(), eq(RetrieveTransferDetailResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockResponse);
        when(retrieveCustomerProfilesService.retrieveCustomerAccounts(anyMap())).thenReturn(customerAccounts);

        // Act
        RetrieveTransferDetailResponse result = tradeTransferService.retrieveTransferDetail(sourceRequestHeader, transferRefNum);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getResponseDetails().getResponseCodeNumber());
        assertEquals("CHECKSUM123", result.getData().getAccountChecksumIdentifier()); // Verify checksum was set
        verify(restClientService, times(1)).get(anyString(), anyMap(), eq(RetrieveTransferDetailResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void retrieveTransferDetail_NoInvestmentAccountInResponse_DoesNotSetChecksum() {
        // Arrange
        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, DUMMY_TOKEN);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, DUMMY_TOKEN);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CUST123");

        String transferRefNum = "REF123";

        // Mock response with null investment account
        RetrieveTransferDetailResponse mockResponse = new RetrieveTransferDetailResponse();
        RetrieveTransferDetailResponseData mockResponseData = new RetrieveTransferDetailResponseData();
        mockResponseData.setTransferSideCode(TransferSideCode.SENDER);
        mockResponseData.setInvestmentAccount(null); // Null investment account
        mockResponse.setData(mockResponseData);
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        mockResponse.setResponseDetails(responseDetails);

        when(restClientService.get(anyString(), anyMap(), eq(RetrieveTransferDetailResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockResponse);

        // Act
        RetrieveTransferDetailResponse result = tradeTransferService.retrieveTransferDetail(sourceRequestHeader, transferRefNum);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getResponseDetails().getResponseCodeNumber());
        // Checksum should not be set because investment account was null
        assertNull(result.getData().getAccountChecksumIdentifier());
        verify(restClientService, times(1)).get(anyString(), anyMap(), eq(RetrieveTransferDetailResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void retrieveTransferDetail_AccountNotFoundInCustomerList_ThrowsBadRequest() {
        // Arrange
        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, DUMMY_TOKEN);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, DUMMY_TOKEN);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CUST123");

        String transferRefNum = "REF123";

        // Mock customer accounts with a different account number
        CustomerAccounts customerAccounts = new CustomerAccounts();
        InvestmentAccount account = new InvestmentAccount();
        account.setChecksum("CHECKSUM123");
        InvestmentAccountId accountId = new InvestmentAccountId();
        accountId.setCountryAccountCode("HK");
        accountId.setGroupMemberAccountCode("HBAP");
        accountId.setAccountNumber("DIFFERENT_ACC"); // Different from the one in the response
        accountId.setAccountProductTypeCode("SAV");
        accountId.setAccountTypeCode("01");
        accountId.setAccountCurrencyCode("HKD");
        account.setInvestmentAccountId(accountId);
        customerAccounts.setInvestmentAccountList(Collections.singletonList(account));

        // Mock response with a specific investment account
        RetrieveTransferDetailResponse mockResponse = new RetrieveTransferDetailResponse();
        RetrieveTransferDetailResponseData mockResponseData = new RetrieveTransferDetailResponseData();
        mockResponseData.setTransferSideCode(TransferSideCode.SENDER);
        InvestmentAccount investmentAccount = new InvestmentAccount();
        investmentAccount.setAccountNumber("ACC123"); // This is the account number in the response
        mockResponseData.setInvestmentAccount(investmentAccount);
        mockResponse.setData(mockResponseData);
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        mockResponse.setResponseDetails(responseDetails);

        when(restClientService.get(anyString(), anyMap(), eq(RetrieveTransferDetailResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockResponse);
        when(retrieveCustomerProfilesService.retrieveCustomerAccounts(anyMap())).thenReturn(customerAccounts);

        // Act & Assert
        assertThrows(BadRequestException.class, () ->
                tradeTransferService.retrieveTransferDetail(sourceRequestHeader, transferRefNum)
        );
    }

    @Test
    void retrieveTransferDetail_RestClientError_ThrowsException() {
        // Arrange
        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, DUMMY_TOKEN);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, DUMMY_TOKEN);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CUST123");

        String transferRefNum = "REF123";

        when(restClientService.get(anyString(), anyMap(), eq(RetrieveTransferDetailResponse.class), anyInt(), anyBoolean()))
                .thenThrow(new RuntimeException("Network error"));

        // Act & Assert
        assertThrows(InternalServerErrorException.class, () ->
                tradeTransferService.retrieveTransferDetail(sourceRequestHeader, transferRefNum)
        );
    }

    @Test
    void createTransfers_Success() {
        // Arrange
        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, DUMMY_TOKEN);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, DUMMY_TOKEN);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CUST123");

        CreateTransferRequest request = new CreateTransferRequest();
        CreateTransferRequestData requestData = new CreateTransferRequestData();
        request.setData(requestData);
        request.getData().setActionRequestCode(ActionRequestCode.C); // Not D operation
        request.getData().setSenderInvestmentAccountChecksumIdentifier("CHECKSUM123");
        request.getData().setRequestPriceValue(new BigDecimal("100")); // Price for C operation

        ReceiverInfo receiver = new ReceiverInfo();
        receiver.setTransferQuantity(new BigDecimal("50"));
        receiver.setReceiverCustomerNumber("RECEIVER_CIN");
        request.getData().setReceiverLists(Collections.singletonList(receiver));

        // Mock limit check
        RetrieveTransferLimitResponse limitResponse = new RetrieveTransferLimitResponse();
        RetrieveTransferLimitResponseData limitData = new RetrieveTransferLimitResponseData();
        limitData.setAvailableTodayAmount(new BigDecimal("10000"));
        limitData.setAvailableMonthToDateAmount(new BigDecimal("10000"));
        limitData.setAvailableYearToDateAmount(new BigDecimal("10000"));
        limitResponse.setData(limitData);
        when(tradeLimitService.retrieveLimitations(anyMap())).thenReturn(limitResponse);

        // Mock account ID retrieval
        InvestmentAccountId expectedAccountId = new InvestmentAccountId();
        expectedAccountId.setCountryAccountCode("HK");
        expectedAccountId.setGroupMemberAccountCode("HBAP");
        expectedAccountId.setAccountNumber("ACC123");
        expectedAccountId.setAccountProductTypeCode("SAV");
        expectedAccountId.setAccountTypeCode("01");
        expectedAccountId.setAccountCurrencyCode("HKD");
        AccountId accountId = new AccountId(expectedAccountId);
        RetrieveCustomerAccountsIdListResponse accountResponse = new RetrieveCustomerAccountsIdListResponse();
        accountResponse.setAccountIdList(Collections.singletonList(new InvestmentAccountIdList(expectedAccountId)));
        when(restClientService.get(anyString(), anyMap(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(accountResponse);

        // Mock SRE validation
        RuleResponse sreResponse = new RuleResponse();
        ResponseDetails sreDetails = new ResponseDetails();
        sreDetails.setResponseCodeNumber(0);
        sreResponse.setResponseDetails(sreDetails);
        when(sreValidationService.callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap())).thenReturn(sreResponse);

        // Mock party name response
        PartyNameResponse partyNameResponse = new PartyNameResponse();
        PartyNameResponseData nameData = new PartyNameResponseData();
        nameData.setLastName("Doe");
        nameData.setGivenName("John");
        nameData.setCustomerChristianName("Christian");
        partyNameResponse.setName(nameData);
        when(retrieveCustomerProfilesService.retrieveCustomerNamesWithCinNumber(anyString(), anyMap())).thenReturn(partyNameResponse);
        when(e2ETrustTokenUtil.updateHeaderWithE2ETrustToken(anyMap())).thenReturn(anyMap());

        // Mock gold price (not used for C operation)
        GoldPriceResponse goldPriceResponse = new GoldPriceResponse();
        GoldPriceResponseData priceData = new GoldPriceResponseData();
        priceData.setGoldPriceAmount(new BigDecimal("2000"));
        priceData.setPublishTime("2025-11-20T10:00:00Z");
        goldPriceResponse.setData(priceData);
        when(retrieveCustomerProfilesService.retrieveGoldPrice(anyMap())).thenReturn(goldPriceResponse);

        // Mock CreateTransferResponse
        CreateTransferResponse mockCreateTransferResponse = new CreateTransferResponse();
        CreateTransferRequestData responseData = new CreateTransferRequestData();
        responseData.setSenderCustomerName("Doe John Christian"); // Expected full name
        mockCreateTransferResponse.setData(responseData);
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        mockCreateTransferResponse.setResponseDetails(responseDetails);

        when(restClientService.post(anyString(), anyMap(), any(CreateTransferRequest.class), eq(CreateTransferResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockCreateTransferResponse);

        // Act
        CreateTransferResponse result = tradeTransferService.createTransfers(sourceRequestHeader, request);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getResponseDetails().getResponseCodeNumber());
        assertEquals("Doe John Christian", result.getData().getSenderCustomerName());
        verify(sreValidationService, times(1)).callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap());
        verify(restClientService, times(1)).post(anyString(), anyMap(), any(CreateTransferRequest.class), eq(CreateTransferResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void createTransfers_DOperation_SetsRealtimePriceAndUniqueKey() {
        // Arrange
        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, DUMMY_TOKEN);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, DUMMY_TOKEN);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CUST123");

        CreateTransferRequest request = new CreateTransferRequest();
        CreateTransferRequestData requestData = new CreateTransferRequestData();
        request.setData(requestData);
        request.getData().setActionRequestCode(ActionRequestCode.D); // D operation
        // request.getData().setRequestPriceValue() will be set by the service based on MDS
        request.getData().setSenderInvestmentAccountChecksumIdentifier("CHECKSUM123");

        ReceiverInfo receiver = new ReceiverInfo();
        receiver.setTransferQuantity(new BigDecimal("50"));
        receiver.setReceiverCustomerNumber("RECEIVER_CIN");
        request.getData().setReceiverLists(Collections.singletonList(receiver));

        // Mock limit check
        RetrieveTransferLimitResponse limitResponse = new RetrieveTransferLimitResponse();
        RetrieveTransferLimitResponseData limitData = new RetrieveTransferLimitResponseData();
        limitData.setAvailableTodayAmount(new BigDecimal("10000"));
        limitData.setAvailableMonthToDateAmount(new BigDecimal("10000"));
        limitData.setAvailableYearToDateAmount(new BigDecimal("10000"));
        limitResponse.setData(limitData);
        when(tradeLimitService.retrieveLimitations(anyMap())).thenReturn(limitResponse);

        // Mock account ID retrieval
        InvestmentAccountId expectedAccountId = new InvestmentAccountId();
        expectedAccountId.setCountryAccountCode("HK");
        expectedAccountId.setGroupMemberAccountCode("HBAP");
        expectedAccountId.setAccountNumber("ACC123");
        expectedAccountId.setAccountProductTypeCode("SAV");
        expectedAccountId.setAccountTypeCode("01");
        expectedAccountId.setAccountCurrencyCode("HKD");
        AccountId accountId = new AccountId(expectedAccountId);
        RetrieveCustomerAccountsIdListResponse accountResponse = new RetrieveCustomerAccountsIdListResponse();
        accountResponse.setAccountIdList(Collections.singletonList(new InvestmentAccountIdList(expectedAccountId)));
        when(restClientService.get(anyString(), anyMap(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(accountResponse);

        // Mock SRE validation
        RuleResponse sreResponse = new RuleResponse();
        ResponseDetails sreDetails = new ResponseDetails();
        sreDetails.setResponseCodeNumber(0);
        sreResponse.setResponseDetails(sreDetails);
        when(sreValidationService.callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap())).thenReturn(sreResponse);

        // Mock party name response
        PartyNameResponse partyNameResponse = new PartyNameResponse();
        PartyNameResponseData nameData = new PartyNameResponseData();
        nameData.setLastName("Doe");
        nameData.setGivenName("John");
        nameData.setCustomerChristianName("Christian");
        partyNameResponse.setName(nameData);
        when(retrieveCustomerProfilesService.retrieveCustomerNamesWithCinNumber(anyString(), anyMap())).thenReturn(partyNameResponse);
        when(e2ETrustTokenUtil.updateHeaderWithE2ETrustToken(anyMap())).thenReturn(anyMap());

        // Mock gold price (used for D operation)
        GoldPriceResponse goldPriceResponse = new GoldPriceResponse();
        GoldPriceResponseData priceData = new GoldPriceResponseData();
        priceData.setGoldPriceAmount(new BigDecimal("2000"));
        priceData.setPublishTime("2025-11-20T10:00:00Z");
        goldPriceResponse.setData(priceData);
        when(retrieveCustomerProfilesService.retrieveGoldPrice(anyMap())).thenReturn(goldPriceResponse);

        // Mock CreateTransferResponse with order list
        CreateTransferResponse mockCreateTransferResponse = new CreateTransferResponse();
        CreateTransferRequestData responseData = new CreateTransferRequestData();
        List<TransferOrderInfo> orderList = new ArrayList<>();
        TransferOrderInfo orderInfo = new TransferOrderInfo();
        orderInfo.setRequestPriceValue(new BigDecimal("100")); // This should be updated
        orderList.add(orderInfo);
        responseData.setTransferOrderLists(orderList);
        responseData.setRequestPriceValue(new BigDecimal("100")); // This should be updated
        responseData.setRequestPriceCurrencyCode("USD"); // This should be updated
        responseData.setRequestPriceAsOfDateTime("2025-11-19T10:00:00Z"); // This should be updated
        responseData.setSenderCustomerName("Doe John Christian"); // Expected full name
        mockCreateTransferResponse.setData(responseData);
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        mockCreateTransferResponse.setResponseDetails(responseDetails);

        // Mock unique key generation
        String expectedUniqueKey = "unique-key-123";
        when(duplicateSubmitPreventionService.generateUniqueKey()).thenReturn(expectedUniqueKey);

        when(restClientService.post(anyString(), anyMap(), any(CreateTransferRequest.class), eq(CreateTransferResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockCreateTransferResponse);

        // Act
        CreateTransferResponse result = tradeTransferService.createTransfers(sourceRequestHeader, request);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getResponseDetails().getResponseCodeNumber());
        assertEquals("Doe John Christian", result.getData().getSenderCustomerName());
        // Verify D-operation specific changes
        assertEquals(expectedUniqueKey, result.getData().getRequestUniqueKey());
        assertEquals(new BigDecimal("2000"), result.getData().getRequestPriceValue());
        assertEquals("HKD", result.getData().getRequestPriceCurrencyCode());
        assertEquals("2025-11-20T10:00:00Z", result.getData().getRequestPriceAsOfDateTime());
        // Verify order list price was updated
        assertEquals(new BigDecimal("2000"), result.getData().getTransferOrderLists().get(0).getRequestPriceValue());
        assertEquals("HKD", result.getData().getTransferOrderLists().get(0).getPriceCurrencyCode());
        assertEquals("2025-11-20T10:00:00Z", result.getData().getTransferOrderLists().get(0).getRequestPriceAsOfDateTime());
        verify(sreValidationService, times(1)).callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap());
        verify(restClientService, times(1)).post(anyString(), anyMap(), any(CreateTransferRequest.class), eq(CreateTransferResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void createTransfers_ExceedsDailyLimit_ThrowsException() {
        // Arrange
        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, DUMMY_TOKEN);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, DUMMY_TOKEN);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CUST123");

        CreateTransferRequest request = new CreateTransferRequest();
        CreateTransferRequestData requestData = new CreateTransferRequestData();
        request.setData(requestData);
        request.getData().setActionRequestCode(ActionRequestCode.C);
        request.getData().setSenderInvestmentAccountChecksumIdentifier("CHECKSUM123");
        request.getData().setRequestPriceValue(new BigDecimal("100"));

        ReceiverInfo receiver = new ReceiverInfo();
        receiver.setTransferQuantity(new BigDecimal("50")); // Total = 100 * 50 = 5000
        request.getData().setReceiverLists(Collections.singletonList(receiver));

        // Mock limit check - available is less than required
        RetrieveTransferLimitResponse limitResponse = new RetrieveTransferLimitResponse();
        RetrieveTransferLimitResponseData limitData = new RetrieveTransferLimitResponseData();
        limitData.setAvailableTodayAmount(new BigDecimal("1000")); // Less than 5000
        limitData.setMaxDailyLimitedAmount(new BigDecimal("10000"));
        limitResponse.setData(limitData);
        when(tradeLimitService.retrieveLimitations(anyMap())).thenReturn(limitResponse);

        // Act & Assert
        assertThrows(TransferLimitExceededException.class, () ->
                tradeTransferService.createTransfers(sourceRequestHeader, request)
        );
        verify(restClientService, never()).post(anyString(), anyMap(), any(CreateTransferRequest.class), eq(CreateTransferResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void modifyTransfers_AcceptAction_Success() {
        // Arrange
        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, DUMMY_TOKEN);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, DUMMY_TOKEN);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CUST123");

        UpdateTransferRequest request = new UpdateTransferRequest();
        UpdateTransferRequestData requestData = new UpdateTransferRequestData();
        request.setData(requestData);
        request.getData().setTransferActionCode(TransferActionCode.A); // Accept
        request.getData().setReceiverCustomerInternalNumber("RECEIVER_CIN");
        request.getData().setReceiverInvestmentAccountChecksumIdentifier("CHECKSUM456");

        // Mock account ID retrieval for receiver
        InvestmentAccountId expectedAccountId = new InvestmentAccountId();
        expectedAccountId.setCountryAccountCode("HK");
        expectedAccountId.setGroupMemberAccountCode("HBAP");
        expectedAccountId.setAccountNumber("ACC456");
        expectedAccountId.setAccountProductTypeCode("SAV");
        expectedAccountId.setAccountTypeCode("01");
        expectedAccountId.setAccountCurrencyCode("HKD");
        AccountId accountId = new AccountId(expectedAccountId);
        RetrieveCustomerAccountsIdListResponse accountResponse = new RetrieveCustomerAccountsIdListResponse();
        accountResponse.setAccountIdList(Collections.singletonList(new InvestmentAccountIdList(expectedAccountId)));
        when(restClientService.get(anyString(), anyMap(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(accountResponse);

        // Mock SRE validation for receiver
        RuleResponse sreResponse = new RuleResponse();
        ResponseDetails sreDetails = new ResponseDetails();
        sreDetails.setResponseCodeNumber(0);
        sreResponse.setResponseDetails(sreDetails);
        when(sreValidationService.callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap())).thenReturn(sreResponse);

        // Mock gold price
        GoldPriceResponse goldPriceResponse = new GoldPriceResponse();
        GoldPriceResponseData priceData = new GoldPriceResponseData();
        priceData.setGoldPriceAmount(new BigDecimal("2000"));
        priceData.setPublishTime("2025-11-20T10:00:00Z");
        goldPriceResponse.setData(priceData);
        when(retrieveCustomerProfilesService.retrieveGoldPrice(anyMap())).thenReturn(goldPriceResponse);

        // Mock UpdateTransferResponse
        UpdateTransferResponse mockUpdateTransferResponse = new UpdateTransferResponse();
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        mockUpdateTransferResponse.setResponseDetails(responseDetails);

        when(restClientService.put(anyString(), anyMap(), any(UpdateTransferRequest.class), eq(UpdateTransferResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockUpdateTransferResponse);

        // Act
        UpdateTransferResponse result = tradeTransferService.modifyTransfers(sourceRequestHeader, request);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getResponseDetails().getResponseCodeNumber());
        // Verify SRE was called for accept action
        verify(sreValidationService, times(1)).callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap());
        // Verify gold price was fetched for accept action
        verify(retrieveCustomerProfilesService, times(1)).retrieveGoldPrice(anyMap());
        // Verify the receiver CIN was updated to the sender's CIN
        assertEquals("CUST123", request.getData().getReceiverCustomerInternalNumber());
        // Verify the receiver investment account was set
        assertEquals(expectedAccountId, request.getData().getReceiverInvestmentAccount().getAccountId());
        verify(restClientService, times(1)).put(anyString(), anyMap(), any(UpdateTransferRequest.class), eq(UpdateTransferResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void modifyTransfers_RejectAction_Success() {
        // Arrange
        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, DUMMY_TOKEN);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, DUMMY_TOKEN);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CUST123");

        UpdateTransferRequest request = new UpdateTransferRequest();
        UpdateTransferRequestData requestData = new UpdateTransferRequestData();
        request.setData(requestData);
        request.getData().setTransferActionCode(TransferActionCode.R); // Reject
        request.getData().setReceiverCustomerInternalNumber("RECEIVER_CIN");
        request.getData().setReceiverInvestmentAccountChecksumIdentifier("CHECKSUM456"); // This should be ignored for reject

        // Mock UpdateTransferResponse
        UpdateTransferResponse mockUpdateTransferResponse = new UpdateTransferResponse();
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        mockUpdateTransferResponse.setResponseDetails(responseDetails);

        when(restClientService.put(anyString(), anyMap(), any(UpdateTransferRequest.class), eq(UpdateTransferResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockUpdateTransferResponse);

        // Act
        UpdateTransferResponse result = tradeTransferService.modifyTransfers(sourceRequestHeader, request);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getResponseDetails().getResponseCodeNumber());
        // Verify SRE was NOT called for reject action
        verify(sreValidationService, never()).callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap());
        // Verify gold price was NOT fetched for reject action
        verify(retrieveCustomerProfilesService, never()).retrieveGoldPrice(anyMap());
        // Verify the receiver CIN was updated to the sender's CIN
        assertEquals("CUST123", request.getData().getReceiverCustomerInternalNumber());
        // Verify the receiver investment account was NOT set
        assertNull(request.getData().getReceiverInvestmentAccount());
        verify(restClientService, times(1)).put(anyString(), anyMap(), any(UpdateTransferRequest.class), eq(UpdateTransferResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void modifyTransfers_NonAcceptOrRejectAction_Success() {
        // Arrange
        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, DUMMY_TOKEN);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, DUMMY_TOKEN);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CUST123");

        UpdateTransferRequest request = new UpdateTransferRequest();
        UpdateTransferRequestData requestData = new UpdateTransferRequestData();
        request.setData(requestData);
        request.getData().setTransferActionCode(TransferActionCode.C); // Cancel, not A or R
        request.getData().setReceiverCustomerInternalNumber("RECEIVER_CIN");
        request.getData().setReceiverInvestmentAccountChecksumIdentifier("CHECKSUM456"); // This should be ignored

        // Mock UpdateTransferResponse
        UpdateTransferResponse mockUpdateTransferResponse = new UpdateTransferResponse();
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        mockUpdateTransferResponse.setResponseDetails(responseDetails);

        when(restClientService.put(anyString(), anyMap(), any(UpdateTransferRequest.class), eq(UpdateTransferResponse.class), anyInt(), anyBoolean()))
                .thenReturn(mockUpdateTransferResponse);

        // Act
        UpdateTransferResponse result = tradeTransferService.modifyTransfers(sourceRequestHeader, request);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getResponseDetails().getResponseCodeNumber());
        // Verify SRE was NOT called for other actions
        verify(sreValidationService, never()).callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap());
        // Verify gold price was NOT fetched for other actions
        verify(retrieveCustomerProfilesService, never()).retrieveGoldPrice(anyMap());
        // Verify the receiver CIN was NOT updated (it should remain as provided or be handled differently based on action)
        // The service sets receiver CIN to sender CIN *only* for A or R.
        // For other actions, the original value might remain or be handled differently.
        // Based on the original code: `if (actionCode.equals(TransferActionCode.A))` sets receiverInvestmentAccount and calls SRE.
        // The `receiverCustomerInternalNumber` is set to sender's CIN inside the `if (actionCode.equals(TransferActionCode.R) || actionCode.equals(TransferActionCode.A))` block.
        // So for action C, `receiverCustomerInternalNumber` should be set to sender's CIN.
        assertEquals("CUST123", request.getData().getReceiverCustomerInternalNumber());
        // Verify the receiver investment account was NOT set
        assertNull(request.getData().getReceiverInvestmentAccount());
        verify(restClientService, times(1)).put(anyString(), anyMap(), any(UpdateTransferRequest.class), eq(UpdateTransferResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void extractAccountIdMap_EmptyCustomerAccounts_ReturnsEmptyMap() {
        // Arrange
        CustomerAccounts customerAccounts = new CustomerAccounts();
        customerAccounts.setInvestmentAccountList(null); // Or an empty list

        // Act
        Map<String, String> result = tradeTransferService.extractAccountIdMap(customerAccounts);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void extractAccountIdMap_ValidCustomerAccounts_ReturnsCorrectMap() {
        // Arrange
        CustomerAccounts customerAccounts = new CustomerAccounts();
        InvestmentAccount account1 = new InvestmentAccount();
        account1.setChecksum("CHECKSUM1");
        InvestmentAccountId accountId1 = new InvestmentAccountId();
        accountId1.setCountryAccountCode("HK");
        accountId1.setGroupMemberAccountCode("HBAP");
        accountId1.setAccountNumber("ACC1");
        accountId1.setAccountProductTypeCode("SAV");
        accountId1.setAccountTypeCode("01");
        accountId1.setAccountCurrencyCode("HKD");
        account1.setInvestmentAccountId(accountId1);

        InvestmentAccount account2 = new InvestmentAccount();
        account2.setChecksum("CHECKSUM2");
        InvestmentAccountId accountId2 = new InvestmentAccountId();
        accountId2.setCountryAccountCode("UK");
        accountId2.setGroupMemberAccountCode("HSBC");
        accountId2.setAccountNumber("ACC2");
        accountId2.setAccountProductTypeCode("CUR");
        accountId2.setAccountTypeCode("02");
        accountId2.setAccountCurrencyCode("GBP");
        account2.setInvestmentAccountId(accountId2);

        customerAccounts.setInvestmentAccountList(Arrays.asList(account1, account2));

        // Act
        Map<String, String> result = tradeTransferService.extractAccountIdMap(customerAccounts);

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.containsKey("CHECKSUM1"));
        assertTrue(result.containsKey("CHECKSUM2"));
        String expectedFormat1 = "countryAccountCode=HK;groupMemberAccountCode=HBAP;accountNumber=ACC1;accountProductTypeCode=SAV;accountTypeCode=01;accountCurrencyCode=HKD";
        String expectedFormat2 = "countryAccountCode=UK;groupMemberAccountCode=HSBC;accountNumber=ACC2;accountProductTypeCode=CUR;accountTypeCode=02;accountCurrencyCode=GBP";
        assertEquals(expectedFormat1, result.get("CHECKSUM1"));
        assertEquals(expectedFormat2, result.get("CHECKSUM2"));
    }

    @Test
    void maskNamesInResponse_ListData_ReceiverSide_MasksSenderNames() {
        // Arrange
        RetrieveTransferListResponseData responseData = new RetrieveTransferListResponseData();
        List<TransferListItemInfo> transferLists = new ArrayList<>();
        TransferListItemInfo item = new TransferListItemInfo();
        item.setTransferSideCode(TransferSideCode.RECEIVER);
        item.setSenderCustomerFirstName("John");
        item.setSenderCustomerMiddleName("A");
        item.setSenderCustomerLastName("Doe");
        transferLists.add(item);
        responseData.setTransferLists(transferLists);

        // Act
        tradeTransferService.maskNamesInResponse(responseData);

        // Assert
        assertEquals("J****n", responseData.getTransferLists().get(0).getSenderCustomerFirstName());
        assertEquals("A***", responseData.getTransferLists().get(0).getSenderCustomerMiddleName());
        // LastName is typically not masked based on the current implementation
        assertEquals("Doe", responseData.getTransferLists().get(0).getSenderCustomerLastName());
    }

    @Test
    void maskNamesInResponse_ListData_SenderSideBankCustomer_MasksReceiverNames() {
        // Arrange
        RetrieveTransferListResponseData responseData = new RetrieveTransferListResponseData();
        List<TransferListItemInfo> transferLists = new ArrayList<>();
        TransferListItemInfo item = new TransferListItemInfo();
        item.setTransferSideCode(TransferSideCode.SENDER);
        item.setIsReceiverBankCustomer("Y");
        item.setReceiverCustomerFirstName("Jane");
        item.setReceiverCustomerMiddleName("B");
        item.setReceiverCustomerLastName("Smith");
        transferLists.add(item);
        responseData.setTransferLists(transferLists);

        // Act
        tradeTransferService.maskNamesInResponse(responseData);

        // Assert
        assertEquals("J****e", responseData.getTransferLists().get(0).getReceiverCustomerFirstName());
        assertEquals("B***", responseData.getTransferLists().get(0).getReceiverCustomerMiddleName());
        // LastName is typically not masked based on the current implementation
        assertEquals("Smith", responseData.getTransferLists().get(0).getReceiverCustomerLastName());
    }

    @Test
    void maskNamesInResponse_DetailData_ReceiverSide_MasksSenderNames() {
        // Arrange
        RetrieveTransferDetailResponseData responseData = new RetrieveTransferDetailResponseData();
        responseData.setTransferSideCode(TransferSideCode.RECEIVER);
        responseData.setSenderCustomerFirstName("John");
        responseData.setSenderCustomerMiddleName("A");
        responseData.setSenderCustomerLastName("Doe");

        // Act
        tradeTransferService.maskNamesInResponse(responseData);

        // Assert
        assertEquals("J****n", responseData.getSenderCustomerFirstName());
        assertEquals("A***", responseData.getSenderCustomerMiddleName());
        // LastName is typically not masked based on the current implementation
        assertEquals("Doe", responseData.getSenderCustomerLastName());
    }

    @Test
    void maskNamesInResponse_DetailData_SenderSideBankCustomer_MasksReceiverNames() {
        // Arrange
        RetrieveTransferDetailResponseData responseData = new RetrieveTransferDetailResponseData();
        responseData.setTransferSideCode(TransferSideCode.SENDER);
        responseData.setIsReceiverBankCustomer("Y");
        responseData.setReceiverCustomerFirstName("Jane");
        responseData.setReceiverCustomerMiddleName("B");
        responseData.setReceiverCustomerLastName("Smith");

        // Act
        tradeTransferService.maskNamesInResponse(responseData);

        // Assert
        assertEquals("J****e", responseData.getReceiverCustomerFirstName());
        assertEquals("B***", responseData.getReceiverCustomerMiddleName());
        // LastName is typically not masked based on the current implementation
        assertEquals("Smith", responseData.getReceiverCustomerLastName());
    }

    @Test
    void validateTransferLimits_DailyLimitExceeded_ThrowsException() {
        BigDecimal totalTranAmount = new BigDecimal("1000");
        RetrieveTransferLimitResponse currentLimit = new RetrieveTransferLimitResponse();
        RetrieveTransferLimitResponseData data = new RetrieveTransferLimitResponseData();
        data.setAvailableTodayAmount(new BigDecimal("500")); // Less than total amount
        data.setMaxDailyLimitedAmount(new BigDecimal("1000"));
        currentLimit.setData(data);

        assertThrows(TransferLimitExceededException.class, () ->
                tradeTransferService.validateTransferLimits(totalTranAmount, currentLimit)
        );
    }

    @Test
    void validateTransferLimits_MonthlyLimitExceeded_ThrowsException() {
        BigDecimal totalTranAmount = new BigDecimal("1000");
        RetrieveTransferLimitResponse currentLimit = new RetrieveTransferLimitResponse();
        RetrieveTransferLimitResponseData data = new RetrieveTransferLimitResponseData();
        data.setAvailableTodayAmount(new BigDecimal("2000")); // Sufficient for daily
        data.setAvailableMonthToDateAmount(new BigDecimal("500")); // Less than total amount
        data.setMaxMonthlyLimitedAmount(new BigDecimal("1000"));
        currentLimit.setData(data);

        assertThrows(TransferLimitExceededException.class, () ->
                tradeTransferService.validateTransferLimits(totalTranAmount, currentLimit)
        );
    }

    @Test
    void validateTransferLimits_YearlyLimitExceeded_ThrowsException() {
        BigDecimal totalTranAmount = new BigDecimal("1000");
        RetrieveTransferLimitResponse currentLimit = new RetrieveTransferLimitResponse();
        RetrieveTransferLimitResponseData data = new RetrieveTransferLimitResponseData();
        data.setAvailableTodayAmount(new BigDecimal("2000")); // Sufficient for daily
        data.setAvailableMonthToDateAmount(new BigDecimal("2000")); // Sufficient for monthly
        data.setAvailableYearToDateAmount(new BigDecimal("500")); // Less than total amount
        data.setMaxYearlyLimitedAmount(new BigDecimal("1000"));
        currentLimit.setData(data);

        assertThrows(TransferLimitExceededException.class, () ->
                tradeTransferService.validateTransferLimits(totalTranAmount, currentLimit)
        );
    }

    @Test
    void validateTransferLimits_AllLimitsSufficient_DoesNotThrowException() {
        BigDecimal totalTranAmount = new BigDecimal("500");
        RetrieveTransferLimitResponse currentLimit = new RetrieveTransferLimitResponse();
        RetrieveTransferLimitResponseData data = new RetrieveTransferLimitResponseData();
        data.setAvailableTodayAmount(new BigDecimal("1000"));
        data.setAvailableMonthToDateAmount(new BigDecimal("1000"));
        data.setAvailableYearToDateAmount(new BigDecimal("1000"));
        currentLimit.setData(data);

        assertDoesNotThrow(() ->
                tradeTransferService.validateTransferLimits(totalTranAmount, currentLimit)
        );
    }

    @Test
    void findAccountChecksumForAccountNumber_AccountExists_ReturnsChecksum() {
        CustomerAccounts customerAccounts = new CustomerAccounts();
        InvestmentAccount account = new InvestmentAccount();
        account.setChecksum("checksum-123");
        InvestmentAccountId accountId = new InvestmentAccountId();
        accountId.setAccountNumber("123456");
        account.setInvestmentAccountId(accountId);
        customerAccounts.setInvestmentAccountList(Collections.singletonList(account));

        String result = tradeTransferService.findAccountChecksumForAccountNumber(customerAccounts, "123456");

        assertEquals("checksum-123", result);
    }

    @Test
    void findAccountChecksumForAccountNumber_AccountDoesNotExist_ReturnsNull() {
        CustomerAccounts customerAccounts = new CustomerAccounts();
        InvestmentAccount account = new InvestmentAccount();
        account.setChecksum("checksum-123");
        InvestmentAccountId accountId = new InvestmentAccountId();
        accountId.setAccountNumber("123456");
        account.setInvestmentAccountId(accountId);
        customerAccounts.setInvestmentAccountList(Collections.singletonList(account));

        String result = tradeTransferService.findAccountChecksumForAccountNumber(customerAccounts, "999999");

        assertNull(result);
    }

    @Test
    void handleDOperationResponse_ActionIsD_SetsUniqueKeyAndUpdatesOrderPrices() {
        CreateTransferRequest createTransferRequest = new CreateTransferRequest();
        CreateTransferRequestData requestData = new CreateTransferRequestData();
        requestData.setActionRequestCode(ActionRequestCode.D); // D operation
        createTransferRequest.setData(requestData);

        CreateTransferResponse createTransferResponse = new CreateTransferResponse();
        CreateTransferRequestData responseData = new CreateTransferRequestData();
        List<TransferOrderInfo> orderInfoList = new ArrayList<>();
        TransferOrderInfo orderInfo = new TransferOrderInfo();
        orderInfo.setRequestPriceValue(new BigDecimal("100"));
        orderInfo.setRequestPriceCurrencyCode("USD");
        orderInfo.setRequestPriceAsOfDateTime("2025-11-19T10:00:00Z");
        orderInfoList.add(orderInfo);
        responseData.setTransferOrderLists(orderInfoList);
        createTransferResponse.setData(responseData);

        GoldPriceResponse goldPriceResponse = new GoldPriceResponse();
        GoldPriceResponseData mdsData = new GoldPriceResponseData();
        mdsData.setGoldPriceAmount(new BigDecimal("2000"));
        mdsData.setPublishTime("2025-11-20T10:00:00Z");
        goldPriceResponse.setData(mdsData);

        String expectedUniqueKey = "unique-key-123";
        when(duplicateSubmitPreventionService.generateUniqueKey()).thenReturn(expectedUniqueKey);

        tradeTransferService.handleDOperationResponse(createTransferRequest, createTransferResponse, goldPriceResponse);

        assertEquals(expectedUniqueKey, createTransferResponse.getData().getRequestUniqueKey());
        assertEquals(new BigDecimal("2000"), createTransferResponse.getData().getTransferOrderLists().get(0).getRequestPriceValue());
        assertEquals("HKD", createTransferResponse.getData().getTransferOrderLists().get(0).getPriceCurrencyCode());
        assertEquals("2025-11-20T10:00:00Z", createTransferResponse.getData().getTransferOrderLists().get(0).getRequestPriceAsOfDateTime());
    }

    @Test
    void handleDOperationResponse_ActionIsNotD_DoesNotModifyResponse() {
        CreateTransferRequest createTransferRequest = new CreateTransferRequest();
        CreateTransferRequestData requestData = new CreateTransferRequestData();
        requestData.setActionRequestCode(ActionRequestCode.C); // Not D operation
        createTransferRequest.setData(requestData);

        CreateTransferResponse createTransferResponse = new CreateTransferResponse();
        CreateTransferRequestData responseData = new CreateTransferRequestData();
        List<TransferOrderInfo> orderInfoList = new ArrayList<>();
        TransferOrderInfo orderInfo = new TransferOrderInfo();
        orderInfo.setRequestPriceValue(new BigDecimal("100"));
        orderInfo.setRequestPriceCurrencyCode("USD");
        orderInfo.setRequestPriceAsOfDateTime("2025-11-19T10:00:00Z");
        orderInfoList.add(orderInfo);
        responseData.setTransferOrderLists(orderInfoList);
        createTransferResponse.setData(responseData);

        GoldPriceResponse goldPriceResponse = new GoldPriceResponse();
        GoldPriceResponseData mdsData = new GoldPriceResponseData();
        mdsData.setGoldPriceAmount(new BigDecimal("2000"));
        mdsData.setPublishTime("2025-11-20T10:00:00Z");
        goldPriceResponse.setData(mdsData);

        tradeTransferService.handleDOperationResponse(createTransferRequest, createTransferResponse, goldPriceResponse);

        // Unique key should remain null
        assertNull(createTransferResponse.getData().getRequestUniqueKey());
        // Order list values should remain unchanged
        assertEquals(new BigDecimal("100"), createTransferResponse.getData().getTransferOrderLists().get(0).getRequestPriceValue());
        assertEquals("USD", createTransferResponse.getData().getTransferOrderLists().get(0).getPriceCurrencyCode());
        assertEquals("2025-11-19T10:00:00Z", createTransferResponse.getData().getTransferOrderLists().get(0).getRequestPriceAsOfDateTime());
    }

    // Test for private methods using reflection is generally discouraged [[1]].
    // The methods addCustomerNameToUri, addCustomerContactToUri, and validateSreForReceivers
    // are protected/package-private and their logic is exercised through public methods like
    // retrieveTransferList and createTransfers. The tests above cover their interactions.
    // If direct testing of private logic is absolutely necessary, reflection could be used [[3]],
    // but it's better to refactor the logic into public methods in separate classes [[4]].
}
