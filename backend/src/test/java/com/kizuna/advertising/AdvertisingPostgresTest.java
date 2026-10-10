package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.kizuna.advertising.api.dto.AdvertisingRequests.CopyRequest;
import com.kizuna.advertising.api.dto.AdvertisingRequests.CreateRequest;
import com.kizuna.advertising.api.dto.AdvertisingRequests.DeleteRequest;
import com.kizuna.advertising.api.dto.AdvertisingRequests.ReplaceRequest;
import com.kizuna.advertising.application.AdvertisingMediaService;
import com.kizuna.advertising.application.AdvertisingOrderCostService;
import com.kizuna.advertising.application.AdvertisingService;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.infrastructure.AdvertisingRecords;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderCourses;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.reporting.AdvertisingOrderReader;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.persistence.StoreScopeStampListener;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreFilterEnable;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@EnabledIfEnvironmentVariable(named = "KIZUNA_ADVERTISING_TEST_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdvertisingPostgresTest {
  AnnotationConfigApplicationContext context;
  AdvertisingService service;
  StoreContext stores;
  JdbcTemplate jdbc;

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    service = context.getBean(AdvertisingService.class);
    stores = context.getBean(StoreContext.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    jdbc.execute("CREATE UNIQUE INDEX uq_ad_test_month ON t_advertising_months(store_id,month)");
    jdbc.execute(
        "CREATE UNIQUE INDEX uq_ad_test_request ON t_advertising_requests(store_id,actor_id,request_id)");
  }

  @AfterAll
  void close() {
    SecurityContextHolder.clearContext();
    if (context != null) context.close();
  }

  @BeforeEach
  void setup() {
    jdbc.execute(
        "TRUNCATE t_advertising_changes,t_advertising_requests,t_advertising_costs,t_advertising_months");
    jdbc.execute("TRUNCATE t_orders CASCADE");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "reader", "unused", List.of(new SimpleGrantedAuthority("PERM_ORDER_MANAGE"))));
    stores.setStoreId(1L);
    reset(context.getBean(AdvertisingRecords.class));
    reset((BusinessAudit) AopTestUtils.getTargetObject(context.getBean(BusinessAudit.class)));
    context.getBean(AppProperties.class).getAdvertisingCost().setMaxOrders(20000);
  }

  CreateRequest create(String month, Integer count, int amount, UUID key) {
    return new CreateRequest(
        month, AdvertisingCategory.SALES, " 媒体 ", null, "プラン", count, amount, key);
  }

  @Test
  void lifecycleReplaysOldSuccessAndRetainsDeletedHistory() {
    var request = create("2026-09", null, 100, UUID.randomUUID());
    var first = service.create(request, "actor");
    assertThat(first.inquiryCount()).isNull();
    var update =
        new ReplaceRequest(
            AdvertisingCategory.RECRUITMENT,
            "媒体",
            null,
            null,
            0,
            200,
            first.version(),
            "修正",
            UUID.randomUUID());
    var second = service.replace(first.id(), update, "actor");
    assertThat(second.version()).isGreaterThan(first.version());
    assertThat(second.inquiryCount()).isZero();
    assertThat(service.create(request, "actor")).isEqualTo(first);
    assertThat(service.replace(first.id(), update, "actor")).isEqualTo(second);
    var deletion = new DeleteRequest(second.version(), "誤入力", UUID.randomUUID());
    service.delete(first.id(), deletion, "actor");
    service.delete(first.id(), deletion, "actor");
    assertThatThrownBy(() -> service.get(first.id())).isInstanceOf(NotFoundException.class);
    assertThat(service.month("2026-09").recordedTotalAmount()).isZero();
    assertThat(service.month("2026-09").version()).isEqualTo(3);
    var history = service.changes("2026-09", null, 2);
    assertThat(history.content()).hasSize(2);
    assertThat(history.nextCursor()).isNotNull();
    assertThat(service.changes("2026-09", history.nextCursor(), 2).content()).hasSize(1);
    var change = service.changeDetail("2026-09", history.content().getFirst().id());
    assertThat(change.before().amount()).isEqualTo(200);
    assertThat(change.after()).isNull();
    assertThat(change.actorId()).isEqualTo(42);
    assertThatThrownBy(() -> service.changeDetail("2026-10", change.id()))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void rejectsKeyReuseAndStaleVersions() {
    UUID key = UUID.randomUUID();
    var c = service.create(create("2026-09", 0, 0, key), "actor");
    assertThatThrownBy(() -> service.create(create("2026-09", 0, 1, key), "actor"))
        .isInstanceOf(ConflictException.class);
    service.replace(
        c.id(),
        new ReplaceRequest(
            AdvertisingCategory.SALES,
            "媒体",
            null,
            null,
            null,
            0,
            c.version(),
            "修正",
            UUID.randomUUID()),
        "actor");
    assertThatThrownBy(
            () ->
                service.delete(
                    c.id(), new DeleteRequest(c.version(), "削除", UUID.randomUUID()), "actor"))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void copiesOnlyEmptyMonthAndClearsInquiryCounts() {
    service.create(create("2026-09", 0, 100, UUID.randomUUID()), "actor");
    service.create(create("2026-09", 5, 200, UUID.randomUUID()), "actor");
    var request = new CopyRequest(2L, 0L, "引継ぎ", UUID.randomUUID());
    var copied = service.copy("2026-10", request, "actor");
    assertThat(copied.copiedCount()).isEqualTo(2);
    assertThat(service.copy("2026-10", request, "actor")).isEqualTo(copied);
    assertThat(service.list("2026-10", 0, 100).getContent())
        .allSatisfy(c -> assertThat(c.inquiryCount()).isNull());
    assertThat(service.month("2026-10").recordedTotalAmount()).isEqualTo(300);
    assertThatThrownBy(
            () ->
                service.copy("2026-10", new CopyRequest(2L, 1L, "再度", UUID.randomUUID()), "actor"))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(
            () ->
                service.copy("2026-12", new CopyRequest(0L, 0L, "空月", UUID.randomUUID()), "actor"))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void filtersAllStoreResourcesAndHistories() {
    var c = service.create(create("2026-09", 0, 100, UUID.randomUUID()), "actor");
    var change = service.changes("2026-09", null, 20).content().getFirst();
    stores.setStoreId(2L);
    assertThat(service.list("2026-09", 0, 20).getContent()).isEmpty();
    assertThat(service.month("2026-09").recordedTotalAmount()).isZero();
    assertThat(service.snapshot("2026-09").costs()).isEmpty();
    assertThat(service.changes("2026-09", null, 20).content()).isEmpty();
    assertThatThrownBy(() -> service.get(c.id())).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.changeDetail("2026-09", change.id()))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void auditFailureRollsBackCostMonthHistoryAndReceipt() {
    doThrow(new IllegalStateException("監査失敗"))
        .when((BusinessAudit) AopTestUtils.getTargetObject(context.getBean(BusinessAudit.class)))
        .record(any(), any(), any(), any(), any(), any(), any(), any(), any());
    assertThatThrownBy(() -> service.create(create("2026-09", 0, 100, UUID.randomUUID()), "actor"))
        .isInstanceOf(IllegalStateException.class);
    for (var name : List.of("costs", "months", "changes", "requests"))
      assertThat(jdbc.queryForObject("SELECT count(*) FROM t_advertising_" + name, Long.class))
          .isZero();
  }

  @Test
  void concurrentSameKeyCreatesExactlyOneRecordAndAuditHistory() throws Exception {
    var request = create("2026-09", 0, 100, UUID.randomUUID());
    var result =
        race(() -> service.create(request, "actor"), () -> service.create(request, "actor"));
    assertThat(result.get(0)).isEqualTo(result.get(1));
    assertThat(service.month("2026-09").entryCount()).isEqualTo(1);
    assertThat(service.changes("2026-09", null, 20).content()).hasSize(1);
  }

  @Test
  void competingCopiesAndEditsRejectOneOperation() throws Exception {
    var c = service.create(create("2026-09", 1, 100, UUID.randomUUID()), "actor");
    var copies =
        race(
            () ->
                service.copy("2026-10", new CopyRequest(1L, 0L, "複製", UUID.randomUUID()), "actor"),
            () ->
                service.copy("2026-10", new CopyRequest(1L, 0L, "複製", UUID.randomUUID()), "actor"));
    assertThat(copies.stream().filter(v -> v instanceof ConflictException)).hasSize(1);
    assertThat(service.month("2026-10").entryCount()).isEqualTo(1);
    var edits =
        race(
            () ->
                service.replace(
                    c.id(),
                    new ReplaceRequest(
                        AdvertisingCategory.SALES,
                        "媒体",
                        null,
                        null,
                        0,
                        200,
                        c.version(),
                        "変更",
                        UUID.randomUUID()),
                    "actor"),
            () ->
                service.replace(
                    c.id(),
                    new ReplaceRequest(
                        AdvertisingCategory.SALES,
                        "媒体",
                        null,
                        null,
                        0,
                        300,
                        c.version(),
                        "変更",
                        UUID.randomUUID()),
                    "actor"));
    assertThat(edits.stream().filter(v -> v instanceof ConflictException)).hasSize(1);
    assertThat(service.month("2026-09").recordedTotalAmount()).isIn(200L, 300L);
  }

  @Test
  void changedSourceAndCopyLimitNeverPartiallyWrite() {
    service.create(create("2026-09", 0, 100, UUID.randomUUID()), "actor");
    service.create(create("2026-09", 0, 100, UUID.randomUUID()), "actor");
    assertThatThrownBy(
            () ->
                service.copy("2026-10", new CopyRequest(1L, 0L, "複製", UUID.randomUUID()), "actor"))
        .isInstanceOf(ConflictException.class);
    context.getBean(AppProperties.class).getAdvertisingCost().setMaxOrders(1);
    assertThatThrownBy(
            () ->
                service.copy("2026-10", new CopyRequest(2L, 0L, "複製", UUID.randomUUID()), "actor"))
        .isInstanceOf(ServiceUnavailableException.class);
    assertThat(service.month("2026-10").entryCount()).isZero();
    assertThat(service.month("2026-10").version()).isZero();
  }

  @Test
  void exportKeepsRowsAndTotalsInTheSameSnapshotDuringWrites() {
    service.create(create("2026-09", 0, 100, UUID.randomUUID()), "actor");
    doAnswer(
            invocation -> {
              var rows = invocation.callRealMethod();
              try (var pool = Executors.newSingleThreadExecutor()) {
                pool.submit(
                        () -> {
                          stores.setStoreId(1L);
                          try {
                            service.create(create("2026-09", 0, 200, UUID.randomUUID()), "actor");
                          } finally {
                            stores.clear();
                          }
                        })
                    .get(5, TimeUnit.SECONDS);
              }
              return rows;
            })
        .when(context.getBean(AdvertisingRecords.class))
        .all(eq("2026-09"), anyInt());
    var snapshot = service.snapshot("2026-09");
    assertThat(snapshot.costs()).hasSize(1);
    assertThat(snapshot.summary().recordedTotalAmount()).isEqualTo(100);
    assertThat(snapshot.summary().version()).isEqualTo(1);
    assertThat(service.month("2026-09").recordedTotalAmount()).isEqualTo(300);
  }

  @Test
  void mediaSummaryKeepsInquiryAmountAndRevisionTogetherAndRecalculatesAfterDeletion() {
    var first = service.create(create("2026-09", 5, 100, UUID.randomUUID()), "actor");
    var media = new AdvertisingMediaService(service, context.getBean(AppProperties.class));
    stores.setStoreId(2L);
    service.create(create("2026-09", 100, 10000, UUID.randomUUID()), "actor");
    stores.setStoreId(1L);
    doAnswer(
            invocation -> {
              var rows = invocation.callRealMethod();
              try (var pool = Executors.newSingleThreadExecutor()) {
                pool.submit(
                        () -> {
                          stores.setStoreId(1L);
                          try {
                            service.create(
                                create("2026-09", null, 200, UUID.randomUUID()), "actor");
                          } finally {
                            stores.clear();
                          }
                        })
                    .get(5, TimeUnit.SECONDS);
              }
              return rows;
            })
        .when(context.getBean(AdvertisingRecords.class))
        .all(eq("2026-09"), anyInt());
    var before = media.view("2026-09");
    assertThat(before.monthVersion()).isEqualTo(1);
    assertThat(before.report().recordedTotalAmount()).isEqualTo(100);
    assertThat(before.report().rows().getFirst().recordedInquiryCountSum()).isEqualTo(5);
    assertThat(before.report().entryCount()).isEqualTo(1);
    reset(context.getBean(AdvertisingRecords.class));
    var after = media.view("2026-09");
    assertThat(after.monthVersion()).isEqualTo(2);
    assertThat(after.report().recordedTotalAmount()).isEqualTo(300);
    assertThat(after.report().rows().getFirst().unrecordedInquiryEntryCount()).isEqualTo(1);
    service.delete(
        first.id(), new DeleteRequest(first.version(), "修正", UUID.randomUUID()), "actor");
    var deleted = media.view("2026-09");
    assertThat(deleted.report().recordedTotalAmount()).isEqualTo(200);
    assertThat(deleted.report().rows().getFirst().recordedInquiryCountSum()).isNull();
    context.getBean(AppProperties.class).getAdvertisingCost().setMaxOrders(1);
    service.create(create("2026-09", 0, 0, UUID.randomUUID()), "actor");
    assertThatThrownBy(() -> media.view("2026-09")).isInstanceOf(ServiceUnavailableException.class);
  }

  @Test
  void copyAuditFailureRollsBackEveryCopiedRowAndAllowsRetry() {
    service.create(create("2026-09", 0, 100, UUID.randomUUID()), "actor");
    service.create(create("2026-09", 0, 100, UUID.randomUUID()), "actor");
    var audits = (BusinessAudit) AopTestUtils.getTargetObject(context.getBean(BusinessAudit.class));
    var count = new AtomicInteger();
    doAnswer(
            invocation -> {
              if (count.incrementAndGet() == 2) throw new IllegalStateException("監査失敗");
              return null;
            })
        .when(audits)
        .record(any(), any(), any(), any(), any(), any(), any(), any(), any());
    var request = new CopyRequest(2L, 0L, "引継ぎ", UUID.randomUUID());
    assertThatThrownBy(() -> service.copy("2026-10", request, "actor"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(service.month("2026-10").entryCount()).isZero();
    assertThat(service.month("2026-10").version()).isZero();
    assertThat(service.changes("2026-10", null, 20).content()).isEmpty();
    reset(audits);
    assertThat(service.copy("2026-10", request, "actor").copiedCount()).isEqualTo(2);
  }

  List<Object> race(Callable<?> first, Callable<?> second) throws Exception {
    var gate = new CyclicBarrier(2);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var futures = new ArrayList<Future<Object>>();
      for (var work : List.of(first, second))
        futures.add(
            pool.submit(
                () -> {
                  stores.setStoreId(1L);
                  gate.await();
                  try {
                    return work.call();
                  } catch (RuntimeException ex) {
                    return ex;
                  } finally {
                    stores.clear();
                  }
                }));
      var result = new ArrayList<Object>();
      for (var future : futures) result.add(future.get(15, TimeUnit.SECONDS));
      return result;
    }
  }

  String order(
      long store, String month, String media, OrderStatus status, boolean invalid, int amount) {
    stores.setStoreId(store);
    return new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
        .execute(
            tx -> {
              var order =
                  Order.builder()
                      .businessDate(LocalDate.parse(month + "-01"))
                      .mediaName(media)
                      .status(OrderStatus.CONFIRMED)
                      .build();
              order.setStoreId(store);
              order.adoptCourse(OrderCourses.course("検証コース", 60, amount), List.of());
              if (status == OrderStatus.COMPLETED) {
                order.completeWith(0, 0);
                if (invalid) order.invalidateCompletion();
              } else if (status == OrderStatus.CANCELLED)
                order.cancelWith("検証", 42L, OffsetDateTime.now());
              else if (status == OrderStatus.IN_SERVICE)
                order.start("検証", 42L, OffsetDateTime.now());
              context.getBean(EntityManager.class).persist(order);
              return order.getId();
            });
  }

  @Test
  void orderComparisonUsesOriginalMonthValidOrdersAndExcludesRecruitment() {
    var cost = service.create(create("2026-09", 999, 100, UUID.randomUUID()), "actor");
    service.create(
        new CreateRequest(
            "2026-09",
            AdvertisingCategory.RECRUITMENT,
            "媒体",
            null,
            null,
            null,
            10000,
            UUID.randomUUID()),
        "actor");
    order(1, "2026-09", "媒体", OrderStatus.COMPLETED, false, 100);
    var free = order(1, "2026-09", "媒体", OrderStatus.COMPLETED, false, 0);
    order(1, "2026-09", "媒体", OrderStatus.COMPLETED, true, 100);
    order(1, "2026-09", "媒体", OrderStatus.CANCELLED, false, 100);
    order(1, "2026-09", "媒体", OrderStatus.CONFIRMED, false, 100);
    order(1, "2026-09", "媒体", OrderStatus.IN_SERVICE, false, 100);
    order(1, "2026-10", "媒体", OrderStatus.COMPLETED, false, 100);
    order(1, "2026-09", null, OrderStatus.COMPLETED, false, 0);
    order(1, "2026-09", " 媒体 ", OrderStatus.COMPLETED, false, 100);
    order(2, "2026-09", "媒体", OrderStatus.COMPLETED, false, 100);
    stores.setStoreId(1L);
    var compare = context.getBean(AdvertisingOrderCostService.class);
    var report = compare.view("2026-09").report();
    assertThat(report.costEntryCount()).isEqualTo(1);
    assertThat(report.recordedSalesAmount()).isEqualTo(100);
    assertThat(report.validCompletedOrderCount()).isEqualTo(4);
    assertThat(report.zeroAmountOrderCount()).isEqualTo(2);
    assertThat(report.unnamedMediaOrderCount()).isEqualTo(1);
    assertThat(report.rows().getLast().costPerOrder()).isEqualTo("50.00");
    jdbc.update("update t_orders set completion_invalidated=true where id=?", free);
    assertThat(compare.view("2026-09").report().rows().getLast().costPerOrder())
        .isEqualTo("100.00");
    service.delete(cost.id(), new DeleteRequest(cost.version(), "訂正", UUID.randomUUID()), "actor");
    assertThat(compare.view("2026-09").report().recordedSalesAmount()).isNull();
  }

  @Test
  void orderAndCostChangesShareOneSnapshotAndReadLimitFailsClosed() throws Exception {
    service.create(create("2026-09", null, 100, UUID.randomUUID()), "actor");
    order(1, "2026-09", "媒体", OrderStatus.COMPLETED, false, 100);
    var changed = new AtomicInteger();
    doAnswer(
            invocation -> {
              var rows = invocation.callRealMethod();
              if (changed.getAndIncrement() == 0) {
                try (var pool = Executors.newSingleThreadExecutor()) {
                  pool.submit(
                          () -> {
                            try {
                              stores.setStoreId(1L);
                              service.create(
                                  create("2026-09", null, 200, UUID.randomUUID()), "actor");
                              order(1, "2026-09", "媒体", OrderStatus.COMPLETED, false, 0);
                            } finally {
                              stores.clear();
                            }
                          })
                      .get(5, TimeUnit.SECONDS);
                }
              }
              return rows;
            })
        .when(context.getBean(AdvertisingRecords.class))
        .all(eq("2026-09"), anyInt());
    var compare = context.getBean(AdvertisingOrderCostService.class);
    var first = compare.view("2026-09");
    assertThat(first.monthVersion()).isEqualTo(1);
    assertThat(first.report().rows().getFirst().costPerOrder()).isEqualTo("100.00");
    assertThat(first.report().validCompletedOrderCount()).isEqualTo(1);
    reset(context.getBean(AdvertisingRecords.class));
    var second = compare.view("2026-09");
    assertThat(second.monthVersion()).isEqualTo(2);
    assertThat(second.report().rows().getFirst().costPerOrder()).isEqualTo("150.00");
    context.getBean(AppProperties.class).getAdvertisingCost().setMaxOrders(1);
    assertThatThrownBy(() -> compare.view("2026-09"))
        .isInstanceOf(ServiceUnavailableException.class);
    jdbc.execute("delete from t_advertising_costs");
    assertThatThrownBy(() -> compare.view("2026-09"))
        .isInstanceOf(ServiceUnavailableException.class);
    context.getBean(AppProperties.class).getAdvertisingCost().setMaxOrders(20000);
    assertThat(compare.view("2026-09").report().validCompletedOrderCount()).isEqualTo(2);
    stores.clear();
    assertThatThrownBy(() -> compare.view("2026-09")).isInstanceOf(RuntimeException.class);
  }

  @Configuration
  @EnableTransactionManagement
  @EnableAspectJAutoProxy
  @EnableMethodSecurity
  @Import({
    AdvertisingService.class,
    AdvertisingOrderCostService.class,
    AdvertisingOrderReader.class,
    StoreScopeStampListener.class,
    StoreFilterEnable.class
  })
  static class Config {
    @Bean
    AdvertisingRecords records(EntityManager em) {
      return spy(new AdvertisingRecords(em));
    }

    @Bean
    DataSource dataSource() {
      return new DriverManagerDataSource(
          System.getenv("KIZUNA_ADVERTISING_TEST_JDBC_URL"), "postgres", "");
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(
        DataSource source, ConfigurableListableBeanFactory beans) {
      var f = new LocalContainerEntityManagerFactoryBean();
      f.setDataSource(source);
      f.setPackagesToScan("com.kizuna.advertising.domain", "com.kizuna.order.domain");
      f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      f.setJpaPropertyMap(
          Map.of(
              "hibernate.hbm2ddl.auto",
              "create-drop",
              "hibernate.resource.beans.container",
              new SpringBeanContainer(beans),
              "hibernate.physical_naming_strategy",
              "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
      return f;
    }

    @Bean
    @Primary
    EntityManager entityManager(EntityManagerFactory f) {
      return SharedEntityManagerCreator.createSharedEntityManager(f);
    }

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory f) {
      return new JpaTransactionManager(f);
    }

    @Bean
    StoreContext storeContext() {
      return new StoreContext();
    }

    @Bean
    AppProperties appProperties() {
      return new AppProperties();
    }

    @Bean
    ActorIdentityService actors() {
      var a = mock(ActorIdentityService.class);
      when(a.requireUserId(any())).thenReturn(42L);
      return a;
    }

    @Bean
    BusinessAudit audit() {
      return mock(BusinessAudit.class);
    }

    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }

    @Bean
    ObjectMapper mapper() {
      return JsonMapper.builder().build();
    }
  }
}
