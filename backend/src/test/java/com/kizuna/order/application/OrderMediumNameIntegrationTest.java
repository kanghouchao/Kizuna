package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.kizuna.customer.domain.CustomerContactRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.medium.application.MediumService;
import com.kizuna.medium.domain.MediumRepository;
import com.kizuna.medium.lookup.MediumLookup;
import com.kizuna.order.api.dto.OrderMapperImpl;
import com.kizuna.order.api.dto.OrderMediumResponse;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.infrastructure.OrderSearchQuery;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.store.StoreShortNames;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.application.ReceptionistEligibilityService;
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
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;

/** 実 PG（Liquibase の baseline）で、受注詳細の応答の媒体名が媒体の改名・停止の後も現在名であることを確かめる。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderMediumNameIntegrationTest {
  private ConfigurableApplicationContext context;
  private JdbcTemplate jdbc;
  private MediumService media;
  private OrderQueryService orders;
  private long actorId;

  @BeforeAll
  void connect() {
    context =
        new SpringApplicationBuilder(Config.class)
            .web(WebApplicationType.NONE)
            .properties(
                IntegrationTestDatabase.springDataSourceProperties(
                    OrderMediumNameIntegrationTest.class))
            .properties(
                Map.ofEntries(
                    Map.entry("spring.config.name", "order-medium-name-test"),
                    Map.entry(
                        "spring.liquibase.change-log",
                        "classpath:db/changelog/db.changelog-master.yaml"),
                    Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                    Map.entry("spring.jpa.open-in-view", "false"),
                    Map.entry("logging.level.root", "WARN")))
            .run();
    jdbc = context.getBean(JdbcTemplate.class);
    media = context.getBean(MediumService.class);
    orders = context.getBean(OrderQueryService.class);
    actorId =
        jdbc.queryForObject(
            "insert into t_users(email,display_name,user_type) values (?,'受付担当','STAFF') returning id",
            Long.class,
            "order-medium-name@example.test");
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @Test
  void detailNamesTheMediumByItsCurrentNameAfterARenameAndAStop() {
    long store = store();
    String medium = media.create(store, "改名前");
    String order = order(store, "MEDIA", medium);
    String none = order(store, null, null);

    media.rename(store, medium, "改名後", 0L);
    media.stop(store, medium, 1L);

    var response = new OrderMapperImpl().toResponse(orders.get(store, order));
    assertThat(response.getMedium()).isEqualTo(new OrderMediumResponse(medium, "改名後"));
    assertThat(new OrderMapperImpl().toResponse(orders.get(store, none)).getMedium()).isNull();
  }

  private String order(long store, String source, String mediumId) {
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
        source,
        mediumId);
    return order;
  }

  private long store() {
    return jdbc.queryForObject(
        "insert into t_stores(name,short_name,domain) values ('受注媒体名検証',?,?) returning id",
        Long.class,
        StoreShortNames.next(),
        UUID.randomUUID() + ".example.test");
  }

  @Configuration
  @EnableAutoConfiguration
  @EntityScan("com.kizuna")
  @EnableJpaRepositories(basePackageClasses = {MediumRepository.class, OrderRepository.class})
  @Import({MediumService.class, MediumLookup.class, OrderQueryService.class})
  static class Config {
    @Bean
    BusinessAudit audit() {
      return mock(BusinessAudit.class);
    }

    // 以下は受注詳細のうち媒体名以外を組む協力者。媒体名の解決には関わらない。
    @Bean
    OrderSearchQuery orderSearchQuery() {
      return mock(OrderSearchQuery.class);
    }

    @Bean
    CustomerRepository customerRepository() {
      return mock(CustomerRepository.class);
    }

    @Bean
    CustomerContactRepository customerContactRepository() {
      return mock(CustomerContactRepository.class);
    }

    @Bean
    NominatableCastLookup nominatableCastLookup() {
      return mock(NominatableCastLookup.class);
    }

    @Bean
    ReceptionistEligibilityService receptionistEligibilityService() {
      return mock(ReceptionistEligibilityService.class);
    }

    @Bean
    BusinessDateService businessDateService() {
      return mock(BusinessDateService.class);
    }

    @Bean
    GuestApplicationConsent guestApplicationConsent() {
      return mock(GuestApplicationConsent.class);
    }

    @Bean
    BusinessContactPermissions businessContactPermissions() {
      return mock(BusinessContactPermissions.class);
    }

    @Bean
    OrderSpecialServices orderSpecialServices() {
      return mock(OrderSpecialServices.class);
    }
  }
}
