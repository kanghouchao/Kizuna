package com.kizuna.customer.api.store;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.customer.api.dto.CustomerMapperImpl;
import com.kizuna.customer.application.CustomerContactService;
import com.kizuna.customer.application.CustomerService;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerCandidateRepository;
import com.kizuna.customer.domain.CustomerContact;
import com.kizuna.customer.domain.CustomerContactHistoryRepository;
import com.kizuna.customer.domain.CustomerContactRepository;
import com.kizuna.customer.domain.CustomerListRepository;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerMergeRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.store.application.StoreActivationService;
import com.kizuna.user.domain.PlatformUserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

/** 実際の controller・service・mapper を通じて候補の判断材料と HTTP 応答を検証する。 */
@WebMvcTest(CustomerController.class)
@Import({
  CustomerService.class,
  CustomerContactService.class,
  CustomerMapperImpl.class,
  StoreContext.class,
  CustomerSearchControllerTest.Security.class
})
@WithMockUser(authorities = {"PERM_CUSTOMER_MANAGE", "PERM_CUSTOMER_MERGE"})
class CustomerSearchControllerTest {
  @TestConfiguration
  @EnableMethodSecurity
  static class Security {}

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockitoBean PlatformTransactionManager transactions;
  @MockitoBean CustomerRepository customers;
  @MockitoBean CustomerListRepository customerList;
  @MockitoBean CustomerCandidateRepository candidates;
  @MockitoBean CustomerContactRepository contacts;
  @MockitoBean CustomerContactHistoryRepository histories;
  @MockitoBean CustomerMemberLinkRepository links;
  @MockitoBean CustomerMergeRepository merges;
  @MockitoBean PlatformUserRepository users;
  @MockitoBean SystemConfigService config;
  @MockitoBean StoreExistenceCheck stores;
  @MockitoBean StoreActivationService activation;

  @BeforeEach
  void storeExists() {
    when(stores.exists(anyLong())).thenReturn(true);
  }

  @Test
  void groupsIncludeComparisonMaterialAndOpaqueContinuation() throws Exception {
    var a = customer("1", "候補甲");
    var b = customer("2", "候補乙");
    when(candidates.groups(any(), any(), any(), any(), anyInt()))
        .thenReturn(
            List.of(
                new CustomerCandidateRepository.Group(ContactType.EMAIL, "Case@example.com", 2),
                new CustomerCandidateRepository.Group(ContactType.LINE, "Case@example.com", 2)));
    when(candidates.members(any()))
        .thenReturn(
            List.of(
                new CustomerCandidateRepository.Match(ContactType.EMAIL, "Case@example.com", a),
                new CustomerCandidateRepository.Match(ContactType.EMAIL, "Case@example.com", b)));
    var preferred = CustomerContact.create("1", ContactType.PHONE, "090-1234-5678");
    preferred.setId("10");
    preferred.prefer(true);
    when(contacts.findByCustomerIdInAndPreferredTrueAndDeletedFalseOrderByIdAsc(any()))
        .thenReturn(List.of(preferred));
    var body =
        mvc.perform(
                get("/store/customers/duplicates")
                    .param("size", "1")
                    .header("X-Role", "store")
                    .header("X-Store-ID", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].matched_type").value("EMAIL"))
            .andExpect(jsonPath("$.content[0].total").value(2))
            .andExpect(jsonPath("$.content[0].customers[0].name").value("候補甲"))
            .andExpect(
                jsonPath("$.content[0].customers[0].preferred_contacts[0].value")
                    .value("+819012345678"))
            .andExpect(jsonPath("$.content[0].customers[0].member_linked").value(false))
            .andExpect(jsonPath("$.content[0].customers[0].order_count").value(0))
            .andExpect(jsonPath("$.next_cursor").isString())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String cursor = json.readTree(body).path("next_cursor").asString();
    when(candidates.groups(any(), any(), any(), any(), anyInt())).thenReturn(List.of());
    when(candidates.members(any())).thenReturn(List.of());
    mvc.perform(
            get("/store/customers/duplicates")
                .param("cursor", cursor)
                .header("X-Role", "store")
                .header("X-Store-ID", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.next_cursor").doesNotExist());
  }

  @Test
  void largeGroupMembersArePagedWithNormalizedInput() throws Exception {
    var a = customer("1", "候補甲");
    var b = customer("2", "候補乙");
    when(candidates.members(eq(ContactType.PHONE), eq("+819012345678"), any(), anyInt()))
        .thenReturn(List.of(a, b));
    var body =
        mvc.perform(
                get("/store/customers/duplicates/customers")
                    .param("type", "PHONE")
                    .param("value", "090-1234-5678")
                    .param("size", "1")
                    .header("X-Role", "store")
                    .header("X-Store-ID", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value("1"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String cursor = json.readTree(body).path("next_cursor").asString();
    when(candidates.members(eq(ContactType.PHONE), eq("+819012345678"), eq("1"), anyInt()))
        .thenReturn(List.of(b));
    mvc.perform(
            get("/store/customers/duplicates/customers")
                .param("type", "PHONE")
                .param("value", "09012345678")
                .param("cursor", cursor)
                .header("X-Role", "store")
                .header("X-Store-ID", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value("2"))
        .andExpect(jsonPath("$.next_cursor").doesNotExist());
  }

  @Test
  void listReturnsMatchEvidenceSeparatelyFromPreferredContacts() throws Exception {
    var customer = customer("1", "候補甲");
    var contact = CustomerContact.create("1", ContactType.EMAIL, "Case@EXAMPLE.COM");
    contact.setId("10");
    when(customerList.findAll(ArgumentMatchers.<Specification<Customer>>any(), any(Pageable.class)))
        .thenReturn(
            new PageImpl<>(List.of(new CustomerListRepository.Row(customer, null, null, false))));
    when(contacts.findAll(ArgumentMatchers.<Specification<CustomerContact>>any(), any(Sort.class)))
        .thenReturn(List.of(contact));
    mvc.perform(
            get("/store/customers")
                .param("search", "Case@EXAMPLE.COM")
                .header("X-Role", "store")
                .header("X-Store-ID", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].preferred_contacts").isEmpty())
        .andExpect(jsonPath("$.content[0].matched_contacts[0].value").value("Case@example.com"));
  }

  @Test
  void rejectsInvalidSizesAndCursorsAsClientErrors() throws Exception {
    for (String endpoint :
        List.of("/store/customers/duplicates", "/store/customers/duplicates/customers")) {
      for (String size : List.of("0", "-1", "2001")) {
        mvc.perform(
                get(endpoint)
                    .param("type", "LINE")
                    .param("value", "Case")
                    .param("size", size)
                    .header("X-Role", "store")
                    .header("X-Store-ID", "1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").isString());
      }
      mvc.perform(
              get(endpoint)
                  .param("type", "LINE")
                  .param("value", "Case")
                  .param("cursor", "bad")
                  .header("X-Role", "store")
                  .header("X-Store-ID", "1"))
          .andExpect(status().isBadRequest());
    }
  }

  private Customer customer(String id, String name) {
    var customer = Customer.builder().name(name).build();
    customer.setId(id);
    return customer;
  }
}
