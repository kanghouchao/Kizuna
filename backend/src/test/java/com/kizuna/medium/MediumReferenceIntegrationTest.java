package com.kizuna.medium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.kizuna.medium.application.MediumService;
import com.kizuna.medium.domain.MediumRepository;
import com.kizuna.order.domain.AcquisitionSource;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.store.StoreShortNames;
import com.kizuna.user.application.BusinessAudit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;

/** 実 PG（Liquibase の baseline）で、受注・応募者の媒体参照が店舗内に閉じ、経路との組と媒体の削除拒否を DB が守ることを確かめる。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MediumReferenceIntegrationTest {
  private ConfigurableApplicationContext context;
  private JdbcTemplate jdbc;
  private MediumService media;
  private long actorId;

  @BeforeAll
  void connect() {
    context =
        new SpringApplicationBuilder(Config.class)
            .web(WebApplicationType.NONE)
            .properties(
                IntegrationTestDatabase.springDataSourceProperties(
                    MediumReferenceIntegrationTest.class))
            .properties(
                Map.ofEntries(
                    Map.entry("spring.config.name", "medium-reference-test"),
                    Map.entry(
                        "spring.liquibase.change-log",
                        "classpath:db/changelog/db.changelog-master.yaml"),
                    Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                    Map.entry("spring.jpa.open-in-view", "false"),
                    Map.entry("logging.level.root", "WARN")))
            .run();
    jdbc = context.getBean(JdbcTemplate.class);
    media = context.getBean(MediumService.class);
    actorId =
        jdbc.queryForObject(
            "insert into t_users(email,display_name,user_type,store_scope_type) values (?,'採用担当','STAFF','ALL_STORES') returning id",
            Long.class,
            "medium-reference@example.test");
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @Test
  void orderRouteAndMediumMustAgreeEvenWhenTheRouteIsNull() {
    long store = store();
    String medium = media.create(store, "受注媒体");
    String order = order(store, AcquisitionSource.MEDIA, medium);

    for (var invalid :
        List.of(
            new Object[] {null, medium},
            new Object[] {"REPEAT", medium},
            new Object[] {"MEDIA", null},
            new Object[] {"UNKNOWN", null}))
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "update t_orders set acquisition_source=?, medium_id=? where id=?",
                      invalid[0],
                      invalid[1],
                      order))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("ck_t_orders_acquisition");
    jdbc.update("update t_orders set acquisition_source=null, medium_id=null where id=?", order);
    jdbc.update("update t_orders set acquisition_source='OTHER', medium_id=null where id=?", order);
  }

  @Test
  void anotherStoresMediumIsRejectedByTheCompositeForeignKeys() {
    long store = store();
    String foreign = media.create(store(), "他店");
    String order = order(store, null, null);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update t_orders set acquisition_source='MEDIA', medium_id=? where id=?",
                    foreign,
                    order))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("fk_t_orders_medium");
    assertThatThrownBy(() -> applicant(store, foreign))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("fk_t_applicants_medium");
  }

  @Test
  void applicantMediumIsAllowedOnlyForMediaSources() {
    long store = store();
    String medium = media.create(store, "応募媒体");
    String applicant = applicant(store, medium);

    assertThatThrownBy(
            () -> jdbc.update("update t_applicants set source_type='DIRECT' where id=?", applicant))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_t_applicants_source");
    assertThatThrownBy(
            () -> jdbc.update("update t_applicants set medium_id=null where id=?", applicant))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_t_applicants_source");
  }

  @Test
  void aMediumReferencedByAnOrderOrAnApplicantCannotBeDeleted() {
    long store = store();
    String byOrder = media.create(store, "受注から参照");
    String byApplicant = media.create(store, "応募者から参照");
    order(store, AcquisitionSource.MEDIA, byOrder);
    applicant(store, byApplicant);

    for (var id : List.of(byOrder, byApplicant))
      assertThatThrownBy(() -> media.delete(store, id, 0L))
          .isInstanceOf(ConflictException.class)
          .hasMessage("記録から参照されている媒体は削除できません。使わない場合は停止してください");
    assertThat(media.list(store, null)).hasSize(2);
  }

  private String order(long store, AcquisitionSource source, String mediumId) {
    String course = UUID.randomUUID().toString();
    String revision = UUID.randomUUID().toString();
    String order = UUID.randomUUID().toString();
    jdbc.update(
        "insert into t_services(id,store_id,kind,name,duration_minutes,price,remuneration,revision_number) values (?,?,'COURSE','検証コース',60,100,0,1)",
        course,
        store);
    jdbc.update(
        "insert into t_service_revisions(id,store_id,service_id,revision_number,operation,actor_id,occurred_at,after_kind,after_name,after_duration_minutes,after_price,after_remuneration) values (?,?,?,1,'CREATED',?,current_timestamp,'COURSE','検証コース',60,100,0)",
        revision,
        store,
        course,
        actorId);
    jdbc.update(
        "insert into t_orders(id,store_id,business_date,course_name,course_minutes,course_service_id,course_revision_id,course_revision_number,course_price,course_remuneration,course_adoption_basis,course_adopted_at,acquisition_source,medium_id) values (?,?,'2026-11-01','検証コース',60,?,?,1,100,0,'CURRENT_SETTING',current_timestamp,?,?)",
        order,
        store,
        course,
        revision,
        source == null ? null : source.name(),
        mediumId);
    return order;
  }

  private String applicant(long store, String mediumId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        """
        insert into t_applicants(id,store_id,created_at,updated_at,version,name,channel,source_type,
          medium_id,status,modified_by,edit_sequence)
        values (?,?,now(),now(),0,'応募者','PHONE','MEDIA',?,'RECEIVED',?,0)
        """,
        id,
        store,
        mediumId,
        actorId);
    return id;
  }

  private long store() {
    return jdbc.queryForObject(
        "insert into t_stores(name,short_name,domain) values ('媒体参照検証',?,?) returning id",
        Long.class,
        StoreShortNames.next(),
        UUID.randomUUID() + ".example.test");
  }

  @Configuration
  @EnableAutoConfiguration
  @EntityScan("com.kizuna")
  @EnableJpaRepositories(basePackageClasses = MediumRepository.class)
  @Import(MediumService.class)
  static class Config {
    @Bean
    BusinessAudit audit() {
      return mock(BusinessAudit.class);
    }
  }
}
