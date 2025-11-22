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
void testRetrieveTransferDetail_Success_WithCustomerName() {
    // Arrange
    String transferReferenceNumber = "ref123";
    String customerInternalNumber = "TESTcin123";

    // === 1. Mock trade-online response ===
    RetrieveTransferDetailResponse mockResponse = new RetrieveTransferDetailResponse();
    RetrieveTransferDetailResponseData responseData = new RetrieveTransferDetailResponseData();
    AccountId accountId = new AccountId();
    accountId.setAccountNumber("987654321");
    responseData.setInvestmentAccount(accountId);
    ResponseDetails responseDetails = new ResponseDetails();
    responseDetails.setResponseCodeNumber(0);
    responseData.setResponseDetails(responseDetails);
    mockResponse.setData(responseData);

    // === 2. Mock accounts-map ===
    CustomerAccounts mockCustomerAccounts = new CustomerAccounts();
    InvestmentAccount acc = new InvestmentAccount();
    acc.setChecksum("chk123");
    com.hsbc.trade.transfer.domain.account.AccountId invAccountId =
        com.hsbc.trade.transfer.domain.account.AccountId.builder()
            .countryAccountCode("HK")
            .groupMemberAccountCode("HBAP")
            .accountNumber("987654321")
            .accountProductTypeCode("GOLD")
            .accountTypeCode("01")
            .accountCurrencyCode("HKD")
            .build();
    acc.setInvestmentAccountId(invAccountId);
    mockCustomerAccounts.setInvestmentAccountList(Collections.singletonList(acc));

    // === 3. Mock CEP responses ===
    PartyNameResponse mockNameResponse = new PartyNameResponse();
    PartyName name = new PartyName();
    name.setLastName("Doe");
    name.setGivenName("John");
    name.setCustomerChristianName("Smith");
    mockNameResponse.setName(name);

    PartyContactResponse mockContactResponse = new PartyContactResponse();
    PartyContact contact = new PartyContact();
    contact.setMobileNumber1("123456789");
    mockContactResponse.setContact(contact);

    // === 4. Build expected headers for CEP name (uses updateHeaderforCEP) ===
    Map<String, String> baseHeaders = buildRequestHeaders(baseRequestHeaders); // includes X_HSBC_CUSTOMER_ID etc.
    Map<String, String> cepHeaders = new HashMap<>(baseHeaders);
    cepHeaders.remove(HTTPRequestHeaderConstants.X_HSBC_SAML);
    cepHeaders.remove(HTTPRequestHeaderConstants.X_HSBC_SAML3);
    cepHeaders.put(HTTPRequestHeaderConstants.X_HSBC_E2E_TRUST_TOKEN, "mock-e2e-token");
    cepHeaders.put(HTTPRequestHeaderConstants.X_HSBC_GBGF, "");
    cepHeaders.put(HTTPRequestHeaderConstants.X_HSBC_SOURCE_SYSTEM_ID, "");
    String sensitiveJson = "[{\"key\":\"SensitiveHeadersKey\",\"value\":\"" + customerInternalNumber + "\"}]";
    String base64Sensitive = Base64.getEncoder().encodeToString(sensitiveJson.getBytes(StandardCharsets.UTF_8));
    cepHeaders.put(HTTPRequestHeaderConstants.X_HSBC_SENSITIVE_DATA, base64Sensitive);

    // === 5. Build expected headers for CEP contact (⚠️ does NOT use updateHeaderforCEP!) ===
    Map<String, String> contactHeaders = new HashMap<>(baseHeaders);
    contactHeaders.put(HTTPRequestHeaderConstants.X_HSBC_SENSITIVE_DATA, base64Sensitive); // only this is added

    // === 6. Mock all calls ===
    when(restClientService.get(
        eq(MOCK_TRADE_ONLINE_URL + "/transfers/ref123?customerInternalNumber=TESTcin123&sParameterType=SENS"),
        anyMap(),
        eq(RetrieveTransferDetailResponse.class),
        anyInt(),
        anyBoolean()
    )).thenReturn(mockResponse);

    when(restClientService.get(
        eq(MOCK_ACCOUNTS_MAP_URL + "accounts-map?consumerId=DAC"),
        anyMap(),
        eq(CustomerAccounts.class),
        anyInt(),
        anyBoolean()
    )).thenReturn(mockCustomerAccounts);

    // ✅ Mock CEP name (with cepHeaders)
    when(restClientService.get(
        eq(MOCK_CEP_PARTY_NAME_URL.replace("CIN-SensitiveHeadersKey", customerInternalNumber)),
        argThat(h -> h.equals(cepHeaders)),
        eq(PartyNameResponse.class),
        anyInt(),
        anyBoolean()
    )).thenReturn(mockNameResponse);

    // ✅ FIX: Mock CEP contact (with contactHeaders — no E2E token!)
    when(restClientService.get(
        eq(MOCK_CEP_PARTY_CONTACT_URL.replace("CIN-SensitiveHeadersKey", customerInternalNumber)),
        argThat(h -> h.equals(contactHeaders)), // ← 关键：使用 contactHeaders 而不是 cepHeaders
        eq(PartyContactResponse.class),
        anyInt(),
        anyBoolean()
    )).thenReturn(mockContactResponse);

    when(e2ETrustTokenUtil.getE2ETrustToken()).thenReturn("mock-e2e-token");

    // Act
    RetrieveTransferDetailResponse result = tradeTransferService.retrieveTransferDetail(baseRequestHeaders, transferReferenceNumber);

    // Assert
    assertNotNull(result);
    assertNotNull(result.getData().getSenderCustomerMobileNumber());
    assertEquals("123456789", result.getData().getSenderCustomerMobileNumber());
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
