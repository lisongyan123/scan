package com.hsbc.trade.transfer.service;

import com.hsbc.trade.HTTPRequestHeaderConstants;
import com.hsbc.trade.common.ResponseDetails;
import com.hsbc.trade.service.RestClientService;
import com.hsbc.trade.transfer.domain.account.CustomerAccounts;
import com.hsbc.trade.transfer.domain.account.InvestmentAccount;
import com.hsbc.trade.transfer.domain.cep.PartyContact;
import com.hsbc.trade.transfer.domain.cep.PartyContactResponse;
import com.hsbc.trade.transfer.domain.cep.PartyName;
import com.hsbc.trade.transfer.domain.cep.PartyNameResponse;
import com.hsbc.trade.transfer.retrievetransferdetail.RetrieveTransferDetailResponse;
import com.hsbc.trade.transfer.retrievetransferdetail.RetrieveTransferDetailResponseData;
import com.hsbc.trade.transfer.service.impl.TradeLimitServiceImpl;
import com.hsbc.trade.transfer.service.impl.TradeTransferServiceImpl;
import com.hsbc.trade.utils.E2ETrustTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeTransferServiceImplRetrieveTransferDetailTest {

    private static final String MOCK_TRADE_ONLINE_URL = "http://mock-trade-online";
    private static final String MOCK_ACCOUNTS_MAP_URL = "http://mock-accounts-map";
    private static final String MOCK_CEP_PARTY_NAME_URL = "http://mock-cep/party/CIN-SensitiveHeadersKey/name";
    private static final String MOCK_CEP_PARTY_CONTACT_URL = "http://mock-cep/party/CIN-SensitiveHeadersKey/contact";

    @InjectMocks
    private TradeTransferServiceImpl tradeTransferService;

    @Mock
    private RestClientService restClientService;

    @Mock
    private E2ETrustTokenUtil e2ETrustTokenUtil;

    // TradeLimitService 虽然 retrieveTransferDetail 不直接依赖它，但 TradeTransferServiceImpl 构造器需要
    @Mock
    private TradeLimitServiceImpl tradeLimitService;

    private Map<String, String> baseRequestHeaders;

    @BeforeEach
    void setUp() {
        // 设置 URL
        ReflectionTestUtils.setField(tradeTransferService, "tradeOnlineUrl", MOCK_TRADE_ONLINE_URL);
        ReflectionTestUtils.setField(tradeTransferService, "accountsMapUrl", MOCK_ACCOUNTS_MAP_URL);
        ReflectionTestUtils.setField(tradeTransferService, "cepPartyNameUrl", MOCK_CEP_PARTY_NAME_URL);
        ReflectionTestUtils.setField(tradeTransferService, "cepPartyContactUrl", MOCK_CEP_PARTY_CONTACT_URL);
        ReflectionTestUtils.setField(tradeTransferService, "timeout", 10000);
        ReflectionTestUtils.setField(tradeTransferService, "printMessageLog", true);

        baseRequestHeaders = new HashMap<>();
        baseRequestHeaders.put(HTTPRequestHeaderConstants.X_HSBC_CUSTOMER_ID, "TESTcin123");
        baseRequestHeaders.put(HTTPRequestHeaderConstants.X_HSBC_USER_ID, "testUser");
    }

    @Test
    void testRetrieveTransferDetail_Success_WithCustomerNameAndChecksum() {
        // Arrange
        String transferReferenceNumber = "TRF123456";
        String customerInternalNumber = "TESTcin123";
        String accountNumber = "987654321";
        String checksum = "chk123";

        // 1. Mock trade-online response
        RetrieveTransferDetailResponse mockResponse = new RetrieveTransferDetailResponse();
        RetrieveTransferDetailResponseData responseData = new RetrieveTransferDetailResponseData();
        com.hsbc.trade.common.AccountId accountId = new com.hsbc.trade.common.AccountId();
        accountId.setAccountNumber(accountNumber);
        responseData.setInvestmentAccount(accountId);
        ResponseDetails responseDetails = new ResponseDetails();
        responseDetails.setResponseCodeNumber(0);
        responseData.setResponseDetails(responseDetails);
        mockResponse.setData(responseData);

        // 2. Mock accounts-map response (for checksum mapping)
        CustomerAccounts customerAccounts = new CustomerAccounts();
        InvestmentAccount investmentAccount = new InvestmentAccount();
        investmentAccount.setChecksum(checksum);
        com.hsbc.trade.transfer.domain.account.AccountId invAccountId =
                com.hsbc.trade.transfer.domain.account.AccountId.builder()
                        .countryAccountCode("HK")
                        .groupMemberAccountCode("HBAP")
                        .accountNumber(accountNumber)
                        .accountProductTypeCode("GOLD")
                        .accountTypeCode("01")
                        .accountCurrencyCode("HKD")
                        .build();
        investmentAccount.setInvestmentAccountId(invAccountId);
        customerAccounts.setInvestmentAccountList(Collections.singletonList(investmentAccount));

        // 3. Mock CEP responses
        PartyNameResponse partyNameResponse = new PartyNameResponse();
        PartyName name = new PartyName();
        name.setLastName("Doe");
        name.setGivenName("John");
        name.setCustomerChristianName("Michael");
        partyNameResponse.setName(name);

        PartyContactResponse partyContactResponse = new PartyContactResponse();
        PartyContact contact = new PartyContact();
        contact.setMobileNumber1("123456789");
        partyContactResponse.setContact(contact);

        // 4. Mock E2E token
        when(e2ETrustTokenUtil.getE2ETrustToken()).thenReturn("mock-e2e-token");

        // 5. Mock all restClientService calls
        // a. trade-online
        when(restClientService.get(
                startsWith(MOCK_TRADE_ONLINE_URL + "/transfers/TRF123456?"),
                anyMap(),
                eq(RetrieveTransferDetailResponse.class),
                anyInt(),
                anyBoolean()
        )).thenReturn(mockResponse);

        // b. accounts-map
        when(restClientService.get(
                eq(MOCK_ACCOUNTS_MAP_URL + "accounts-map?consumerId=DAC"),
                anyMap(),
                eq(CustomerAccounts.class),
                anyInt(),
                anyBoolean()
        )).thenReturn(customerAccounts);

        // c. CEP name
        String expectedNameUrl = MOCK_CEP_PARTY_NAME_URL.replace("CIN-SensitiveHeadersKey", customerInternalNumber);
        Map<String, String> expectedCepHeaders = buildExpectedCepHeaders(baseRequestHeaders, customerInternalNumber);
        when(restClientService.get(
                eq(expectedNameUrl),
                argThat(h -> h.equals(expectedCepHeaders)),
                eq(PartyNameResponse.class),
                anyInt(),
                anyBoolean()
        )).thenReturn(partyNameResponse);

        // d. CEP contact
        String expectedContactUrl = MOCK_CEP_PARTY_CONTACT_URL.replace("CIN-SensitiveHeadersKey", customerInternalNumber);
        when(restClientService.get(
                eq(expectedContactUrl),
                argThat(h -> h.equals(expectedCepHeaders)),
                eq(PartyContactResponse.class),
                anyInt(),
                anyBoolean()
        )).thenReturn(partyContactResponse);

        // Act
        RetrieveTransferDetailResponse result = tradeTransferService.retrieveTransferDetail(baseRequestHeaders, transferReferenceNumber);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getData());
        assertNotNull(result.getResponseDetails());
        assertEquals(0, result.getResponseDetails().getResponseCodeNumber());
        assertNotNull(result.getData().getInvestmentAccount());
        assertEquals(accountNumber, result.getData().getInvestmentAccount().getAccountNumber());
        assertEquals(checksum, result.getData().getAccountChecksumIdentifier()); // ✅ checksum set
        assertEquals("Doe", result.getData().getSenderCustomerLastName()); // from CEP
        assertEquals("123456789", result.getData().getSenderCustomerMobileNumber()); // from CEP

        // Verify
        verify(restClientService, times(1)).get(anyString(), anyMap(), eq(RetrieveTransferDetailResponse.class), anyInt(), anyBoolean());
        verify(restClientService, times(1)).get(anyString(), anyMap(), eq(CustomerAccounts.class), anyInt(), anyBoolean());
        verify(restClientService, times(2)).get(anyString(), anyMap(), any(), anyInt(), anyBoolean()); // 2 CEP calls
        verify(e2ETrustTokenUtil, times(2)).getE2ETrustToken();
    }

    // Helper: 构建 CEP 预期 headers
    private Map<String, String> buildExpectedCepHeaders(Map<String, String> baseHeaders, String cin) {
        Map<String, String> headers = new HashMap<>(baseHeaders);
        headers.remove(HTTPRequestHeaderConstants.X_HSBC_SAML);
        headers.remove(HTTPRequestHeaderConstants.X_HSBC_SAML3);
        headers.put(HTTPRequestHeaderConstants.X_HSBC_E2E_TRUST_TOKEN, "mock-e2e-token");
        headers.put(HTTPRequestHeaderConstants.X_HSBC_GBGF, "");
        headers.put(HTTPRequestHeaderConstants.X_HSBC_SOURCE_SYSTEM_ID, "");
        String sensitiveJson = "[{\"key\":\"SensitiveHeadersKey\",\"value\":\"" + cin + "\"}]";
        String base64 = Base64.getEncoder().encodeToString(sensitiveJson.getBytes(StandardCharsets.UTF_8));
        headers.put(HTTPRequestHeaderConstants.X_HSBC_SENSITIVE_DATA, base64);
        return headers;
    }
}
