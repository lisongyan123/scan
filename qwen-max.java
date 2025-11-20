package com.hsbc.trade.transfer.service.impl;

import com.hsbc.trade.HTTPRequestHeaderConstants;
import com.hsbc.trade.common.AccountId;
import com.hsbc.trade.common.ResponseDetails;
import com.hsbc.trade.service.DuplicateSubmitPreventionService;
import com.hsbc.trade.service.RestClientService;
import com.hsbc.trade.service.impl.RetrieveCustomerProfilesServiceImpl;
import com.hsbc.trade.transfer.common.ActionRequestCode;
import com.hsbc.trade.transfer.common.ReceiverInfo;
import com.hsbc.trade.transfer.common.TransferActionCode;
import com.hsbc.trade.transfer.common.TransferListItemInfo;
import com.hsbc.trade.transfer.common.TransferSideCode;
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
import com.hsbc.trade.transfer.domain.cep.PartyContactResponseData;
import com.hsbc.trade.transfer.domain.cep.PartyNameResponse;
import com.hsbc.trade.transfer.domain.cep.PartyNameResponseData;
import com.hsbc.trade.transfer.domain.cep.PartyNameValue;
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
    private DuplicateSubmitPreventionService duplicateSubmitPreventionService;

    @Mock
    private TradeLimitServiceImpl tradeLimitService;

    private final String dummyToken = "<saml:Assertion xmlns:saml='http://www.hsbc.com/saas/assertion' xmlns:ds='http://www.w3.org/2000/09/xmldsig#' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' ID='id_9a9e4d35-7d80-4805-849f-ddb90a8b2c1f' IssueInstant='2025-08-07T01:25:13.837Z' Version='3.0'><saml:Issuer>https://www.hsbc.com/rbwm/dtp</saml:Issuer><ds:Signature><ds:SignedInfo><ds:CanonicalizationMethod Algorithm='http://www.w3.org/2001/10/xml-exc-c14n#'/><ds:SignatureMethod Algorithm='http://www.w3.org/2001/04/xmldsig-more#rsa-sha256'/><ds:Reference URI='#id_9a9e4d35-7d80-4805-849f-ddb90a8b2c1f'><ds:Transforms><ds:Transform Algorithm='http://www.w3.org/2000/09/xmldsig#enveloped-signature'/><ds:Transform Algorithm='http://www.w3.org/2001/10/xml-exc-c14n#'><ds:InclusiveNamespaces xmlns:ds='http://www.w3.org/2001/10/xml-exc-c14n#' PrefixList='#default saml ds xs xsi'/></ds:Transform></ds:Transforms><ds:DigestMethod Algorithm='http://www.w3.org/2001/04/xmlenc#sha256'/><ds:DigestValue>GLd2xpRi6DRAl81eH6NBRNzVWBlEL1zn5mWNpp16xCk=</ds:DigestValue></ds:Reference></ds:SignedInfo><ds:SignatureValue>IJQo3CpyjsDRTfXdnQGQnugNniIy56neA0eaLr87DITEauIPFNZHGCk6sb/Wp9PSIxoJNGpF4T5vVPWqUmv1fYasVtrukqodTgK2JD3NHhviDmTVGKzhZ2hnfjSewDRYeqHVURHMWY1EzltUhpZgO9u12i9+PPK4OJLFDR5Q4tZico3GfweUS7+Ds9wYssqgECZg3XayVg5w9ruSdxPIrcjU7aOe2sZFkge+I6cD2OWHC0K+u+PG+DD0UNmK9OnIY///lwgUdhbdSv0zdkUhOcHRKstuFIKhb4E8eZDogB5Sjeqya3EwJ8sIda99n+jug9IrDAjQIBTTnxtMfwq+gQ==</ds:SignatureValue></ds:Signature><saml:Subject><saml:NameID>HK00100718688801</saml:NameID></saml:Subject><saml:Conditions NotBefore='2025-08-07T01:25:12.837Z' NotOnOrAfter='2025-08-07T01:26:13.837Z'/><saml:AttributeStatement><saml:Attribute Name='GUID'><saml:AttributeValue>98b45150-5c73-11ea-8a50-0350565a170c</saml:AttributeValue></saml:Attribute><saml:Attribute Name='CAM'><saml:AttributeValue>30</saml:AttributeValue></saml:Attribute><saml:Attribute Name='KeyAlias'><saml:AttributeValue>E2E_TRUST_SAAS_AP01_BRTB1_ALIAS</saml:AttributeValue></saml:Attribute></saml:AttributeStatement></saml:Assertion>";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tradeTransferService, "tradeOnlineUrl", "https://dummy.trade");
        ReflectionTestUtils.setField(tradeTransferService, "customerAccountUrl", "https://dummy.accounts");
        ReflectionTestUtils.setField(tradeTransferService, "retrieveCustomerProfilesService", retrieveCustomerProfilesService);
        ReflectionTestUtils.setField(tradeTransferService, "sreValidationService", sreValidationService);
        ReflectionTestUtils.setField(tradeTransferService, "duplicateSubmitPreventionService", duplicateSubmitPreventionService);
        ReflectionTestUtils.setField(tradeTransferService, "tradeLimitService", tradeLimitService);
        lenient().when(retrieveCustomerProfilesService.getCIN(any())).thenReturn("dummy-cin");
    }

    @Test
    void retrieveTransferList() {
        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CUST123");

        RetrieveTransferListResponse response = new RetrieveTransferListResponse();
        RetrieveTransferListResponseData responseData = new RetrieveTransferListResponseData();
        responseData.setTransferLists(new ArrayList<>());
        response.setData(responseData);
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        response.setResponseDetails(responseDetails);

        CustomerAccounts customerAccounts = new CustomerAccounts();
        List<InvestmentAccount> accountList = new ArrayList<>();
        InvestmentAccount account = new InvestmentAccount();
        AccountId accountId = new AccountId();
        accountId.setAccountNumber("123456");
        account.setInvestmentAccountId(accountId);
        account.setChecksum("CHK123");
        accountList.add(account);
        customerAccounts.setInvestmentAccountList(accountList);

        PartyNameResponse nameResponse = new PartyNameResponse();
        PartyNameResponseData nameData = new PartyNameResponseData();
        PartyNameValue nameValue = new PartyNameValue();
        nameValue.setGivenName("John");
        nameValue.setLastName("Doe");
        nameValue.setCustomerChristianName("Christian");
        nameData.setName(nameValue);
        nameResponse.setData(nameData);

        PartyContactResponse contactResponse = new PartyContactResponse();
        PartyContactResponseData contactData = new PartyContactResponseData();
        contactData.setMobileNumber1("12345678");
        contactResponse.setData(contactData);

        when(restClientService.get(any(), any(), any(), anyInt(), anyBoolean())).thenReturn(response);
        when(restClientService.get(eq("https://dummy.accounts"), any(), eq(CustomerAccounts.class), anyInt(), anyBoolean())).thenReturn(customerAccounts);
        when(restClientService.get(contains("partyName"), any(), eq(PartyNameResponse.class), anyInt(), anyBoolean())).thenReturn(nameResponse);
        when(restClientService.get(contains("partyContact"), any(), eq(PartyContactResponse.class), anyInt(), anyBoolean())).thenReturn(contactResponse);
        when(e2ETrustTokenUtil.generateE2ETrustToken(anyString(), anyMap())).thenReturn("dummy-token");

        tradeTransferService.retrieveTransferList(sourceRequestHeader, "ACCEPTED", Collections.singletonList("CHK123"),
                "1", "PROD1", "PLAIN_TEXT");

        verify(restClientService).get(any(), any(), any(), anyInt(), anyBoolean());
    }

    @Test
    void retrieveTransferListEmptyAccountList() {
        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CUST123");

        RetrieveTransferListResponse response = new RetrieveTransferListResponse();
        RetrieveTransferListResponseData responseData = new RetrieveTransferListResponseData();
        responseData.setTransferLists(new ArrayList<>());
        response.setData(responseData);
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        response.setResponseDetails(responseDetails);

        CustomerAccounts customerAccounts = new CustomerAccounts(); // Empty list
        customerAccounts.setInvestmentAccountList(new ArrayList<>());

        PartyNameResponse nameResponse = new PartyNameResponse();
        PartyNameResponseData nameData = new PartyNameResponseData();
        PartyNameValue nameValue = new PartyNameValue();
        nameValue.setGivenName("John");
        nameValue.setLastName("Doe");
        nameValue.setCustomerChristianName("Christian");
        nameData.setName(nameValue);
        nameResponse.setData(nameData);

        PartyContactResponse contactResponse = new PartyContactResponse();
        PartyContactResponseData contactData = new PartyContactResponseData();
        contactData.setMobileNumber1("12345678");
        contactResponse.setData(contactData);

        when(restClientService.get(any(), any(), any(), anyInt(), anyBoolean())).thenReturn(response);
        when(restClientService.get(eq("https://dummy.accounts"), any(), eq(CustomerAccounts.class), anyInt(), anyBoolean())).thenReturn(customerAccounts);
        when(restClientService.get(contains("partyName"), any(), eq(PartyNameResponse.class), anyInt(), anyBoolean())).thenReturn(nameResponse);
        when(restClientService.get(contains("partyContact"), any(), eq(PartyContactResponse.class), anyInt(), anyBoolean())).thenReturn(contactResponse);
        when(e2ETrustTokenUtil.generateE2ETrustToken(anyString(), anyMap())).thenReturn("dummy-token");

        tradeTransferService.retrieveTransferList(sourceRequestHeader, "ACCEPTED", Collections.singletonList("CHK123"),
                "1", "PROD1", "PLAIN_TEXT");

        verify(restClientService).get(any(), any(), any(), anyInt(), anyBoolean());
    }

    @Test
    void retrieveTransferListNullAccountList() {
        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CUST123");

        RetrieveTransferListResponse response = new RetrieveTransferListResponse();
        RetrieveTransferListResponseData responseData = new RetrieveTransferListResponseData();
        responseData.setTransferLists(new ArrayList<>());
        response.setData(responseData);
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        response.setResponseDetails(responseDetails);

        CustomerAccounts customerAccounts = new CustomerAccounts(); // Null list
        customerAccounts.setInvestmentAccountList(null);

        PartyNameResponse nameResponse = new PartyNameResponse();
        PartyNameResponseData nameData = new PartyNameResponseData();
        PartyNameValue nameValue = new PartyNameValue();
        nameValue.setGivenName("John");
        nameValue.setLastName("Doe");
        nameValue.setCustomerChristianName("Christian");
        nameData.setName(nameValue);
        nameResponse.setData(nameData);

        PartyContactResponse contactResponse = new PartyContactResponse();
        PartyContactResponseData contactData = new PartyContactResponseData();
        contactData.setMobileNumber1("12345678");
        contactResponse.setData(contactData);

        when(restClientService.get(any(), any(), any(), anyInt(), anyBoolean())).thenReturn(response);
        when(restClientService.get(eq("https://dummy.accounts"), any(), eq(CustomerAccounts.class), anyInt(), anyBoolean())).thenReturn(customerAccounts);
        when(restClientService.get(contains("partyName"), any(), eq(PartyNameResponse.class), anyInt(), anyBoolean())).thenReturn(nameResponse);
        when(restClientService.get(contains("partyContact"), any(), eq(PartyContactResponse.class), anyInt(), anyBoolean())).thenReturn(contactResponse);
        when(e2ETrustTokenUtil.generateE2ETrustToken(anyString(), anyMap())).thenReturn("dummy-token");

        tradeTransferService.retrieveTransferList(sourceRequestHeader, "ACCEPTED", Collections.singletonList("CHK123"),
                "1", "PROD1", "PLAIN_TEXT");

        verify(restClientService).get(any(), any(), any(), anyInt(), anyBoolean());
    }

    @Test
    void retrieveTransferDetail() {
        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CUST123");

        RetrieveTransferDetailResponse response = new RetrieveTransferDetailResponse();
        RetrieveTransferDetailResponseData responseData = new RetrieveTransferDetailResponseData();
        responseData.setInvestmentAccount(new com.hsbc.trade.transfer.domain.account.InvestmentAccount());
        responseData.getInvestmentAccount().setAccountNumber("123456");
        response.setData(responseData);
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        response.setResponseDetails(responseDetails);

        CustomerAccounts customerAccounts = new CustomerAccounts();
        List<InvestmentAccount> accountList = new ArrayList<>();
        InvestmentAccount account = new InvestmentAccount();
        AccountId accountId = new AccountId();
        accountId.setAccountNumber("123456");
        account.setInvestmentAccountId(accountId);
        account.setChecksum("CHK123");
        accountList.add(account);
        customerAccounts.setInvestmentAccountList(accountList);

        PartyNameResponse nameResponse = new PartyNameResponse();
        PartyNameResponseData nameData = new PartyNameResponseData();
        PartyNameValue nameValue = new PartyNameValue();
        nameValue.setGivenName("John");
        nameValue.setLastName("Doe");
        nameValue.setCustomerChristianName("Christian");
        nameData.setName(nameValue);
        nameResponse.setData(nameData);

        PartyContactResponse contactResponse = new PartyContactResponse();
        PartyContactResponseData contactData = new PartyContactResponseData();
        contactData.setMobileNumber1("12345678");
        contactResponse.setData(contactData);

        when(restClientService.get(any(), any(), any(), anyInt(), anyBoolean())).thenReturn(response);
        when(restClientService.get(eq("https://dummy.accounts"), any(), eq(CustomerAccounts.class), anyInt(), anyBoolean())).thenReturn(customerAccounts);
        when(restClientService.get(contains("partyName"), any(), eq(PartyNameResponse.class), anyInt(), anyBoolean())).thenReturn(nameResponse);
        when(restClientService.get(contains("partyContact"), any(), eq(PartyContactResponse.class), anyInt(), anyBoolean())).thenReturn(contactResponse);
        when(e2ETrustTokenUtil.generateE2ETrustToken(anyString(), anyMap())).thenReturn("dummy-token");

        tradeTransferService.retrieveTransferDetail(sourceRequestHeader, "REF123");

        verify(restClientService).get(any(), any(), any(), anyInt(), anyBoolean());
    }

    @Test
    void retrieveTransferDetailNullInvestmentAccount() {
        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CUST123");

        RetrieveTransferDetailResponse response = new RetrieveTransferDetailResponse();
        RetrieveTransferDetailResponseData responseData = new RetrieveTransferDetailResponseData();
        responseData.setInvestmentAccount(null); // Null investment account
        response.setData(responseData);
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        response.setResponseDetails(responseDetails);

        when(restClientService.get(any(), any(), any(), anyInt(), anyBoolean())).thenReturn(response);

        RetrieveTransferDetailResponse result = tradeTransferService.retrieveTransferDetail(sourceRequestHeader, "REF123");

        verify(restClientService).get(any(), any(), any(), anyInt(), anyBoolean());
        assertNull(result.getData().getInvestmentAccount());
    }

    @Test
    void retrieveTransferDetailAccountNotFound() {
        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CUST123");

        RetrieveTransferDetailResponse response = new RetrieveTransferDetailResponse();
        RetrieveTransferDetailResponseData responseData = new RetrieveTransferDetailResponseData();
        responseData.setInvestmentAccount(new com.hsbc.trade.transfer.domain.account.InvestmentAccount());
        responseData.getInvestmentAccount().setAccountNumber("999999"); // Account number not in list
        response.setData(responseData);
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        response.setResponseDetails(responseDetails);

        CustomerAccounts customerAccounts = new CustomerAccounts();
        List<InvestmentAccount> accountList = new ArrayList<>();
        InvestmentAccount account = new InvestmentAccount();
        AccountId accountId = new AccountId();
        accountId.setAccountNumber("123456");
        account.setInvestmentAccountId(accountId);
        account.setChecksum("CHK123");
        accountList.add(account);
        customerAccounts.setInvestmentAccountList(accountList);

        PartyNameResponse nameResponse = new PartyNameResponse();
        PartyNameResponseData nameData = new PartyNameResponseData();
        PartyNameValue nameValue = new PartyNameValue();
        nameValue.setGivenName("John");
        nameValue.setLastName("Doe");
        nameValue.setCustomerChristianName("Christian");
        nameData.setName(nameValue);
        nameResponse.setData(nameData);

        PartyContactResponse contactResponse = new PartyContactResponse();
        PartyContactResponseData contactData = new PartyContactResponseData();
        contactData.setMobileNumber1("12345678");
        contactResponse.setData(contactData);

        when(restClientService.get(any(), any(), any(), anyInt(), anyBoolean())).thenReturn(response);
        when(restClientService.get(eq("https://dummy.accounts"), any(), eq(CustomerAccounts.class), anyInt(), anyBoolean())).thenReturn(customerAccounts);
        when(restClientService.get(contains("partyName"), any(), eq(PartyNameResponse.class), anyInt(), anyBoolean())).thenReturn(nameResponse);
        when(restClientService.get(contains("partyContact"), any(), eq(PartyContactResponse.class), anyInt(), anyBoolean())).thenReturn(contactResponse);
        when(e2ETrustTokenUtil.generateE2ETrustToken(anyString(), anyMap())).thenReturn("dummy-token");

        assertThrows(BadRequestException.class, () -> tradeTransferService.retrieveTransferDetail(sourceRequestHeader, "REF123"));
    }

    @Test
    void retrieveTransferDetailRestClientException() {
        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CUST123");

        when(restClientService.get(any(), any(), any(), anyInt(), anyBoolean())).thenThrow(new RuntimeException("Network Error"));

        assertThrows(InternalServerErrorException.class, () -> tradeTransferService.retrieveTransferDetail(sourceRequestHeader, "REF123"));
    }

    @Test
    void createTransfers() {
        InvestmentAccountId investmentAccountId = new InvestmentAccountId();
        investmentAccountId.setCountryAccountCode("HK");
        investmentAccountId.setGroupMemberAccountCode("HBAP");
        investmentAccountId.setAccountNumber("123456");
        investmentAccountId.setAccountProductTypeCode("SAV");
        investmentAccountId.setAccountTypeCode("01");
        investmentAccountId.setAccountCurrencyCode("HKD");

        InvestmentAccountIdList accountIdList = new InvestmentAccountIdList();
        accountIdList.setAccountId(investmentAccountId);

        RetrieveCustomerAccountsIdListResponse response = new RetrieveCustomerAccountsIdListResponse();
        response.setAccountIdList(List.of(accountIdList));

        lenient().when(restClientService.get(anyString(), any(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(response);

        CreateTransferRequest request = new CreateTransferRequest();
        CreateTransferRequestData data = new CreateTransferRequestData();
        request.setData(data);
        request.getData().setSenderInvestmentAccountChecksumIdentifier("12345");
        request.getData().setActionRequestCode(ActionRequestCode.D); // D operation
        request.getData().setRequestPriceValue(BigDecimal.valueOf(1000.00)); // Price for D operation
        ReceiverInfo receiver = new ReceiverInfo();
        receiver.setTransferQuantity(BigDecimal.valueOf(100));
        receiver.setReceiverCustomerNumber("12345");
        List<ReceiverInfo> receivers = new ArrayList<>();
        receivers.add(receiver);
        request.getData().setReceiverLists(receivers);
        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CUST123");

        CreateTransferResponse mockCreateTransferResponse = new CreateTransferResponse();
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        mockCreateTransferResponse.setResponseDetails(responseDetails);

        RuleResponse sreResponse = new RuleResponse();
        ResponseDetails sreDetails = new ResponseDetails();
        sreDetails.setResponseCodeNumber(0);
        sreResponse.setResponseDetails(sreDetails);

        PartyNameResponse nameResponse = new PartyNameResponse();
        PartyNameResponseData nameData = new PartyNameResponseData();
        PartyNameValue nameValue = new PartyNameValue();
        nameValue.setGivenName("John");
        nameValue.setLastName("Doe");
        nameValue.setCustomerChristianName("Christian");
        nameData.setName(nameValue);
        nameResponse.setData(nameData);

        GoldPriceResponse goldPriceResponse = new GoldPriceResponse();
        GoldPriceResponseData goldPriceData = new GoldPriceResponseData();
        goldPriceData.setGoldPriceAmount(BigDecimal.valueOf(1200.50));
        goldPriceData.setPublishTime("2025-08-07T01:25:13.837Z");
        goldPriceResponse.setData(goldPriceData);

        RetrieveTransferLimitResponse limitResponse = new RetrieveTransferLimitResponse();
        RetrieveTransferLimitResponseData limitData = new RetrieveTransferLimitResponseData();
        limitData.setAvailableTodayAmount(BigDecimal.valueOf(100000));
        limitData.setMaxDailyLimitedAmount(BigDecimal.valueOf(100000));
        limitData.setAvailableMonthToDateAmount(BigDecimal.valueOf(1000000));
        limitData.setMaxMonthlyLimitedAmount(BigDecimal.valueOf(1000000));
        limitData.setAvailableYearToDateAmount(BigDecimal.valueOf(10000000));
        limitData.setMaxYearlyLimitedAmount(BigDecimal.valueOf(10000000));
        limitResponse.setData(limitData);

        lenient().when(sreValidationService.callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(sreResponse);
        when(restClientService.get(contains("partyName"), any(), eq(PartyNameResponse.class), anyInt(), anyBoolean())).thenReturn(nameResponse);
        when(restClientService.get(contains("goldPrice"), any(), eq(GoldPriceResponse.class), anyInt(), anyBoolean())).thenReturn(goldPriceResponse);
        when(restClientService.post(anyString(), any(), any(), eq(CreateTransferResponse.class), anyInt(), anyBoolean())).thenReturn(mockCreateTransferResponse);
        when(tradeLimitService.retrieveLimitations(anyMap())).thenReturn(limitResponse);
        when(duplicateSubmitPreventionService.generateUniqueKey()).thenReturn("UNIQUE_KEY_123");

        tradeTransferService.createTransfers(sourceRequestHeader, request);

        verify(restClientService).post(anyString(), any(), any(), eq(CreateTransferResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void createTransfersLimitExceededException() {
        InvestmentAccountId investmentAccountId = new InvestmentAccountId();
        investmentAccountId.setCountryAccountCode("HK");
        investmentAccountId.setGroupMemberAccountCode("HBAP");
        investmentAccountId.setAccountNumber("123456");
        investmentAccountId.setAccountProductTypeCode("SAV");
        investmentAccountId.setAccountTypeCode("01");
        investmentAccountId.setAccountCurrencyCode("HKD");

        InvestmentAccountIdList accountIdList = new InvestmentAccountIdList();
        accountIdList.setAccountId(investmentAccountId);

        RetrieveCustomerAccountsIdListResponse response = new RetrieveCustomerAccountsIdListResponse();
        response.setAccountIdList(List.of(accountIdList));

        lenient().when(restClientService.get(anyString(), any(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(response);

        CreateTransferRequest request = new CreateTransferRequest();
        CreateTransferRequestData data = new CreateTransferRequestData();
        request.setData(data);
        request.getData().setSenderInvestmentAccountChecksumIdentifier("12345");
        request.getData().setRequestPriceValue(BigDecimal.valueOf(1000.00));
        ReceiverInfo receiver = new ReceiverInfo();
        receiver.setTransferQuantity(BigDecimal.valueOf(100)); // Total amount = 1000.00 * 100 = 100,000
        receiver.setReceiverCustomerNumber("12345");
        List<ReceiverInfo> receivers = new ArrayList<>();
        receivers.add(receiver);
        request.getData().setReceiverLists(receivers);
        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CUST123");

        RuleResponse sreResponse = new RuleResponse();
        ResponseDetails sreDetails = new ResponseDetails();
        sreDetails.setResponseCodeNumber(0);
        sreResponse.setResponseDetails(sreDetails);

        RetrieveTransferLimitResponse limitResponse = new RetrieveTransferLimitResponse();
        RetrieveTransferLimitResponseData limitData = new RetrieveTransferLimitResponseData();
        limitData.setAvailableTodayAmount(BigDecimal.valueOf(50000)); // Less than required amount
        limitData.setMaxDailyLimitedAmount(BigDecimal.valueOf(100000));
        limitData.setAvailableMonthToDateAmount(BigDecimal.valueOf(500000));
        limitData.setMaxMonthlyLimitedAmount(BigDecimal.valueOf(1000000));
        limitData.setAvailableYearToDateAmount(BigDecimal.valueOf(5000000));
        limitData.setMaxYearlyLimitedAmount(BigDecimal.valueOf(10000000));
        limitResponse.setData(limitData);

        lenient().when(sreValidationService.callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(sreResponse);
        when(tradeLimitService.retrieveLimitations(anyMap())).thenReturn(limitResponse);

        assertThrows(TransferLimitExceededException.class, () -> tradeTransferService.createTransfers(sourceRequestHeader, request));
    }

    @Test
    void createTransfersNullRequestPriceValue() {
        InvestmentAccountId investmentAccountId = new InvestmentAccountId();
        investmentAccountId.setCountryAccountCode("HK");
        investmentAccountId.setGroupMemberAccountCode("HBAP");
        investmentAccountId.setAccountNumber("123456");
        investmentAccountId.setAccountProductTypeCode("SAV");
        investmentAccountId.setAccountTypeCode("01");
        investmentAccountId.setAccountCurrencyCode("HKD");

        InvestmentAccountIdList accountIdList = new InvestmentAccountIdList();
        accountIdList.setAccountId(investmentAccountId);

        RetrieveCustomerAccountsIdListResponse response = new RetrieveCustomerAccountsIdListResponse();
        response.setAccountIdList(List.of(accountIdList));

        lenient().when(restClientService.get(anyString(), any(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(response);

        CreateTransferRequest request = new CreateTransferRequest();
        CreateTransferRequestData data = new CreateTransferRequestData();
        request.setData(data);
        request.getData().setSenderInvestmentAccountChecksumIdentifier("12345");
        request.getData().setActionRequestCode(ActionRequestCode.C); // C operation, no price update
        request.getData().setRequestPriceValue(null); // Null price
        ReceiverInfo receiver = new ReceiverInfo();
        receiver.setTransferQuantity(BigDecimal.valueOf(100));
        receiver.setReceiverCustomerNumber("12345");
        List<ReceiverInfo> receivers = new ArrayList<>();
        receivers.add(receiver);
        request.getData().setReceiverLists(receivers);
        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CUST123");

        CreateTransferResponse mockCreateTransferResponse = new CreateTransferResponse();
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        mockCreateTransferResponse.setResponseDetails(responseDetails);

        RuleResponse sreResponse = new RuleResponse();
        ResponseDetails sreDetails = new ResponseDetails();
        sreDetails.setResponseCodeNumber(0);
        sreResponse.setResponseDetails(sreDetails);

        PartyNameResponse nameResponse = new PartyNameResponse();
        PartyNameResponseData nameData = new PartyNameResponseData();
        PartyNameValue nameValue = new PartyNameValue();
        nameValue.setGivenName("John");
        nameValue.setLastName("Doe");
        nameValue.setCustomerChristianName("Christian");
        nameData.setName(nameValue);
        nameResponse.setData(nameData);

        RetrieveTransferLimitResponse limitResponse = new RetrieveTransferLimitResponse();
        RetrieveTransferLimitResponseData limitData = new RetrieveTransferLimitResponseData();
        limitData.setAvailableTodayAmount(BigDecimal.valueOf(100000));
        limitData.setMaxDailyLimitedAmount(BigDecimal.valueOf(100000));
        limitData.setAvailableMonthToDateAmount(BigDecimal.valueOf(1000000));
        limitData.setMaxMonthlyLimitedAmount(BigDecimal.valueOf(1000000));
        limitData.setAvailableYearToDateAmount(BigDecimal.valueOf(10000000));
        limitData.setMaxYearlyLimitedAmount(BigDecimal.valueOf(10000000));
        limitResponse.setData(limitData);

        lenient().when(sreValidationService.callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(sreResponse);
        when(restClientService.get(contains("partyName"), any(), eq(PartyNameResponse.class), anyInt(), anyBoolean())).thenReturn(nameResponse);
        when(restClientService.post(anyString(), any(), any(), eq(CreateTransferResponse.class), anyInt(), anyBoolean())).thenReturn(mockCreateTransferResponse);
        when(tradeLimitService.retrieveLimitations(anyMap())).thenReturn(limitResponse);

        tradeTransferService.createTransfers(sourceRequestHeader, request);

        verify(restClientService).post(anyString(), any(), any(), eq(CreateTransferResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void createTransfersNullReceivers() {
        InvestmentAccountId investmentAccountId = new InvestmentAccountId();
        investmentAccountId.setCountryAccountCode("HK");
        investmentAccountId.setGroupMemberAccountCode("HBAP");
        investmentAccountId.setAccountNumber("123456");
        investmentAccountId.setAccountProductTypeCode("SAV");
        investmentAccountId.setAccountTypeCode("01");
        investmentAccountId.setAccountCurrencyCode("HKD");

        InvestmentAccountIdList accountIdList = new InvestmentAccountIdList();
        accountIdList.setAccountId(investmentAccountId);

        RetrieveCustomerAccountsIdListResponse response = new RetrieveCustomerAccountsIdListResponse();
        response.setAccountIdList(List.of(accountIdList));

        lenient().when(restClientService.get(anyString(), any(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(response);

        CreateTransferRequest request = new CreateTransferRequest();
        CreateTransferRequestData data = new CreateTransferRequestData();
        request.setData(data);
        request.getData().setSenderInvestmentAccountChecksumIdentifier("12345");
        request.getData().setRequestPriceValue(BigDecimal.valueOf(1000.00));
        request.getData().setReceiverLists(null); // Null receivers
        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "CUST123");

        CreateTransferResponse mockCreateTransferResponse = new CreateTransferResponse();
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        mockCreateTransferResponse.setResponseDetails(responseDetails);

        PartyNameResponse nameResponse = new PartyNameResponse();
        PartyNameResponseData nameData = new PartyNameResponseData();
        PartyNameValue nameValue = new PartyNameValue();
        nameValue.setGivenName("John");
        nameValue.setLastName("Doe");
        nameValue.setCustomerChristianName("Christian");
        nameData.setName(nameValue);
        nameResponse.setData(nameData);

        RetrieveTransferLimitResponse limitResponse = new RetrieveTransferLimitResponse();
        RetrieveTransferLimitResponseData limitData = new RetrieveTransferLimitResponseData();
        limitData.setAvailableTodayAmount(BigDecimal.valueOf(100000));
        limitData.setMaxDailyLimitedAmount(BigDecimal.valueOf(100000));
        limitData.setAvailableMonthToDateAmount(BigDecimal.valueOf(1000000));
        limitData.setMaxMonthlyLimitedAmount(BigDecimal.valueOf(1000000));
        limitData.setAvailableYearToDateAmount(BigDecimal.valueOf(10000000));
        limitData.setMaxYearlyLimitedAmount(BigDecimal.valueOf(10000000));
        limitResponse.setData(limitData);

        when(restClientService.get(contains("partyName"), any(), eq(PartyNameResponse.class), anyInt(), anyBoolean())).thenReturn(nameResponse);
        when(restClientService.post(anyString(), any(), any(), eq(CreateTransferResponse.class), anyInt(), anyBoolean())).thenReturn(mockCreateTransferResponse);
        when(tradeLimitService.retrieveLimitations(anyMap())).thenReturn(limitResponse);

        tradeTransferService.createTransfers(sourceRequestHeader, request);

        verify(restClientService).post(anyString(), any(), any(), eq(CreateTransferResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void modifyTransfersAcceptAction() {
        InvestmentAccountId investmentAccountId = new InvestmentAccountId();
        investmentAccountId.setCountryAccountCode("HK");
        investmentAccountId.setGroupMemberAccountCode("HBAP");
        investmentAccountId.setAccountNumber("123456");
        investmentAccountId.setAccountProductTypeCode("SAV");
        investmentAccountId.setAccountTypeCode("01");
        investmentAccountId.setAccountCurrencyCode("HKD");

        InvestmentAccountIdList accountIdList = new InvestmentAccountIdList();
        accountIdList.setAccountId(investmentAccountId);

        RetrieveCustomerAccountsIdListResponse response = new RetrieveCustomerAccountsIdListResponse();
        response.setAccountIdList(List.of(accountIdList));
        RuleResponse sreResponse = new RuleResponse();
        ResponseDetails sreDetails = new ResponseDetails();
        sreDetails.setResponseCodeNumber(0);
        sreResponse.setResponseDetails(sreDetails);

        when(restClientService.get(anyString(), any(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(response);
        lenient().when(sreValidationService.callSreForTransferValidation(anyString(), anyString(),anyString(), anyMap()))
                .thenReturn(sreResponse);

        UpdateTransferRequest request = new UpdateTransferRequest();
        UpdateTransferRequestData data = new UpdateTransferRequestData();
        request.setData(data);
        request.getData().setReceiverInvestmentAccountChecksumIdentifier("12345");

        TransferActionCode actionCode = TransferActionCode.A; // Accept
        request.getData().setTransferActionCode(actionCode);
        request.getData().setReceiverCustomerInternalNumber("RECEIVER_CIN");

        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "SENDER_CIN");

        UpdateTransferResponse mockUpdateTransferResponse = new UpdateTransferResponse();
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        mockUpdateTransferResponse.setResponseDetails(responseDetails);

        GoldPriceResponse goldPriceResponse = new GoldPriceResponse();
        GoldPriceResponseData goldPriceData = new GoldPriceResponseData();
        goldPriceData.setGoldPriceAmount(BigDecimal.valueOf(1200.50));
        goldPriceData.setPublishTime("2025-08-07T01:25:13.837Z");
        goldPriceResponse.setData(goldPriceData);

        when(restClientService.get(contains("goldPrice"), any(), eq(GoldPriceResponse.class), anyInt(), anyBoolean())).thenReturn(goldPriceResponse);
        when(restClientService.put(anyString(), any(), any(), eq(UpdateTransferResponse.class), anyInt(), anyBoolean())).thenReturn(mockUpdateTransferResponse);

        tradeTransferService.modifyTransfers(sourceRequestHeader, request);

        verify(restClientService).put(anyString(), any(), any(), eq(UpdateTransferResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void modifyTransfersRejectAction() {
        InvestmentAccountId investmentAccountId = new InvestmentAccountId();
        investmentAccountId.setCountryAccountCode("HK");
        investmentAccountId.setGroupMemberAccountCode("HBAP");
        investmentAccountId.setAccountNumber("123456");
        investmentAccountId.setAccountProductTypeCode("SAV");
        investmentAccountId.setAccountTypeCode("01");
        investmentAccountId.setAccountCurrencyCode("HKD");

        InvestmentAccountIdList accountIdList = new InvestmentAccountIdList();
        accountIdList.setAccountId(investmentAccountId);

        RetrieveCustomerAccountsIdListResponse response = new RetrieveCustomerAccountsIdListResponse();
        response.setAccountIdList(List.of(accountIdList));

        when(restClientService.get(anyString(), any(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(response);

        UpdateTransferRequest request = new UpdateTransferRequest();
        UpdateTransferRequestData data = new UpdateTransferRequestData();
        request.setData(data);
        request.getData().setReceiverInvestmentAccountChecksumIdentifier("12345");

        TransferActionCode actionCode = TransferActionCode.R; // Reject
        request.getData().setTransferActionCode(actionCode);
        request.getData().setReceiverCustomerInternalNumber("RECEIVER_CIN");

        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "SENDER_CIN");

        UpdateTransferResponse mockUpdateTransferResponse = new UpdateTransferResponse();
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        mockUpdateTransferResponse.setResponseDetails(responseDetails);

        when(restClientService.put(anyString(), any(), any(), eq(UpdateTransferResponse.class), anyInt(), anyBoolean())).thenReturn(mockUpdateTransferResponse);

        tradeTransferService.modifyTransfers(sourceRequestHeader, request);

        verify(restClientService).put(anyString(), any(), any(), eq(UpdateTransferResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void modifyTransfersOtherAction() {
        InvestmentAccountId investmentAccountId = new InvestmentAccountId();
        investmentAccountId.setCountryAccountCode("HK");
        investmentAccountId.setGroupMemberAccountCode("HBAP");
        investmentAccountId.setAccountNumber("123456");
        investmentAccountId.setAccountProductTypeCode("SAV");
        investmentAccountId.setAccountTypeCode("01");
        investmentAccountId.setAccountCurrencyCode("HKD");

        InvestmentAccountIdList accountIdList = new InvestmentAccountIdList();
        accountIdList.setAccountId(investmentAccountId);

        RetrieveCustomerAccountsIdListResponse response = new RetrieveCustomerAccountsIdListResponse();
        response.setAccountIdList(List.of(accountIdList));

        when(restClientService.get(anyString(), any(), eq(RetrieveCustomerAccountsIdListResponse.class), anyInt(), anyBoolean()))
                .thenReturn(response);

        UpdateTransferRequest request = new UpdateTransferRequest();
        UpdateTransferRequestData data = new UpdateTransferRequestData();
        request.setData(data);
        request.getData().setReceiverInvestmentAccountChecksumIdentifier("12345");

        TransferActionCode actionCode = TransferActionCode.C; // Other action code, not R or A
        request.getData().setTransferActionCode(actionCode);
        request.getData().setReceiverCustomerInternalNumber("RECEIVER_CIN");

        Map<String, String> sourceRequestHeader = new HashMap<>();
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_GROUP_MEMBER, "HBAP");
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_SAML3, dummyToken);
        sourceRequestHeader.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "SENDER_CIN");

        UpdateTransferResponse mockUpdateTransferResponse = new UpdateTransferResponse();
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        mockUpdateTransferResponse.setResponseDetails(responseDetails);

        when(restClientService.put(anyString(), any(), any(), eq(UpdateTransferResponse.class), anyInt(), anyBoolean())).thenReturn(mockUpdateTransferResponse);

        tradeTransferService.modifyTransfers(sourceRequestHeader, request);

        verify(restClientService).put(anyString(), any(), any(), eq(UpdateTransferResponse.class), anyInt(), anyBoolean());
    }

    @Test
    void extractAccountIdMap() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("extractAccountIdMap", CustomerAccounts.class);
        method.setAccessible(true);

        CustomerAccounts customerAccounts = new CustomerAccounts();
        List<InvestmentAccount> accountList = new ArrayList<>();
        InvestmentAccount account = new InvestmentAccount();
        AccountId accountId = new AccountId();
        accountId.setCountryAccountCode("HK");
        accountId.setGroupMemberAccountCode("HBAP");
        accountId.setAccountNumber("123456");
        accountId.setAccountProductTypeCode("SAV");
        accountId.setAccountTypeCode("01");
        accountId.setAccountCurrencyCode("HKD");
        account.setInvestmentAccountId(accountId);
        account.setChecksum("CHK123");
        accountList.add(account);
        customerAccounts.setInvestmentAccountList(accountList);

        Map<String, String> result = (Map<String, String>) method.invoke(tradeTransferService, customerAccounts);

        assertEquals(1, result.size());
        assertTrue(result.containsKey("CHK123"));
        assertTrue(result.containsValue("countryAccountCode=HK;groupMemberAccountCode=HBAP;accountNumber=123456;accountProductTypeCode=SAV;accountTypeCode=01;accountCurrencyCode=HKD"));
    }

    @Test
    void extractAccountIdMapNullList() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("extractAccountIdMap", CustomerAccounts.class);
        method.setAccessible(true);

        CustomerAccounts customerAccounts = new CustomerAccounts();
        customerAccounts.setInvestmentAccountList(null); // Null list

        Map<String, String> result = (Map<String, String>) method.invoke(tradeTransferService, customerAccounts);

        assertTrue(result.isEmpty());
    }

    @Test
    void extractAccountIdMapEmptyList() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("extractAccountIdMap", CustomerAccounts.class);
        method.setAccessible(true);

        CustomerAccounts customerAccounts = new CustomerAccounts();
        customerAccounts.setInvestmentAccountList(new ArrayList<>()); // Empty list

        Map<String, String> result = (Map<String, String>) method.invoke(tradeTransferService, customerAccounts);

        assertTrue(result.isEmpty());
    }

    @Test
    void extractAccountIdMapNullAccountInList() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("extractAccountIdMap", CustomerAccounts.class);
        method.setAccessible(true);

        CustomerAccounts customerAccounts = new CustomerAccounts();
        List<InvestmentAccount> accountList = new ArrayList<>();
        accountList.add(null); // Null account in list
        customerAccounts.setInvestmentAccountList(accountList);

        Map<String, String> result = (Map<String, String>) method.invoke(tradeTransferService, customerAccounts);

        assertTrue(result.isEmpty());
    }

    @Test
    void extractAccountIdMapNullAccountId() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("extractAccountIdMap", CustomerAccounts.class);
        method.setAccessible(true);

        CustomerAccounts customerAccounts = new CustomerAccounts();
        List<InvestmentAccount> accountList = new ArrayList<>();
        InvestmentAccount account = new InvestmentAccount();
        account.setInvestmentAccountId(null); // Null account ID
        account.setChecksum("CHK123");
        accountList.add(account);
        customerAccounts.setInvestmentAccountList(accountList);

        Map<String, String> result = (Map<String, String>) method.invoke(tradeTransferService, customerAccounts);

        assertTrue(result.isEmpty());
    }

    @Test
    void maskNamesInResponseList() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("maskNamesInResponse", RetrieveTransferListResponseData.class);
        method.setAccessible(true);

        RetrieveTransferListResponseData responseData = new RetrieveTransferListResponseData();
        List<TransferListItemInfo> transferList = new ArrayList<>();
        TransferListItemInfo item = new TransferListItemInfo();
        item.setTransferSideCode(TransferSideCode.RECEIVER);
        item.setSenderCustomerFirstName("John");
        item.setSenderCustomerMiddleName("Christian");
        transferList.add(item);
        responseData.setTransferLists(transferList);

        method.invoke(tradeTransferService, responseData);

        assertEquals("J***", responseData.getTransferLists().get(0).getSenderCustomerFirstName());
        assertEquals("C*******", responseData.getTransferLists().get(0).getSenderCustomerMiddleName());
    }

    @Test
    void maskNamesInResponseListNullData() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("maskNamesInResponse", RetrieveTransferListResponseData.class);
        method.setAccessible(true);

        method.invoke(tradeTransferService, null); // Null data object

        // Should not throw an exception
    }

    @Test
    void maskNamesInResponseListNullList() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("maskNamesInResponse", RetrieveTransferListResponseData.class);
        method.setAccessible(true);

        RetrieveTransferListResponseData responseData = new RetrieveTransferListResponseData();
        responseData.setTransferLists(null); // Null list

        method.invoke(tradeTransferService, responseData);

        // Should not throw an exception
    }

    @Test
    void maskNamesInResponseDetail() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("maskNamesInResponse", RetrieveTransferDetailResponseData.class);
        method.setAccessible(true);

        RetrieveTransferDetailResponseData responseData = new RetrieveTransferDetailResponseData();
        responseData.setTransferSideCode(TransferSideCode.SENDER);
        responseData.setIsReceiverBankCustomer("Y");
        responseData.setReceiverCustomerFirstName("Jane");
        responseData.setReceiverCustomerMiddleName("Marie");

        method.invoke(tradeTransferService, responseData);

        assertEquals("J***", responseData.getReceiverCustomerFirstName());
        assertEquals("M****", responseData.getReceiverCustomerMiddleName());
    }

    @Test
    void maskNamesInResponseDetailNullData() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("maskNamesInResponse", RetrieveTransferDetailResponseData.class);
        method.setAccessible(true);

        method.invoke(tradeTransferService, null); // Null data object

        // Should not throw an exception
    }

    @Test
    void maskFirstNameAndMiddleName() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("maskFirstNameAndMiddleName", Object.class, String.class, String.class);
        method.setAccessible(true);

        RetrieveTransferListResponseData responseData = new RetrieveTransferListResponseData();
        List<TransferListItemInfo> transferList = new ArrayList<>();
        TransferListItemInfo item = new TransferListItemInfo();
        item.setSenderCustomerFirstName("John");
        item.setSenderCustomerMiddleName("Christian");
        transferList.add(item);
        responseData.setTransferLists(transferList);

        method.invoke(tradeTransferService, responseData.getTransferLists().get(0), "senderCustomerFirstName", "senderCustomerMiddleName");

        assertEquals("J***", responseData.getTransferLists().get(0).getSenderCustomerFirstName());
        assertEquals("C*******", responseData.getTransferLists().get(0).getSenderCustomerMiddleName());
    }

    @Test
    void maskFirstNameAndMiddleNameEmptyName() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("maskFirstNameAndMiddleName", Object.class, String.class, String.class);
        method.setAccessible(true);

        RetrieveTransferListResponseData responseData = new RetrieveTransferListResponseData();
        List<TransferListItemInfo> transferList = new ArrayList<>();
        TransferListItemInfo item = new TransferListItemInfo();
        item.setSenderCustomerFirstName(""); // Empty name
        item.setSenderCustomerMiddleName("Christian");
        transferList.add(item);
        responseData.setTransferLists(transferList);

        method.invoke(tradeTransferService, responseData.getTransferLists().get(0), "senderCustomerFirstName", "senderCustomerMiddleName");

        assertEquals("", responseData.getTransferLists().get(0).getSenderCustomerFirstName()); // Should remain empty
        assertEquals("C*******", responseData.getTransferLists().get(0).getSenderCustomerMiddleName());
    }

    @Test
    void maskFirstNameAndMiddleNameNullName() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("maskFirstNameAndMiddleName", Object.class, String.class, String.class);
        method.setAccessible(true);

        RetrieveTransferListResponseData responseData = new RetrieveTransferListResponseData();
        List<TransferListItemInfo> transferList = new ArrayList<>();
        TransferListItemInfo item = new TransferListItemInfo();
        item.setSenderCustomerFirstName(null); // Null name
        item.setSenderCustomerMiddleName("Christian");
        transferList.add(item);
        responseData.setTransferLists(transferList);

        method.invoke(tradeTransferService, responseData.getTransferLists().get(0), "senderCustomerFirstName", "senderCustomerMiddleName");

        assertNull(responseData.getTransferLists().get(0).getSenderCustomerFirstName()); // Should remain null
        assertEquals("C*******", responseData.getTransferLists().get(0).getSenderCustomerMiddleName());
    }

    @Test
    void capitalize() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("capitalize", String.class);
        method.setAccessible(true);

        assertEquals("Test", method.invoke(tradeTransferService, "test"));
        assertEquals("A", method.invoke(tradeTransferService, "a"));
        assertEquals("", method.invoke(tradeTransferService, ""));
        assertNull(method.invoke(tradeTransferService, null));
    }

    @Test
    void validateTransferLimits() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("validateTransferLimits", BigDecimal.class, RetrieveTransferLimitResponse.class);
        method.setAccessible(true);

        RetrieveTransferLimitResponse limitResponse = new RetrieveTransferLimitResponse();
        RetrieveTransferLimitResponseData limitData = new RetrieveTransferLimitResponseData();
        limitData.setAvailableTodayAmount(BigDecimal.valueOf(100000));
        limitData.setMaxDailyLimitedAmount(BigDecimal.valueOf(100000));
        limitData.setAvailableMonthToDateAmount(BigDecimal.valueOf(1000000));
        limitData.setMaxMonthlyLimitedAmount(BigDecimal.valueOf(1000000));
        limitData.setAvailableYearToDateAmount(BigDecimal.valueOf(10000000));
        limitData.setMaxYearlyLimitedAmount(BigDecimal.valueOf(10000000));
        limitResponse.setData(limitData);

        // Should not throw exception as amount is less than limits
        method.invoke(tradeTransferService, BigDecimal.valueOf(50000), limitResponse);
    }

    @Test
    void validateTransferLimitsDailyExceeded() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("validateTransferLimits", BigDecimal.class, RetrieveTransferLimitResponse.class);
        method.setAccessible(true);

        RetrieveTransferLimitResponse limitResponse = new RetrieveTransferLimitResponse();
        RetrieveTransferLimitResponseData limitData = new RetrieveTransferLimitResponseData();
        limitData.setAvailableTodayAmount(BigDecimal.valueOf(40000)); // Less than required amount
        limitData.setMaxDailyLimitedAmount(BigDecimal.valueOf(100000));
        limitData.setAvailableMonthToDateAmount(BigDecimal.valueOf(1000000));
        limitData.setMaxMonthlyLimitedAmount(BigDecimal.valueOf(1000000));
        limitData.setAvailableYearToDateAmount(BigDecimal.valueOf(10000000));
        limitData.setMaxYearlyLimitedAmount(BigDecimal.valueOf(10000000));
        limitResponse.setData(limitData);

        assertThrows(TransferLimitExceededException.class, () -> method.invoke(tradeTransferService, BigDecimal.valueOf(50000), limitResponse));
    }

    @Test
    void validateTransferLimitsMonthlyExceeded() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("validateTransferLimits", BigDecimal.class, RetrieveTransferLimitResponse.class);
        method.setAccessible(true);

        RetrieveTransferLimitResponse limitResponse = new RetrieveTransferLimitResponse();
        RetrieveTransferLimitResponseData limitData = new RetrieveTransferLimitResponseData();
        limitData.setAvailableTodayAmount(BigDecimal.valueOf(100000));
        limitData.setMaxDailyLimitedAmount(BigDecimal.valueOf(100000));
        limitData.setAvailableMonthToDateAmount(BigDecimal.valueOf(400000)); // Less than required amount
        limitData.setMaxMonthlyLimitedAmount(BigDecimal.valueOf(1000000));
        limitData.setAvailableYearToDateAmount(BigDecimal.valueOf(10000000));
        limitData.setMaxYearlyLimitedAmount(BigDecimal.valueOf(10000000));
        limitResponse.setData(limitData);

        assertThrows(TransferLimitExceededException.class, () -> method.invoke(tradeTransferService, BigDecimal.valueOf(500000), limitResponse));
    }

    @Test
    void validateTransferLimitsYearlyExceeded() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("validateTransferLimits", BigDecimal.class, RetrieveTransferLimitResponse.class);
        method.setAccessible(true);

        RetrieveTransferLimitResponse limitResponse = new RetrieveTransferLimitResponse();
        RetrieveTransferLimitResponseData limitData = new RetrieveTransferLimitResponseData();
        limitData.setAvailableTodayAmount(BigDecimal.valueOf(100000));
        limitData.setMaxDailyLimitedAmount(BigDecimal.valueOf(100000));
        limitData.setAvailableMonthToDateAmount(BigDecimal.valueOf(1000000));
        limitData.setMaxMonthlyLimitedAmount(BigDecimal.valueOf(1000000));
        limitData.setAvailableYearToDateAmount(BigDecimal.valueOf(4000000)); // Less than required amount
        limitData.setMaxYearlyLimitedAmount(BigDecimal.valueOf(10000000));
        limitResponse.setData(limitData);

        assertThrows(TransferLimitExceededException.class, () -> method.invoke(tradeTransferService, BigDecimal.valueOf(5000000), limitResponse));
    }

    @Test
    void validateSreForReceivers() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("validateSreForReceivers", List.class, String.class, Map.class);
        method.setAccessible(true);

        List<ReceiverInfo> receivers = new ArrayList<>();
        ReceiverInfo receiver = new ReceiverInfo();
        receiver.setReceiverCustomerNumber("12345");
        receivers.add(receiver);

        Map<String, String> headers = new HashMap<>();
        headers.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");

        RuleResponse sreResponse = new RuleResponse();
        ResponseDetails sreDetails = new ResponseDetails();
        sreDetails.setResponseCodeNumber(0);
        sreResponse.setResponseDetails(sreDetails);

        lenient().when(sreValidationService.callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(sreResponse);

        // Should not throw exception
        method.invoke(tradeTransferService, receivers, "SENDER_CIN", headers);
    }

    @Test
    void validateSreForReceiversNullList() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("validateSreForReceivers", List.class, String.class, Map.class);
        method.setAccessible(true);

        Map<String, String> headers = new HashMap<>();
        headers.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");

        // Should not throw exception
        method.invoke(tradeTransferService, null, "SENDER_CIN", headers);
    }

    @Test
    void validateSreForReceiversEmptyList() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("validateSreForReceivers", List.class, String.class, Map.class);
        method.setAccessible(true);

        List<ReceiverInfo> receivers = new ArrayList<>();

        Map<String, String> headers = new HashMap<>();
        headers.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");

        // Should not throw exception
        method.invoke(tradeTransferService, receivers, "SENDER_CIN", headers);
    }

    @Test
    void validateSreForReceiversNullCin() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("validateSreForReceivers", List.class, String.class, Map.class);
        method.setAccessible(true);

        List<ReceiverInfo> receivers = new ArrayList<>();
        ReceiverInfo receiver = new ReceiverInfo();
        receiver.setReceiverCustomerNumber(null); // Null CIN
        receivers.add(receiver);

        Map<String, String> headers = new HashMap<>();
        headers.put(HTTPRequestHeaderConstants.X_HSBC_CHNL_COUNTRYCODE, "HK");

        RuleResponse sreResponse = new RuleResponse();
        ResponseDetails sreDetails = new ResponseDetails();
        sreDetails.setResponseCodeNumber(0);
        sreResponse.setResponseDetails(sreDetails);

        lenient().when(sreValidationService.callSreForTransferValidation(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(sreResponse);

        // Should not throw exception
        method.invoke(tradeTransferService, receivers, "SENDER_CIN", headers);
    }

    @Test
    void setSenderNames() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("setSenderNames", CreateTransferRequest.class, PartyNameResponse.class);
        method.setAccessible(true);

        CreateTransferRequest request = new CreateTransferRequest();
        CreateTransferRequestData data = new CreateTransferRequestData();
        request.setData(data);

        PartyNameResponse nameResponse = new PartyNameResponse();
        PartyNameResponseData nameData = new PartyNameResponseData();
        PartyNameValue nameValue = new PartyNameValue();
        nameValue.setGivenName("John");
        nameValue.setLastName("Doe");
        nameValue.setCustomerChristianName("Christian");
        nameData.setName(nameValue);
        nameResponse.setData(nameData);

        method.invoke(tradeTransferService, request, nameResponse);

        assertEquals("John", request.getData().getSenderCustomerFirstName());
        assertEquals("Christian", request.getData().getSenderCustomerMiddleName());
        assertEquals("Doe", request.getData().getSenderCustomerLastName());
    }

    @Test
    void setSenderNamesNullResponse() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("setSenderNames", CreateTransferRequest.class, PartyNameResponse.class);
        method.setAccessible(true);

        CreateTransferRequest request = new CreateTransferRequest();
        CreateTransferRequestData data = new CreateTransferRequestData();
        request.setData(data);

        method.invoke(tradeTransferService, request, null); // Null response

        assertNull(request.getData().getSenderCustomerFirstName());
        assertNull(request.getData().getSenderCustomerMiddleName());
        assertNull(request.getData().getSenderCustomerLastName());
    }

    @Test
    void setSenderNamesNullName() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("setSenderNames", CreateTransferRequest.class, PartyNameResponse.class);
        method.setAccessible(true);

        CreateTransferRequest request = new CreateTransferRequest();
        CreateTransferRequestData data = new CreateTransferRequestData();
        request.setData(data);

        PartyNameResponse nameResponse = new PartyNameResponse();
        PartyNameResponseData nameData = new PartyNameResponseData();
        nameData.setName(null); // Null name object
        nameResponse.setData(nameData);

        method.invoke(tradeTransferService, request, nameResponse);

        assertNull(request.getData().getSenderCustomerFirstName());
        assertNull(request.getData().getSenderCustomerMiddleName());
        assertNull(request.getData().getSenderCustomerLastName());
    }

    @Test
    void setSenderFullName() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("setSenderFullName", CreateTransferResponse.class, PartyNameResponse.class);
        method.setAccessible(true);

        CreateTransferResponse response = new CreateTransferResponse();
        CreateTransferResponseData data = new CreateTransferResponseData();
        response.setData(data);

        PartyNameResponse nameResponse = new PartyNameResponse();
        PartyNameResponseData nameData = new PartyNameResponseData();
        PartyNameValue nameValue = new PartyNameValue();
        nameValue.setGivenName("John");
        nameValue.setLastName("Doe");
        nameValue.setCustomerChristianName("Christian");
        nameData.setName(nameValue);
        nameResponse.setData(nameData);

        method.invoke(tradeTransferService, response, nameResponse);

        assertEquals("Doe John Christian", response.getData().getSenderCustomerName());
    }

    @Test
    void setSenderFullNameNullResponse() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("setSenderFullName", CreateTransferResponse.class, PartyNameResponse.class);
        method.setAccessible(true);

        CreateTransferResponse response = new CreateTransferResponse();
        CreateTransferResponseData data = new CreateTransferResponseData();
        response.setData(data);

        method.invoke(tradeTransferService, response, null); // Null name response

        assertNull(response.getData().getSenderCustomerName());
    }

    @Test
    void setSenderFullNameNullName() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("setSenderFullName", CreateTransferResponse.class, PartyNameResponse.class);
        method.setAccessible(true);

        CreateTransferResponse response = new CreateTransferResponse();
        CreateTransferResponseData data = new CreateTransferResponseData();
        response.setData(data);

        PartyNameResponse nameResponse = new PartyNameResponse();
        PartyNameResponseData nameData = new PartyNameResponseData();
        nameData.setName(null); // Null name object
        nameResponse.setData(nameData);

        method.invoke(tradeTransferService, response, nameResponse);

        assertNull(response.getData().getSenderCustomerName());
    }

    @Test
    void setSenderFullNameMissingNames() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("setSenderFullName", CreateTransferResponse.class, PartyNameResponse.class);
        method.setAccessible(true);

        CreateTransferResponse response = new CreateTransferResponse();
        CreateTransferResponseData data = new CreateTransferResponseData();
        response.setData(data);

        PartyNameResponse nameResponse = new PartyNameResponse();
        PartyNameResponseData nameData = new PartyNameResponseData();
        PartyNameValue nameValue = new PartyNameValue();
        nameValue.setGivenName("John");
        nameValue.setLastName("Doe");
        // CustomerChristianName is null
        nameData.setName(nameValue);
        nameResponse.setData(nameData);

        method.invoke(tradeTransferService, response, nameResponse);

        assertEquals("Doe John", response.getData().getSenderCustomerName());
    }

    @Test
    void handleDOperationResponse() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("handleDOperationResponse", CreateTransferRequest.class, CreateTransferResponse.class, GoldPriceResponse.class);
        method.setAccessible(true);

        CreateTransferRequest request = new CreateTransferRequest();
        CreateTransferRequestData data = new CreateTransferRequestData();
        data.setActionRequestCode(ActionRequestCode.D); // D operation
        request.setData(data);

        CreateTransferResponse response = new CreateTransferResponse();
        CreateTransferResponseData responseData = new CreateTransferResponseData();
        List<TransferOrderInfo> orderList = new ArrayList<>();
        TransferOrderInfo order = new TransferOrderInfo();
        orderList.add(order);
        responseData.setTransferOrderLists(orderList);
        response.setData(responseData);

        GoldPriceResponse goldPriceResponse = new GoldPriceResponse();
        GoldPriceResponseData goldPriceData = new GoldPriceResponseData();
        goldPriceData.setGoldPriceAmount(BigDecimal.valueOf(1200.50));
        goldPriceData.setPublishTime("2025-08-07T01:25:13.837Z");
        goldPriceResponse.setData(goldPriceData);

        when(duplicateSubmitPreventionService.generateUniqueKey()).thenReturn("UNIQUE_KEY_123");

        method.invoke(tradeTransferService, request, response, goldPriceResponse);

        assertEquals("UNIQUE_KEY_123", response.getData().getRequestUniqueKey());
        assertEquals(BigDecimal.valueOf(1200.50), response.getData().getTransferOrderLists().get(0).getRequestPriceValue());
        assertEquals("2025-08-07T01:25:13.837Z", response.getData().getTransferOrderLists().get(0).getRequestPriceAsOfDateTime());
        assertEquals("HKD", response.getData().getTransferOrderLists().get(0).getPriceCurrencyCode());
    }

    @Test
    void handleDOperationResponseNotDOperation() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("handleDOperationResponse", CreateTransferRequest.class, CreateTransferResponse.class, GoldPriceResponse.class);
        method.setAccessible(true);

        CreateTransferRequest request = new CreateTransferRequest();
        CreateTransferRequestData data = new CreateTransferRequestData();
        data.setActionRequestCode(ActionRequestCode.C); // Not D operation
        request.setData(data);

        CreateTransferResponse response = new CreateTransferResponse();
        CreateTransferResponseData responseData = new CreateTransferResponseData();
        List<TransferOrderInfo> orderList = new ArrayList<>();
        TransferOrderInfo order = new TransferOrderInfo();
        orderList.add(order);
        responseData.setTransferOrderLists(orderList);
        response.setData(responseData);

        GoldPriceResponse goldPriceResponse = new GoldPriceResponse();
        GoldPriceResponseData goldPriceData = new GoldPriceResponseData();
        goldPriceData.setGoldPriceAmount(BigDecimal.valueOf(1200.50));
        goldPriceData.setPublishTime("2025-08-07T01:25:13.837Z");
        goldPriceResponse.setData(goldPriceData);

        // Call method for non-D operation
        method.invoke(tradeTransferService, request, response, goldPriceResponse);

        // RequestUniqueKey should not be set
        assertNull(response.getData().getRequestUniqueKey());
        // Order details should not be updated with price
        assertNull(response.getData().getTransferOrderLists().get(0).getRequestPriceValue());
    }

    @Test
    void handleDOperationResponseNullResponse() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("handleDOperationResponse", CreateTransferRequest.class, CreateTransferResponse.class, GoldPriceResponse.class);
        method.setAccessible(true);

        CreateTransferRequest request = new CreateTransferRequest();
        CreateTransferRequestData data = new CreateTransferRequestData();
        data.setActionRequestCode(ActionRequestCode.D); // D operation
        request.setData(data);

        GoldPriceResponse goldPriceResponse = new GoldPriceResponse();
        GoldPriceResponseData goldPriceData = new GoldPriceResponseData();
        goldPriceData.setGoldPriceAmount(BigDecimal.valueOf(1200.50));
        goldPriceData.setPublishTime("2025-08-07T01:25:13.837Z");
        goldPriceResponse.setData(goldPriceData);

        // Call method with null response
        method.invoke(tradeTransferService, request, null, goldPriceResponse);

        // Should not throw exception
    }

    @Test
    void handleDOperationResponseNullGoldPrice() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("handleDOperationResponse", CreateTransferRequest.class, CreateTransferResponse.class, GoldPriceResponse.class);
        method.setAccessible(true);

        CreateTransferRequest request = new CreateTransferRequest();
        CreateTransferRequestData data = new CreateTransferRequestData();
        data.setActionRequestCode(ActionRequestCode.D); // D operation
        request.setData(data);

        CreateTransferResponse response = new CreateTransferResponse();
        CreateTransferResponseData responseData = new CreateTransferResponseData();
        List<TransferOrderInfo> orderList = new ArrayList<>();
        TransferOrderInfo order = new TransferOrderInfo();
        orderList.add(order);
        responseData.setTransferOrderLists(orderList);
        response.setData(responseData);

        // Call method with null gold price
        method.invoke(tradeTransferService, request, response, null);

        // Order details should not be updated with price
        assertNull(response.getData().getTransferOrderLists().get(0).getRequestPriceValue());
    }

    @Test
    void handleDOperationResponseNullGoldPriceData() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("handleDOperationResponse", CreateTransferRequest.class, CreateTransferResponse.class, GoldPriceResponse.class);
        method.setAccessible(true);

        CreateTransferRequest request = new CreateTransferRequest();
        CreateTransferRequestData data = new CreateTransferRequestData();
        data.setActionRequestCode(ActionRequestCode.D); // D operation
        request.setData(data);

        CreateTransferResponse response = new CreateTransferResponse();
        CreateTransferResponseData responseData = new CreateTransferResponseData();
        List<TransferOrderInfo> orderList = new ArrayList<>();
        TransferOrderInfo order = new TransferOrderInfo();
        orderList.add(order);
        responseData.setTransferOrderLists(orderList);
        response.setData(responseData);

        GoldPriceResponse goldPriceResponse = new GoldPriceResponse();
        goldPriceResponse.setData(null); // Null data

        // Call method with null gold price data
        method.invoke(tradeTransferService, request, response, goldPriceResponse);

        // Order details should not be updated with price
        assertNull(response.getData().getTransferOrderLists().get(0).getRequestPriceValue());
    }

    @Test
    void handleDOperationResponseEmptyOrderList() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("handleDOperationResponse", CreateTransferRequest.class, CreateTransferResponse.class, GoldPriceResponse.class);
        method.setAccessible(true);

        CreateTransferRequest request = new CreateTransferRequest();
        CreateTransferRequestData data = new CreateTransferRequestData();
        data.setActionRequestCode(ActionRequestCode.D); // D operation
        request.setData(data);

        CreateTransferResponse response = new CreateTransferResponse();
        CreateTransferResponseData responseData = new CreateTransferResponseData();
        responseData.setTransferOrderLists(new ArrayList<>()); // Empty list
        response.setData(responseData);

        GoldPriceResponse goldPriceResponse = new GoldPriceResponse();
        GoldPriceResponseData goldPriceData = new GoldPriceResponseData();
        goldPriceData.setGoldPriceAmount(BigDecimal.valueOf(1200.50));
        goldPriceData.setPublishTime("2025-08-07T01:25:13.837Z");
        goldPriceResponse.setData(goldPriceData);

        when(duplicateSubmitPreventionService.generateUniqueKey()).thenReturn("UNIQUE_KEY_123");

        method.invoke(tradeTransferService, request, response, goldPriceResponse);

        assertEquals("UNIQUE_KEY_123", response.getData().getRequestUniqueKey());
        // No orders to update, so no assertion for order details
    }

    @Test
    void findAccountChecksumForAccountNumber() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("findAccountChecksumForAccountNumber", CustomerAccounts.class, String.class);
        method.setAccessible(true);

        CustomerAccounts customerAccounts = new CustomerAccounts();
        List<InvestmentAccount> accountList = new ArrayList<>();
        InvestmentAccount account = new InvestmentAccount();
        AccountId accountId = new AccountId();
        accountId.setAccountNumber("123456");
        account.setInvestmentAccountId(accountId);
        account.setChecksum("CHK123");
        accountList.add(account);
        customerAccounts.setInvestmentAccountList(accountList);

        String result = (String) method.invoke(tradeTransferService, customerAccounts, "123456");

        assertEquals("CHK123", result);
    }

    @Test
    void findAccountChecksumForAccountNumberNotFound() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("findAccountChecksumForAccountNumber", CustomerAccounts.class, String.class);
        method.setAccessible(true);

        CustomerAccounts customerAccounts = new CustomerAccounts();
        List<InvestmentAccount> accountList = new ArrayList<>();
        InvestmentAccount account = new InvestmentAccount();
        AccountId accountId = new AccountId();
        accountId.setAccountNumber("123456");
        account.setInvestmentAccountId(accountId);
        account.setChecksum("CHK123");
        accountList.add(account);
        customerAccounts.setInvestmentAccountList(accountList);

        String result = (String) method.invoke(tradeTransferService, customerAccounts, "999999"); // Not found

        assertNull(result);
    }

    @Test
    void findAccountChecksumForAccountNumberNullList() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("findAccountChecksumForAccountNumber", CustomerAccounts.class, String.class);
        method.setAccessible(true);

        CustomerAccounts customerAccounts = new CustomerAccounts();
        customerAccounts.setInvestmentAccountList(null); // Null list

        String result = (String) method.invoke(tradeTransferService, customerAccounts, "123456");

        assertNull(result);
    }

    @Test
    void findAccountChecksumForAccountNumberEmptyList() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("findAccountChecksumForAccountNumber", CustomerAccounts.class, String.class);
        method.setAccessible(true);

        CustomerAccounts customerAccounts = new CustomerAccounts();
        customerAccounts.setInvestmentAccountList(new ArrayList<>()); // Empty list

        String result = (String) method.invoke(tradeTransferService, customerAccounts, "123456");

        assertNull(result);
    }

    @Test
    void findAccountChecksumForAccountNumberNullAccountInList() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("findAccountChecksumForAccountNumber", CustomerAccounts.class, String.class);
        method.setAccessible(true);

        CustomerAccounts customerAccounts = new CustomerAccounts();
        List<InvestmentAccount> accountList = new ArrayList<>();
        accountList.add(null); // Null account in list
        customerAccounts.setInvestmentAccountList(accountList);

        String result = (String) method.invoke(tradeTransferService, customerAccounts, "123456");

        assertNull(result);
    }

    @Test
    void findAccountChecksumForAccountNumberNullAccountId() throws Exception {
        Method method = TradeTransferServiceImpl.class.getDeclaredMethod("findAccountChecksumForAccountNumber", CustomerAccounts.class, String.class);
        method.setAccessible(true);

        CustomerAccounts customerAccounts = new CustomerAccounts();
        List<InvestmentAccount> accountList = new ArrayList<>();
        InvestmentAccount account = new InvestmentAccount();
        account.setInvestmentAccountId(null); // Null account ID
        account.setChecksum("CHK123");
        accountList.add(account);
        customerAccounts.setInvestmentAccountList(accountList);

        String result = (String) method.invoke(tradeTransferService, customerAccounts, "123456");

        assertNull(result);
    }
}
