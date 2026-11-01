package com.kizuna.remuneration.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.cast.remuneration.RemunerationPersonLookup;
import com.kizuna.order.remuneration.OrderRemunerationFacts;
import com.kizuna.remuneration.api.me.SelfRemunerationStatementController;
import com.kizuna.remuneration.application.RemunerationManagementService;
import com.kizuna.remuneration.application.RemunerationStatementService;
import com.kizuna.remuneration.domain.BonusAward;
import com.kizuna.remuneration.domain.GuaranteeState;
import com.kizuna.remuneration.domain.GuaranteeTerm;
import com.kizuna.remuneration.domain.RemunerationChange;
import com.kizuna.remuneration.domain.RemunerationChangeRepository;
import com.kizuna.remuneration.domain.RemunerationRequest;
import com.kizuna.remuneration.infrastructure.RemunerationRecords;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.shift.remuneration.AttendanceFacts;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.application.StoreScopeResolver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

@WebMvcTest({
  GuaranteeController.class,
  BonusController.class,
  RemunerationStatementController.class,
  SelfRemunerationStatementController.class
})
@Import({
  RemunerationControllerTest.Security.class,
  RemunerationStatementService.class,
  RemunerationManagementService.class,
  StoreScopeHolder.class
})
@WithMockUser(
    username = "person@example.test",
    authorities = {
      "PERM_ORDER_MANAGE",
      "PERM_REMUNERATION_VIEW",
      "PERM_GUARANTEE_MANAGE",
      "PERM_BONUS_AWARD",
      "PERM_REMUNERATION_CORRECT",
      "ROLE_CAST"
    })
class RemunerationControllerTest {
  @TestConfiguration
  @EnableMethodSecurity
  static class Security {}

  @Autowired MockMvc mvc;
  @MockitoBean RemunerationRecords records;
  @MockitoBean RemunerationChangeRepository changes;
  @MockitoBean RemunerationPersonLookup people;
  @MockitoBean OrderRemunerationFacts orders;
  @MockitoBean AttendanceFacts attendances;
  @MockitoBean ActorIdentityService actors;
  @MockitoBean BusinessDateService dates;
  @MockitoBean BusinessAudit audit;
  @MockitoBean SystemConfigService settings;
  @MockitoBean StoreExistenceCheck stores;
  @MockitoBean StoreScopeResolver storeScopeResolver;
  @MockitoBean PlatformTransactionManager transactions;
  private static final LocalDate DAY = LocalDate.of(2026, 9, 30);

  @BeforeEach
  void setup() {
    when(stores.exists(anyLong())).thenReturn(true);
    when(people.require(1L, 34L))
        .thenReturn(new RemunerationPersonLookup.Person(34L, "本人", "過去在籍店舗"));
    when(people.require(1L, 999L)).thenThrow(new NotFoundException("見つかりません"));
    when(actors.requireUserId("person@example.test")).thenReturn(12L);
    when(people.self(12L)).thenReturn(34L);
    when(orders.daily(eq(1L), eq(34L), any())).thenReturn(Map.of(DAY, 7000L));
    when(orders.orders(eq(1L), eq(34L), any(), any())).thenReturn(new PageImpl<>(List.of()));
    when(attendances.between(eq(1L), eq(34L), any(), any()))
        .thenReturn(
            List.of(
                new AttendanceFacts.Interval(
                    DAY, DAY.atTime(20, 0), DAY.plusDays(1).atTime(0, 0))));
    when(records.activeTerms(1L, 34L))
        .thenReturn(List.of(new GuaranteeTerm(34L, DAY, GuaranteeState.ACTIVE, 10000L, "日額")));
    when(records.bonusDays(eq(1L), eq(34L), any(), any()))
        .thenReturn(List.of(new RemunerationRecords.BonusDay(DAY, BigDecimal.valueOf(2000))));
    when(records.bonuses(eq(1L), eq(34L), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of()));
    when(dates.currentBusinessDate()).thenReturn(DAY);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/remuneration-statements", "/me/remuneration-statements"})
  void safeStatementUsesDailyTopUpAndAuthenticatedPerson(String path) throws Exception {
    mvc.perform(
            get(path)
                .principal(() -> "person@example.test")
                .header("X-Store-ID", "1")
                .param("store_id", "1")
                .param("person_id", "34")
                .param("month", "2026-09"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.order_total").value(7000))
        .andExpect(jsonPath("$.guarantee_total").value(3000))
        .andExpect(jsonPath("$.bonus_total").value(2000))
        .andExpect(jsonPath("$.total").value(12000))
        .andExpect(jsonPath("$.actor_id").doesNotExist());
  }

  @Test
  void guaranteeListReadsTheNamedStore() throws Exception {
    when(people.require(7L, 34L)).thenReturn(new RemunerationPersonLookup.Person(34L, "本人", "七号店"));
    when(records.timeline(7L, 34L)).thenReturn(Optional.empty());
    when(records.terms(eq(7L), eq(34L), any())).thenReturn(new PageImpl<>(List.of()));
    mvc.perform(get("/remuneration-guarantees").header("X-Store-ID", "7").param("person_id", "34"))
        .andExpect(status().isOk());
    verify(people).require(7L, 34L);
    verify(records).terms(eq(7L), eq(34L), any());
  }

  @Test
  void bonusCreationLocksReadsAndStampsEveryRowInTheNamedStore() throws Exception {
    when(people.require(7L, 34L)).thenReturn(new RemunerationPersonLookup.Person(34L, "本人", "七号店"));
    doAnswer(
            invocation -> {
              Object row = invocation.getArgument(0);
              ReflectionTestUtils.setField(row, "version", 0L);
              return null;
            })
        .when(records)
        .persist(any());
    mvc.perform(
            post("/bonus-awards")
                .principal(() -> "person@example.test")
                .header("X-Store-ID", "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"person_id\":34,\"award_date\":\"2026-09-30\",\"amount\":1000,\"reason\":\"付与\",\"request_id\":\"37caf776-d07e-418d-ad80-75c88de60480\"}"))
        .andExpect(status().isCreated());
    verify(records)
        .receipt(eq(7L), eq(12L), eq(UUID.fromString("37caf776-d07e-418d-ad80-75c88de60480")));
    var persisted = ArgumentCaptor.forClass(Object.class);
    verify(records, times(3)).persist(persisted.capture());
    assertThat(persisted.getAllValues())
        .hasExactlyElementsOfTypes(
            BonusAward.class, RemunerationChange.class, RemunerationRequest.class);
    assertThat(persisted.getAllValues())
        .allSatisfy(row -> assertThat(((StoreScopedEntity) row).getStoreId()).isEqualTo(7L));
  }

  @Test
  void selfCannotSelectAnotherPerson() throws Exception {
    mvc.perform(
            get("/me/remuneration-statements")
                .principal(() -> "person@example.test")
                .param("store_id", "1")
                .param("person_id", "999")
                .param("month", "2026-09"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.person_id").value(34));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/remuneration-statements", "/remuneration-guarantees", "/bonus-awards"})
  void allPersonReadsCheckHistoricalStoreRelation(String path) throws Exception {
    mvc.perform(
            get(path)
                .principal(() -> "person@example.test")
                .header("X-Store-ID", "1")
                .param("person_id", "999")
                .param("month", "2026-09"))
        .andExpect(status().isNotFound());
  }

  @Test
  void allResourceOperationsCheckTargetPersonsStoreRelation() throws Exception {
    var bonus = new BonusAward(999L, DAY, 1000, "付与");
    var term = new GuaranteeTerm(999L, DAY, GuaranteeState.ACTIVE, 10000L, "日額");
    when(records.bonus(1L, "b")).thenReturn(bonus);
    when(records.term(1L, "g")).thenReturn(term);
    for (String base : List.of("/bonus-awards/b", "/remuneration-guarantees/g")) {
      mvc.perform(get(base + "/changes").header("X-Store-ID", "1"))
          .andExpect(status().isNotFound());
      String correction =
          base.contains("bonus-awards")
              ? "\"award_date\":\"2026-09-30\",\"amount\":1000"
              : "\"effective_from\":\"2026-09-30\",\"state\":\"ACTIVE\",\"daily_amount\":10000";
      mvc.perform(
              post(base + "/corrections")
                  .principal(() -> "person@example.test")
                  .header("X-Store-ID", "1")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{"
                          + correction
                          + ",\"reason\":\"条件\",\"correction_reason\":\"訂正\",\"expected_version\":0,\"request_id\":\"37caf776-d07e-418d-ad80-75c88de60480\"}"))
          .andExpect(status().isNotFound());
      mvc.perform(
              post(base + "/cancellation")
                  .principal(() -> "person@example.test")
                  .header("X-Store-ID", "1")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"reason\":\"取消\",\"expected_version\":0,\"request_id\":\"37caf776-d07e-418d-ad80-75c88de60480\"}"))
          .andExpect(status().isNotFound());
    }
  }

  @Test
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void orderPermissionDoesNotGrantNewMoneyReads() throws Exception {
    mvc.perform(
            get("/remuneration-statements")
                .header("X-Store-ID", "1")
                .param("person_id", "34")
                .param("month", "2026-09"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = {"PERM_ORDER_MANAGE", "PERM_REMUNERATION_VIEW"})
  void readingDoesNotGrantBonusWrite() throws Exception {
    mvc.perform(
            post("/bonus-awards")
                .header("X-Store-ID", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"person_id\":34,\"award_date\":\"2026-09-30\",\"amount\":1000,\"reason\":\"付与\",\"request_id\":\"37caf776-d07e-418d-ad80-75c88de60480\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = {"PERM_ORDER_SET_VIEW", "PERM_REMUNERATION_VIEW"})
  void crossStoreViewerReadsCurrentStoreStatement() throws Exception {
    mvc.perform(
            get("/remuneration-statements")
                .header("X-Store-ID", "1")
                .param("person_id", "34")
                .param("month", "2026-09"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(12000));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/remuneration-guarantees", "/bonus-awards"})
  @WithMockUser(authorities = {"PERM_ORDER_SET_VIEW", "PERM_REMUNERATION_VIEW"})
  void crossStoreViewerDoesNotReachStoreOnlyReads(String path) throws Exception {
    mvc.perform(
            get(path).header("X-Store-ID", "1").param("person_id", "34").param("month", "2026-09"))
        .andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "101", "-1"})
  void pageLimitsAreValidated(String size) throws Exception {
    mvc.perform(
            get("/remuneration-statements")
                .header("X-Store-ID", "1")
                .param("person_id", "34")
                .param("month", "2026-09")
                .param("size", size))
        .andExpect(status().isBadRequest());
  }

  @Test
  void changeHistoriesArePagesNewestFirst() throws Exception {
    when(records.bonus(1L, "b")).thenReturn(new BonusAward(34L, DAY, 1000, "付与"));
    when(records.term(1L, "g"))
        .thenReturn(new GuaranteeTerm(34L, DAY, GuaranteeState.ACTIVE, 10000L, "日額"));
    var pageable = ArgumentCaptor.forClass(Pageable.class);
    when(changes.findByStoreIdAndResourceTypeAndResourceId(
            eq(1L), any(), any(), pageable.capture()))
        .thenReturn(new PageImpl<>(List.of()));
    for (String base : List.of("/bonus-awards/b", "/remuneration-guarantees/g"))
      mvc.perform(get(base + "/changes").header("X-Store-ID", "1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.total_elements").value(0));
    verify(changes).findByStoreIdAndResourceTypeAndResourceId(eq(1L), eq("BONUS"), eq("b"), any());
    verify(changes)
        .findByStoreIdAndResourceTypeAndResourceId(eq(1L), eq("GUARANTEE"), eq("g"), any());
    assertThat(pageable.getAllValues())
        .allSatisfy(
            p ->
                assertThat(p.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt", "id")));
  }
}
