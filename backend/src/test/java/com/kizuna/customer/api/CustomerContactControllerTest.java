package com.kizuna.customer.api;

import static com.kizuna.shared.web.JwtRequests.jwtUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.customer.application.CustomerCommands.ContactValue;
import com.kizuna.customer.application.CustomerCommands.PermissionChange;
import com.kizuna.customer.application.CustomerContactService;
import com.kizuna.customer.application.CustomerViews.ContactView;
import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactPermissions;
import com.kizuna.customer.domain.ContactPurpose;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.CustomerContact;
import com.kizuna.customer.domain.CustomerContactHistory;
import com.kizuna.shared.exception.CommonExceptionHandler;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(CustomerContactController.class)
@Import({
  CustomerContactControllerTest.MethodSecurityConfig.class,
  StoreScopeHolder.class,
  CommonExceptionHandler.class
})
class CustomerContactControllerTest {
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  private static final Long STORE_ID = 7L;
  private static final long ACTOR_ID = 42L;

  @Autowired private MockMvc mockMvc;
  @MockitoBean private CustomerContactService service;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  /** 利用者の店舗集合に名乗る店舗が含まれている状態。 */
  @BeforeEach
  void storeIsInScope() {
    when(storeScopeResolver.resolve(any())).thenReturn(new StoreScope(Set.of(STORE_ID)));
  }

  private static RequestPostProcessor manager() {
    return jwtUser(ACTOR_ID, "PERM_CUSTOMER_MANAGE");
  }

  @BeforeEach
  void storeExists() {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
  }

  @Test
  void contactsAreAPageInIdOrder() throws Exception {
    var pageable = ArgumentCaptor.forClass(Pageable.class);
    when(service.list(eq(STORE_ID), eq("c1"), pageable.capture()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    mockMvc
        .perform(
            get("/customers/c1/contacts").header("X-Store-ID", STORE_ID.toString()).with(manager()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));
    assertThat(pageable.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Direction.ASC, CustomerContact::getId));
  }

  @Test
  void historyIsAPageNewestFirstAndRejectsOutOfRangePages() throws Exception {
    var pageable = ArgumentCaptor.forClass(Pageable.class);
    when(service.history(eq(STORE_ID), eq("c1"), pageable.capture()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    mockMvc
        .perform(
            get("/customers/c1/contact-history")
                .header("X-Store-ID", STORE_ID.toString())
                .with(manager()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));
    assertThat(pageable.getValue().getSort())
        .isEqualTo(
            Sort.by(
                Sort.Direction.DESC,
                CustomerContactHistory::getOccurredAt,
                CustomerContactHistory::getId));

    mockMvc
        .perform(
            get("/customers/c1/contact-history")
                .param("page", "99999999")
                .param("size", "100")
                .header("X-Store-ID", STORE_ID.toString())
                .with(manager()))
        .andExpect(status().isBadRequest());
  }

  private static ContactView view() {
    return new ContactView(
        mock(CustomerContact.class),
        new ContactPermissions(ContactPermissionStatus.UNKNOWN, ContactPermissionStatus.UNKNOWN));
  }

  @Test
  void writesNameTheTokenUserAsTheActor() throws Exception {
    var value = new ContactValue(ContactType.PHONE, "09011112222");
    var change = new PermissionChange(ContactPermissionStatus.DENIED, "電話", "本人の申し出");
    when(service.create(STORE_ID, "c1", value, ACTOR_ID)).thenReturn(view());
    when(service.update(STORE_ID, "c1", "k1", value, ACTOR_ID)).thenReturn(view());
    when(service.changePermission(STORE_ID, "c1", "k1", ContactPurpose.MARKETING, change, ACTOR_ID))
        .thenReturn(view());
    String contact = "{\"type\":\"PHONE\",\"value\":\"09011112222\"}";

    mockMvc
        .perform(
            post("/customers/c1/contacts")
                .header("X-Store-ID", STORE_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(contact)
                .with(manager()))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            put("/customers/c1/contacts/k1")
                .header("X-Store-ID", STORE_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(contact)
                .with(manager()))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            put("/customers/c1/contacts/k1/permissions/MARKETING")
                .header("X-Store-ID", STORE_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DENIED\",\"source\":\"電話\",\"reason\":\"本人の申し出\"}")
                .with(manager()))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            delete("/customers/c1/contacts/k1")
                .header("X-Store-ID", STORE_ID.toString())
                .with(manager()))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            put("/customers/c1/contact-preferences/PHONE")
                .header("X-Store-ID", STORE_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contact_id\":\"k1\"}")
                .with(manager()))
        .andExpect(status().isNoContent());

    verify(service).create(STORE_ID, "c1", value, ACTOR_ID);
    verify(service).update(STORE_ID, "c1", "k1", value, ACTOR_ID);
    verify(service)
        .changePermission(STORE_ID, "c1", "k1", ContactPurpose.MARKETING, change, ACTOR_ID);
    verify(service).delete(STORE_ID, "c1", "k1", ACTOR_ID);
    verify(service).prefer(STORE_ID, "c1", ContactType.PHONE, "k1", ACTOR_ID);
  }
}
