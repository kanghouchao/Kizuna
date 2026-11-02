package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.advertising.api.dto.AdvertisingRequests.CreateRequest;
import com.kizuna.advertising.application.AdvertisingRecruitmentEffectService;
import com.kizuna.advertising.application.AdvertisingService;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingChangeRepository;
import com.kizuna.advertising.domain.AdvertisingRecruitmentEffectReport.Row;
import com.kizuna.advertising.domain.AdvertisingRecruitmentEffectReport.Status;
import com.kizuna.advertising.infrastructure.AdvertisingRecords;
import com.kizuna.medium.domain.MediumRepository;
import com.kizuna.medium.lookup.MediumLookup;
import com.kizuna.receipt.domain.OperationReceiptRepository;
import com.kizuna.receipt.once.OperationReceipts;
import com.kizuna.recruitment.reporting.RecruitmentHireReader;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.store.StoreShortNames;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** 実 PG（Liquibase の baseline）で、採用効果の媒体 ID による費用のまとまりと入店月の判定を確かめる。今日は東京 2026-07-01。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdvertisingRecruitmentEffectIntegrationTest {
  private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
  private ConfigurableApplicationContext context;
  private JdbcTemplate jdbc;
  private AdvertisingService costs;
  private AdvertisingRecruitmentEffectService effects;
  private long actorId;

  @BeforeAll
  void connect() {
    context =
        new SpringApplicationBuilder(Config.class)
            .web(WebApplicationType.NONE)
            .properties(
                IntegrationTestDatabase.springDataSourceProperties(
                    AdvertisingRecruitmentEffectIntegrationTest.class))
            .properties(
                Map.ofEntries(
                    Map.entry("spring.config.name", "recruitment-effect-test"),
                    Map.entry(
                        "spring.liquibase.change-log",
                        "classpath:db/changelog/db.changelog-master.yaml"),
                    Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                    Map.entry("spring.jpa.open-in-view", "false"),
                    Map.entry("logging.level.root", "WARN")))
            .run();
    jdbc = context.getBean(JdbcTemplate.class);
    costs = context.getBean(AdvertisingService.class);
    effects = context.getBean(AdvertisingRecruitmentEffectService.class);
    actorId =
        jdbc.queryForObject(
            "insert into t_users(email,display_name,user_type,store_scope_type) values ('effect@example.test','採用効果担当','STAFF','ALL_STORES') returning id",
            Long.class);
    when(context.getBean(ActorIdentityService.class).requireUserId(any())).thenReturn(actorId);
  }

  @BeforeEach
  void authenticate() {
    authenticate("PERM_ADVERTISING_COST_VIEW", "PERM_RECRUITMENT_VIEW");
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @Test
  void costsAndHiresGroupByMediumIdWithinTheStoreAndNonMediaHiresStayUnnamed() {
    long store = store();
    long other = store();
    String recruiting = medium(store, "求人A");
    String costOnly = medium(store, "費用だけ");
    cost(store, "2026-05", AdvertisingCategory.RECRUITMENT, recruiting, 30000);
    cost(store, "2026-05", AdvertisingCategory.SALES, recruiting, 99999);
    cost(store, "2026-05", AdvertisingCategory.RECRUITMENT, costOnly, 500);
    cost(store, "2026-04", AdvertisingCategory.RECRUITMENT, recruiting, 70000);
    String foreign = medium(other, "求人A");
    cost(other, "2026-05", AdvertisingCategory.RECRUITMENT, foreign, 1);
    hire(store, "MEDIA", recruiting, "HIRED", "ENROLLED", "2026-05-10", null);
    hire(store, "MEDIA", recruiting, "HIRED", "ENROLLED", "2026-05-11", null);
    hire(store, "REFERRAL", null, "HIRED", "ENROLLED", "2026-05-13", null);
    hire(store, "MEDIA", recruiting, "WITHDRAWN", "ENROLLED", "2026-05-14", null);
    hire(other, "MEDIA", foreign, "HIRED", "ENROLLED", "2026-05-15", null);

    var report = effects.view(store, "2026-05").report();

    assertThat(report.recordedRecruitmentAmount()).isEqualTo(30500);
    assertThat(report.hireCount()).isEqualTo(3);
    assertThat(report.unnamedMediaHireCount()).isEqualTo(1);
    assertThat(report.rows())
        .extracting(r -> r.medium().id(), r -> r.medium().name())
        .containsExactly(tuple(recruiting, "求人A"), tuple(costOnly, "費用だけ"));
    assertThat(report.rows().get(0))
        .returns(30000L, Row::recordedRecruitmentAmount)
        .returns(2L, Row::hireCount)
        .returns("15000.00", Row::costPerHire)
        .returns(Status.CALCULATED, Row::calculationStatus);
  }

  @Test
  void hiresCountOnlyEnrollmentsStartedWithinTheMonth() {
    long store = store();
    String boundary = medium(store, "月境界");
    for (var startedOn : List.of("2026-04-30", "2026-05-01", "2026-05-31", "2026-06-01"))
      hire(store, "MEDIA", boundary, "HIRED", "ENROLLED", startedOn, null);

    var report = effects.view(store, "2026-05").report();

    assertThat(report.hireCount()).isEqualTo(2);
    assertThat(report.unnamedMediaHireCount()).isZero();
    assertThat(report.rows())
        .singleElement()
        .returns(2L, Row::hireCount)
        .returns(Status.NO_COST_RECORDS, Row::calculationStatus);
  }

  @Test
  void hireReaderRefusesCallersWithoutRecruitmentView() {
    long store = store();
    authenticate("PERM_ADVERTISING_COST_VIEW");
    assertThatThrownBy(() -> effects.view(store, "2026-05"))
        .isInstanceOf(AccessDeniedException.class);
  }

  private void authenticate(String... authorities) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "effect@example.test",
                null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
  }

  private long store() {
    return jdbc.queryForObject(
        "insert into t_stores(name,short_name,domain) values ('採用効果検証',?,?) returning id",
        Long.class,
        StoreShortNames.next(),
        UUID.randomUUID() + ".example.test");
  }

  private String medium(long store, String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "insert into t_media(id,store_id,name,status) values (?,?,?,'ACTIVE')", id, store, name);
    return id;
  }

  private void cost(
      long store, String month, AdvertisingCategory category, String mediumId, int amount) {
    costs.create(
        store,
        new CreateRequest(month, category, mediumId, null, null, null, amount, UUID.randomUUID()),
        "effect@example.test");
  }

  private void hire(
      long store,
      String sourceType,
      String mediumId,
      String status,
      String enrollmentStatus,
      String startedOn,
      String endedAt) {
    String enrollment = UUID.randomUUID().toString();
    jdbc.update(
        "insert into t_cast_enrollments(id,store_id,status,started_on,ended_at) values (?,?,?,?,?)",
        enrollment,
        store,
        enrollmentStatus,
        LocalDate.parse(startedOn),
        endedAt == null ? null : OffsetDateTime.parse(endedAt));
    jdbc.update(
        """
        insert into t_applicants(id,store_id,created_at,updated_at,version,name,channel,source_type,
          medium_id,referrer,phone,status,modified_by,edit_sequence,enrollment_id)
        values (?,?,now(),now(),0,'非公開の氏名','PHONE',?,?,?,'090-0000-0000',?,?,0,?)
        """,
        UUID.randomUUID().toString(),
        store,
        sourceType,
        mediumId,
        "REFERRAL".equals(sourceType) ? "紹介者" : null,
        status,
        actorId,
        enrollment);
  }

  @Configuration
  @EnableAutoConfiguration
  @EnableMethodSecurity
  @EntityScan("com.kizuna")
  @EnableJpaRepositories(
      basePackageClasses = {
        AdvertisingChangeRepository.class,
        MediumRepository.class,
        OperationReceiptRepository.class
      })
  @Import({
    AdvertisingService.class,
    AdvertisingRecords.class,
    MediumLookup.class,
    AdvertisingRecruitmentEffectService.class,
    RecruitmentHireReader.class,
    OperationReceipts.class
  })
  static class Config {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-07-01T03:00:00Z"), TOKYO);
    }

    @Bean
    AppProperties appProperties() {
      return new AppProperties();
    }

    @Bean
    ActorIdentityService actors() {
      return mock(ActorIdentityService.class);
    }

    @Bean
    BusinessAudit audit() {
      return mock(BusinessAudit.class);
    }
  }
}
