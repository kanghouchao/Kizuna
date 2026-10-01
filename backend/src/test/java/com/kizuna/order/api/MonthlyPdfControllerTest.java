package com.kizuna.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.order.api.dto.MonthlyRemunerationOrderSummary;
import com.kizuna.order.api.platform.PlatformMonthlyPdfController;
import com.kizuna.order.api.platform.SelfMonthlyPdfController;
import com.kizuna.order.api.store.StoreMonthlyPdfController;
import com.kizuna.order.application.MonthlyPdfService;
import com.kizuna.order.application.MonthlyPdfSnapshotService;
import com.kizuna.order.application.MonthlyRemunerationService;
import com.kizuna.order.application.PlatformMonthlyRemunerationService;
import com.kizuna.order.application.SelfMonthlyRemunerationService;
import com.kizuna.order.infrastructure.MonthlyPdfRenderer;
import com.kizuna.order.infrastructure.MonthlyRemunerationQuery;
import com.kizuna.order.infrastructure.PlatformMonthlyRemunerationStores;
import com.kizuna.order.infrastructure.SelfMonthlyRemunerationQuery;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.store.application.StoreActivationService;
import com.kizuna.user.application.ActorIdentityService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;

@WebMvcTest({
  StoreMonthlyPdfController.class,
  PlatformMonthlyPdfController.class,
  SelfMonthlyPdfController.class
})
@Import({
  MonthlyPdfControllerTest.Config.class,
  MonthlyPdfService.class,
  MonthlyPdfSnapshotService.class,
  MonthlyPdfRenderer.class,
  MonthlyRemunerationService.class,
  PlatformMonthlyRemunerationService.class,
  SelfMonthlyRemunerationService.class,
  StoreContext.class
})
class MonthlyPdfControllerTest {
  @TestConfiguration
  @EnableMethodSecurity
  @EnableWebSecurity
  static class Config {
    @Bean
    AppProperties appProperties() {
      return new AppProperties();
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
      return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }

    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-10-01T06:00:00Z"), ZoneId.of("Asia/Tokyo"));
    }
  }

  @Autowired MockMvc mvc;
  @Autowired AppProperties properties;
  @MockitoBean MonthlyRemunerationQuery query;
  @MockitoBean PlatformMonthlyRemunerationStores stores;
  @MockitoBean SelfMonthlyRemunerationQuery self;
  @MockitoBean ActorIdentityService actors;
  @MockitoBean CastRepository people;
  @MockitoBean SystemConfigService systemConfigService;
  @MockitoBean StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreActivationService storeActivationService;
  @MockitoBean PlatformTransactionManager transactionManager;

  @BeforeEach
  void data() {
    properties.getMonthlyPdf().setMaxBytes(16L * 1024 * 1024);
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(stores.name(1L)).thenReturn("日本語店舗");
    when(query.personName(1L, 34L)).thenReturn("源氏名さくら");
    when(self.storeName(34L, 1L)).thenReturn("日本語店舗");
    when(actors.requireUserId("actor")).thenReturn(12L);
    var person = Cast.builder().platformUserId(12L).realName("印刷禁止の本名").build();
    person.setId(34L);
    when(people.findByPlatformUserId(12L)).thenReturn(Optional.of(person));
    rows(0);
  }

  private void rows(int count) {
    var rows =
        IntStream.range(0, count)
            .mapToObj(
                i ->
                    new MonthlyRemunerationOrderSummary(
                        "ORDER-" + String.format("%05d", i),
                        LocalDate.of(2026, 9, 30),
                        "日本語サービス",
                        7000,
                        false))
            .toList();
    when(query.total(eq(1L), eq(34L), any())).thenReturn(count * 7000L);
    when(query.orders(eq(1L), eq(34L), any(), any()))
        .thenAnswer(
            inv -> {
              Pageable page = inv.getArgument(3);
              int start = (int) page.getOffset();
              return new PageImpl<>(
                  rows.subList(Math.min(start, count), Math.min(start + page.getPageSize(), count)),
                  page,
                  count);
            });
  }

  private MockHttpServletRequestBuilder request(String scope) {
    String path = scope.equals("self") ? "/platform/me" : "/" + scope;
    String authority =
        switch (scope) {
          case "store" -> "PERM_ORDER_MANAGE";
          case "platform" -> "PERM_ORDER_SET_MANAGE";
          default -> "ROLE_CAST";
        };
    var request =
        get(path + "/monthly-remunerations/pdf")
            .with(
                jwt()
                    .jwt(
                        j ->
                            j.subject("actor")
                                .claim("storeBridge", true)
                                .claim("storeScopeType", "SPECIFIC_STORES")
                                .claim("storeIds", List.of(1L)))
                    .authorities(new SimpleGrantedAuthority(authority)))
            .param("store_id", "1")
            .param("person_id", "34")
            .param("month", "2026-09");
    if (scope.equals("store")) request.header("X-Role", "store").header("X-Store-ID", "1");
    return request;
  }

  @ParameterizedTest
  @ValueSource(strings = {"store", "platform", "self"})
  void emptyMonthHasNamesAndNoPrivateIdentity(String scope) throws Exception {
    var result =
        mvc.perform(request(scope))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        "attachment; filename=\"monthly-remuneration-2026-09.pdf\""))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    try (var pdf = Loader.loadPDF(result)) {
      assertThat(new PDFTextStripper().getText(pdf))
          .contains(
              "源氏名さくら",
              "日本語店舗",
              "2026-09",
              "2026-10-01 15:00:00 +09:00",
              "月次報酬合計: 0 円",
              "対象月の完了受注はありません")
          .doesNotContain("印刷禁止の本名", "actor");
    }
  }

  @Test
  void allOrdersBeyondApiLimitIgnorePageParameters() throws Exception {
    rows(2001);
    var result =
        mvc.perform(request("store").param("page", "99").param("size", "1"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    try (var pdf = Loader.loadPDF(result)) {
      String text = new PDFTextStripper().getText(pdf);
      assertThat(text).contains("全 2001 件", "14,007,000 円");
      assertThat(text.lines().filter(line -> line.contains("ORDER-")).count()).isEqualTo(2001);
      for (int i = 0; i < 2001; i++) assertThat(text).contains("ORDER-" + String.format("%05d", i));
      assertThat(pdf.getNumberOfPages()).isGreaterThan(20);
    }
  }

  @Test
  void platformCannotReadOutsideGrantedStores() throws Exception {
    mvc.perform(
            request("platform")
                .with(
                    r -> {
                      r.setParameter("store_id", "2");
                      return r;
                    }))
        .andExpect(status().isForbidden());
  }

  @Test
  void unrelatedSelfStoreIsNotFound() throws Exception {
    when(self.storeName(34L, 1L)).thenThrow(new NotFoundException("報酬明細の店舗が見つかりません"));
    mvc.perform(request("self")).andExpect(status().isNotFound());
  }

  @Test
  void extraIdentityCannotImpersonateAnotherCast() throws Exception {
    rows(1);
    var response =
        mvc.perform(
                request("self")
                    .with(
                        r -> {
                          r.setParameter("person_id", "999");
                          return r;
                        })
                    .param("cast_id", "999"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    try (var pdf = Loader.loadPDF(response)) {
      assertThat(new PDFTextStripper().getText(pdf)).contains("ORDER-00000");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"store", "platform", "self"})
  void wrongPermissionDoesNotGrantPdf(String scope) throws Exception {
    mvc.perform(
            request(scope).with(jwt().authorities(new SimpleGrantedAuthority("PERM_CAST_MANAGE"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void invalidMonthIsJsonError() throws Exception {
    mvc.perform(
            request("store")
                .with(
                    r -> {
                      r.setParameter("month", "0000-01");
                      return r;
                    }))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").isString());
  }

  @Test
  void oversizedOutputFailsCompletelyThenCanRetry() throws Exception {
    properties.getMonthlyPdf().setMaxBytes(1);
    mvc.perform(request("self"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").isString())
        .andExpect(header().doesNotExist("Content-Disposition"));
    properties.getMonthlyPdf().setMaxBytes(16L * 1024 * 1024);
    mvc.perform(request("self")).andExpect(status().isOk());
  }
}
