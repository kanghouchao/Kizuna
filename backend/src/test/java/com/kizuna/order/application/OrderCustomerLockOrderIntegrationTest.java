package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastProfileRepository;
import com.kizuna.customer.application.CustomerCommands.MergeExecution;
import com.kizuna.customer.application.CustomerCommands.MergeSelection;
import com.kizuna.customer.application.CustomerContactService;
import com.kizuna.customer.application.CustomerMergeService;
import com.kizuna.customer.application.MergeConfirmation;
import com.kizuna.customer.application.MergePreparation;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.member.application.MemberLookupService;
import com.kizuna.order.domain.OrderApplicationRepository;
import com.kizuna.order.domain.OrderFeeLineKind;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderVersionConflictException;
import com.kizuna.order.infrastructure.ReceiptTokenGenerator;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.service.application.OrderServiceCatalog;
import com.kizuna.service.application.OrderServiceTerms;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.shared.persistence.Timestamps;
import com.kizuna.shift.application.ConfirmedShiftLookupService;
import com.kizuna.store.StoreShortNames;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.application.ReceptionistEligibilityService;
import com.kizuna.user.domain.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * 受注の作成・編集・完了と申請の確定が、指名の在籍 → 顧客 → 受注の順に行を押さえ、顧客統合と待ちが環にならないことを実 PostgreSQL で確かめる。
 *
 * <p>店舗単位のロックは模擬に置き換えている。実物は同じ店舗の受注操作を先に直列化してしまい、顧客行での待ちが観測できない。
 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderCustomerLockOrderIntegrationTest {
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private TransactionTemplate transaction;
  private OrderService orders;
  private CustomerMergeService merges;
  private ExecutorService executor;
  private long storeId;
  private long actorId;
  private String courseId;
  private String revisionId;
  private final LocalDate businessDate = LocalDate.now();

  enum NewOrderPath {
    CREATE,
    CREATE_PREVIEW,
    CONFIRMATION,
    CONFIRMATION_PREVIEW
  }

  enum ExistingOrderPath {
    UPDATE,
    UPDATE_PREVIEW,
    COMPLETION,
    COMPLETION_PREVIEW
  }

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    orders = context.getBean(OrderService.class);
    merges = context.getBean(CustomerMergeService.class);
    actorId =
        jdbc.queryForObject(
            "insert into t_users(email,display_name,user_type) values (?,'検証担当','STAFF') returning id",
            Long.class,
            UUID.randomUUID() + "@example.test");
    when(context.getBean(ActorIdentityService.class).requireActor(anyLong()))
        .thenReturn(new AuditActor(actorId, "STAFF", "検証担当"));
    storeId =
        jdbc.queryForObject(
            "insert into t_stores(name,short_name,domain) values ('検証店舗',?,?) returning id",
            Long.class,
            StoreShortNames.next(),
            UUID.randomUUID() + ".example.test");
    courseId = UUID.randomUUID().toString();
    revisionId = UUID.randomUUID().toString();
    jdbc.update(
        "insert into t_services(id,store_id,kind,name,duration_minutes,price,remuneration,revision_number) values (?,?,'COURSE','検証コース',60,100,0,1)",
        courseId,
        storeId);
    jdbc.update(
        "insert into t_service_revisions(id,store_id,service_id,revision_number,operation,actor_id,occurred_at,after_kind,after_name,after_duration_minutes,after_price,after_remuneration) values (?,?,?,1,'CREATED',?,current_timestamp,'COURSE','検証コース',60,100,0)",
        revisionId,
        storeId,
        courseId,
        actorId);
    when(context.getBean(OrderServiceCatalog.class).current(any(), any(), any()))
        .thenReturn(
            new OrderServiceTerms(
                courseId, revisionId, 1, "検証コース", 60, 100, 0, Timestamps.now(), false));
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @BeforeEach
  void startExecutor() {
    executor = Executors.newFixedThreadPool(4);
  }

  @AfterEach
  void stopExecutor() {
    executor.shutdownNow();
  }

  @ParameterizedTest
  @EnumSource(ExistingOrderPath.class)
  void waitsOnTheCustomerRowWithoutHoldingTheOrderRow(ExistingOrderPath path) throws Exception {
    String customer = customer();
    String order = order(customer);
    var release = new CountDownLatch(1);
    Future<?> holder =
        hold("select id from t_customers where id = ? for update", customer, release);

    Future<?> run = executor.submit(() -> run(path, order));

    assertThat(awaitBlockedStatements(1)).singleElement().asString().contains("t_customers");
    // 逆順の実装でも外部キー検査で結局待つので、「待つ」だけでは順序を固定できない（ADR 0016）。
    jdbc.queryForObject(
        "select id from t_orders where id = ? for update nowait", String.class, order);
    release.countDown();
    holder.get(10, TimeUnit.SECONDS);
    run.get(10, TimeUnit.SECONDS);
  }

  @Test
  void updateWaitsOnTheReselectedCustomerWithoutHoldingTheOrderRow() throws Exception {
    String current = customer();
    String reselected = customer();
    String order = order(current);
    var release = new CountDownLatch(1);
    Future<?> holder =
        hold("select id from t_customers where id = ? for update", reselected, release);
    var selection = new CustomerSelection(CustomerSelection.Mode.EXISTING, reselected, null);

    Future<?> update =
        executor.submit(() -> orders.update(storeId, order, update(selection), actorId, true));

    assertThat(awaitBlockedStatements(1)).singleElement().asString().contains("t_customers");
    jdbc.queryForObject(
        "select id from t_orders where id = ? for update nowait", String.class, order);
    release.countDown();
    holder.get(10, TimeUnit.SECONDS);
    update.get(10, TimeUnit.SECONDS);
    assertThat(
            jdbc.queryForObject(
                "select customer_id from t_orders where id = ?", String.class, order))
        .isEqualTo(reselected);
  }

  @Test
  void completionRejectsAnOrderRepointedWhileItWaitedOnTheCustomerRow() throws Exception {
    String read = customer();
    String repointed = customer();
    String order = order(read);
    var release = new CountDownLatch(1);
    Future<?> holder = hold("select id from t_customers where id = ? for update", read, release);
    Future<?> completion = executor.submit(() -> run(ExistingOrderPath.COMPLETION, order));
    awaitBlockedStatements(1);

    // 版は据え置く。後段の版の照合ではなく、押さえた受注が読んだ時の顧客を指すかの確認だけで撥ねさせる。
    jdbc.update("update t_orders set customer_id = ? where id = ?", repointed, order);
    release.countDown();
    holder.get(10, TimeUnit.SECONDS);

    assertThatThrownBy(() -> completion.get(10, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(OrderVersionConflictException.class);
    assertThat(status(order)).isEqualTo("CONFIRMED");
  }

  @Test
  void completionOfAnOrderWithoutACustomerTakesNoCustomerRow() {
    String order = order(null);

    run(ExistingOrderPath.COMPLETION, order);

    assertThat(status(order)).isEqualTo("COMPLETED");
  }

  @Test
  void twoCompletionsOfOneCustomerBothSucceed() throws Exception {
    String customer = customer();
    String first = order(customer);
    String second = order(customer);
    var release = new CountDownLatch(1);
    // 1 件目を受注行で止め、顧客行を握ったままにする。2 件目はその顧客行を待つ。
    Future<?> holder = hold("select id from t_orders where id = ? for update", first, release);
    Future<?> firstRun = executor.submit(() -> run(ExistingOrderPath.COMPLETION, first));
    awaitBlockedStatements(1);

    Future<?> secondRun = executor.submit(() -> run(ExistingOrderPath.COMPLETION, second));

    assertThat(awaitBlockedStatements(2))
        .anySatisfy(sql -> assertThat(sql).contains("t_customers"));
    release.countDown();
    holder.get(10, TimeUnit.SECONDS);
    firstRun.get(10, TimeUnit.SECONDS);
    secondRun.get(10, TimeUnit.SECONDS);
    assertThat(status(first)).isEqualTo("COMPLETED");
    assertThat(status(second)).isEqualTo("COMPLETED");
  }

  @Test
  void completionWaitingBehindAMergeIsRejectedAsAVersionMismatch() throws Exception {
    String surviving = customer();
    String merged = customer();
    String order = order(merged);
    var execution = mergeExecution(surviving, merged);
    var release = new CountDownLatch(1);
    // 統合を受注行で止め、両方の顧客行を握ったままにする。完了は被統合行を待つ。
    Future<?> holder = hold("select id from t_orders where id = ? for update", order, release);
    Future<?> merge = executor.submit(() -> merges.merge(storeId, surviving, execution, actorId));
    awaitBlockedStatements(1);

    Future<?> completion = executor.submit(() -> run(ExistingOrderPath.COMPLETION, order));

    assertThat(awaitBlockedStatements(2))
        .anySatisfy(sql -> assertThat(sql).contains("t_customers"));
    release.countDown();
    holder.get(10, TimeUnit.SECONDS);
    merge.get(10, TimeUnit.SECONDS);
    assertThatThrownBy(() -> completion.get(10, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(OrderVersionConflictException.class);
    assertThat(status(order)).isEqualTo("CONFIRMED");
    assertThat(
            jdbc.queryForObject(
                "select customer_id from t_orders where id = ?", String.class, order))
        .isEqualTo(surviving);
  }

  @Test
  void mergeWaitingBehindACompletionRunsAfterItWithoutADeadlock() throws Exception {
    String surviving = customer();
    String merged = customer();
    String order = order(merged);
    var execution = mergeExecution(surviving, merged);
    var release = new CountDownLatch(1);
    // 完了を受注行で止め、被統合行を握ったままにする。統合はその顧客行を待つ。
    Future<?> holder = hold("select id from t_orders where id = ? for update", order, release);
    Future<?> completion = executor.submit(() -> run(ExistingOrderPath.COMPLETION, order));
    awaitBlockedStatements(1);

    Future<?> merge = executor.submit(() -> merges.merge(storeId, surviving, execution, actorId));

    assertThat(awaitBlockedStatements(2))
        .anySatisfy(sql -> assertThat(sql).contains("t_customers"));
    release.countDown();
    holder.get(10, TimeUnit.SECONDS);
    completion.get(10, TimeUnit.SECONDS);
    // 完了が受注の版を進めたので、統合は確認した内容との食い違いとして拒まれる（デッドロックではない）。
    assertThatThrownBy(() -> merge.get(10, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasRootCauseMessage("関連情報が変更されました。入力を保持して再度プレビューを確認してください");
    assertThat(status(order)).isEqualTo("COMPLETED");
  }

  @ParameterizedTest
  @EnumSource(NewOrderPath.class)
  void newOrderWaitsOnTheCustomerRowHoldingTheEnrollmentButNotTheSourceOrder(NewOrderPath path)
      throws Exception {
    String customer = customer();
    String cast = enrollment();
    String source = invalidatedOrder(null);
    String application = guestApplication();
    var release = new CountDownLatch(1);
    Future<?> holder =
        hold("select id from t_customers where id = ? for update", customer, release);

    Future<?> run = executor.submit(newOrder(path, customer, cast, source, application));

    assertThat(awaitBlockedStatements(1)).singleElement().asString().contains("t_customers");
    assertThat(sqlStateOfLockingWithoutWaiting("t_cast_enrollments", cast)).isEqualTo("55P03");
    assertThat(sqlStateOfLockingWithoutWaiting("t_orders", source)).isNull();
    release.countDown();
    holder.get(10, TimeUnit.SECONDS);
    run.get(10, TimeUnit.SECONDS);
    switch (path) {
      case CREATE -> assertThat(replacementsFor(source)).containsExactly(customer);
      case CONFIRMATION ->
          assertThat(
                  jdbc.queryForObject(
                      "select o.customer_id from t_order_applications a"
                          + " join t_orders o on o.id = a.order_id where a.id = ?",
                      String.class,
                      application))
              .isEqualTo(customer);
      case CREATE_PREVIEW, CONFIRMATION_PREVIEW -> assertThat(replacementsFor(source)).isEmpty();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void createWaitingBehindAMergeFollowsTheMergeOutcome(boolean selectsSurviving) throws Exception {
    String surviving = customer();
    String merged = customer();
    String cast = enrollment();
    // 元受注が統合する顧客に着いていないと、統合が押さえる受注と作成が押さえる受注が重ならない。
    String source = invalidatedOrder(merged);
    var execution = mergeExecution(surviving, merged);
    var release = new CountDownLatch(1);
    // 統合を元受注の行で止め、両方の顧客行を握ったままにする。作成は選んだ顧客行を待つ。
    Future<?> holder = hold("select id from t_orders where id = ? for update", source, release);
    Future<?> merge = executor.submit(() -> merges.merge(storeId, surviving, execution, actorId));
    awaitBlockedStatements(1);

    Future<?> create =
        executor.submit(
            newOrder(
                NewOrderPath.CREATE, selectsSurviving ? surviving : merged, cast, source, null));

    assertThat(awaitBlockedStatements(2))
        .anySatisfy(sql -> assertThat(sql).contains("t_customers"));
    release.countDown();
    holder.get(10, TimeUnit.SECONDS);
    merge.get(10, TimeUnit.SECONDS);
    if (selectsSurviving) {
      create.get(10, TimeUnit.SECONDS);
      assertThat(replacementsFor(source)).containsExactly(surviving);
    } else {
      assertThatThrownBy(() -> create.get(10, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(NotFoundException.class);
      assertThat(replacementsFor(source)).isEmpty();
    }
  }

  @Test
  void mergeWaitingBehindACreateRunsAfterItWithoutADeadlock() throws Exception {
    String surviving = customer();
    String merged = customer();
    String cast = enrollment();
    String source = invalidatedOrder(merged);
    var execution = mergeExecution(surviving, merged);
    var release = new CountDownLatch(1);
    // 作成を元受注の行で止め、選んだ顧客行を握ったままにする。統合はその顧客行を待つ。
    Future<?> holder = hold("select id from t_orders where id = ? for update", source, release);
    Future<?> create =
        executor.submit(newOrder(NewOrderPath.CREATE, surviving, cast, source, null));
    awaitBlockedStatements(1);
    assertThat(sqlStateOfLockingWithoutWaiting("t_customers", surviving)).isEqualTo("55P03");

    Future<?> merge = executor.submit(() -> merges.merge(storeId, surviving, execution, actorId));

    assertThat(awaitBlockedStatements(2))
        .anySatisfy(sql -> assertThat(sql).contains("t_customers"));
    release.countDown();
    holder.get(10, TimeUnit.SECONDS);
    create.get(10, TimeUnit.SECONDS);
    // 作成が存続側に受注を足したので、統合は確認した内容との食い違いとして拒まれる（デッドロックではない）。
    assertThatThrownBy(() -> merge.get(10, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasRootCauseMessage("関連情報が変更されました。入力を保持して再度プレビューを確認してください");
    assertThat(replacementsFor(source)).containsExactly(surviving);
  }

  @Test
  void createRejectsACustomerMergedWhileItWaitedWithoutWaitingOnTheSourceOrder() throws Exception {
    String surviving = customer();
    String merged = customer();
    String cast = enrollment();
    String pinned = order(merged);
    String source = invalidatedOrder(null);
    var execution = mergeExecution(surviving, merged);
    var releaseSource = new CountDownLatch(1);
    var releaseMerge = new CountDownLatch(1);
    // 元受注は終わりまで外から握る。作成が墓標を握ったまま元受注を待てば、この試験は時間切れになる。
    Future<?> sourceHolder =
        hold("select id from t_orders where id = ? for update", source, releaseSource);
    Future<?> mergeHolder =
        hold("select id from t_orders where id = ? for update", pinned, releaseMerge);
    Future<?> merge = executor.submit(() -> merges.merge(storeId, surviving, execution, actorId));
    awaitBlockedStatements(1);
    Future<?> create = executor.submit(newOrder(NewOrderPath.CREATE, merged, cast, source, null));
    assertThat(awaitBlockedStatements(2))
        .anySatisfy(sql -> assertThat(sql).contains("t_customers"));

    releaseMerge.countDown();
    mergeHolder.get(10, TimeUnit.SECONDS);
    merge.get(10, TimeUnit.SECONDS);

    assertThatThrownBy(() -> create.get(10, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(NotFoundException.class);
    releaseSource.countDown();
    sourceHolder.get(10, TimeUnit.SECONDS);
  }

  private Callable<Object> newOrder(
      NewOrderPath path, String customer, String cast, String source, String application) {
    var selection = new CustomerSelection(CustomerSelection.Mode.EXISTING, customer, null);
    var create =
        new OrderCreateCommand(
            source,
            null,
            null,
            courseId,
            null,
            businessDate,
            null,
            null,
            selection,
            null,
            null,
            null,
            null,
            cast,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    var confirmation =
        new OrderApplicationConfirmationCommand(
            List.of(),
            null,
            null,
            courseId,
            null,
            null,
            businessDate,
            null,
            null,
            cast,
            null,
            null,
            null,
            null,
            selection,
            null);
    return switch (path) {
      case CREATE -> () -> orders.create(storeId, create, actorId, true);
      case CREATE_PREVIEW -> () -> orders.previewCreate(storeId, create, actorId, true);
      case CONFIRMATION ->
          () -> orders.confirmApplication(storeId, application, confirmation, actorId, true);
      case CONFIRMATION_PREVIEW ->
          () -> orders.previewConfirmation(storeId, application, confirmation, actorId, true);
    };
  }

  /** その行を待たずに押さえてみて、取れれば null、取れなければ SQLSTATE を返す。押さえた行は直後に放す。 */
  private String sqlStateOfLockingWithoutWaiting(String table, String id) {
    try {
      jdbc.queryForObject(
          "select id from " + table + " where id = ? for update nowait", String.class, id);
      return null;
    } catch (DataAccessException ex) {
      if (ex.getRootCause() instanceof SQLException sql) return sql.getSQLState();
      throw ex;
    }
  }

  /** その受注を元受注にして作られた受注の顧客。 */
  private List<String> replacementsFor(String source) {
    return jdbc.queryForList(
        "select customer_id from t_orders where replacement_for_order_id = ?",
        String.class,
        source);
  }

  private Object run(ExistingOrderPath path, String order) {
    var completion =
        new OrderCompletionCommand(
            0L,
            List.of(
                new OrderFeeLineInput(
                    null, OrderFeeLineKind.CREDIT_SURCHARGE, "会計", 100, null, null, null, null)),
            null,
            null);
    var update = update(null);
    return switch (path) {
      case UPDATE -> orders.update(storeId, order, update, actorId, true);
      case UPDATE_PREVIEW -> orders.previewUpdate(storeId, order, update, actorId, true);
      case COMPLETION -> orders.complete(storeId, order, completion, actorId);
      case COMPLETION_PREVIEW -> orders.completionPreview(storeId, order, completion, actorId);
    };
  }

  private static OrderUpdateCommand update(CustomerSelection selection) {
    return new OrderUpdateCommand(
        null, null, null, 0L, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, selection, null, null);
  }

  private MergeExecution mergeExecution(String surviving, String merged) {
    var selection = new MergeSelection(merged, null, null);
    return new MergeExecution(
        selection, merges.preview(storeId, surviving, selection).previewToken(), "同一人物のため");
  }

  /** 別のトランザクションで 1 行を押さえ、解放の合図まで握り続ける。押さえ終えてから戻る。 */
  private Future<?> hold(String lockingSelect, String id, CountDownLatch release)
      throws InterruptedException {
    var locked = new CountDownLatch(1);
    Future<?> holder =
        executor.submit(
            () ->
                transaction.executeWithoutResult(
                    status -> {
                      jdbc.queryForObject(lockingSelect, String.class, id);
                      locked.countDown();
                      await(release);
                    }));
    assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
    return holder;
  }

  /** ロック待ちの接続がちょうど {@code count} 本になるまで待ち、待っている文を小文字で返す。 */
  private List<String> awaitBlockedStatements(int count) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      List<String> waiting =
          jdbc.queryForList(
              "select lower(query) from pg_stat_activity"
                  + " where datname = current_database() and wait_event_type = 'Lock'",
              String.class);
      if (waiting.size() == count) return waiting;
      Thread.sleep(20);
    }
    throw new AssertionError("ロックを待つトランザクションが " + count + " 本になりません");
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("待ち合わせが時間切れです");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new AssertionError(ex);
    }
  }

  private String customer() {
    String id = UUID.randomUUID().toString();
    jdbc.update("insert into t_customers(id,store_id,name) values (?,?,'検証顧客')", id, storeId);
    return id;
  }

  private String order(String customer) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "insert into t_orders(id,store_id,customer_id,business_date,course_name,course_minutes,course_service_id,course_revision_id,course_revision_number,course_price,course_remuneration,course_adoption_basis,course_adopted_at,status,total_fee) values (?,?,?,current_date,'検証コース',60,?,?,1,100,0,'CURRENT_SETTING',current_timestamp,'CONFIRMED',100)",
        id,
        storeId,
        customer,
        courseId,
        revisionId);
    return id;
  }

  private String invalidatedOrder(String customer) {
    String id = order(customer);
    jdbc.update(
        "update t_orders set status = 'COMPLETED', completed_at = current_timestamp, total_fee = 0,"
            + " completion_invalidated = true where id = ?",
        id);
    return id;
  }

  private String enrollment() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "insert into t_cast_enrollments(id,store_id,started_on) values (?,?,current_date)",
        id,
        storeId);
    return id;
  }

  private String guestApplication() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "insert into t_order_applications(id,store_id,status,business_date,contact_name)"
            + " values (?,?,'PENDING',?,'検証ゲスト')",
        id,
        storeId,
        businessDate);
    return id;
  }

  private String status(String order) {
    return jdbc.queryForObject("select status from t_orders where id = ?", String.class, order);
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(
      basePackageClasses = {
        OrderRepository.class,
        CustomerRepository.class,
        CastEnrollmentRepository.class,
        StoreRepository.class
      })
  @Import({CustomerMergeService.class, MergePreparation.class, MergeConfirmation.class})
  static class Config {
    @Bean
    DataSource dataSource() {
      return IntegrationTestDatabase.dataSource(OrderCustomerLockOrderIntegrationTest.class);
    }

    @Bean
    SpringLiquibase liquibase(DataSource source) {
      var migration = new SpringLiquibase();
      migration.setDataSource(source);
      migration.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
      migration.setContexts("production");
      return migration;
    }

    @Bean
    @DependsOn("liquibase")
    LocalContainerEntityManagerFactoryBean entityManagerFactory(
        DataSource source, ConfigurableListableBeanFactory beans) {
      var factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(source);
      factory.setPackagesToScan("com.kizuna");
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factory.setJpaPropertyMap(
          Map.of(
              "hibernate.resource.beans.container",
              new SpringBeanContainer(beans),
              "hibernate.hbm2ddl.auto",
              "validate",
              "hibernate.physical_naming_strategy",
              "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
      return factory;
    }

    @Bean
    @Primary
    EntityManager entityManager(EntityManagerFactory factory) {
      return SharedEntityManagerCreator.createSharedEntityManager(factory);
    }

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
      return new JpaTransactionManager(factory);
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    AppProperties appProperties() {
      var properties = new AppProperties();
      properties.getJwt().setSecret(UUID.randomUUID() + "-" + UUID.randomUUID());
      return properties;
    }

    @Bean
    BusinessAudit businessAudit() {
      return mock(BusinessAudit.class);
    }

    @Bean
    ActorIdentityService actorIdentityService() {
      return mock(ActorIdentityService.class);
    }

    @Bean
    CustomerContactService customerContactService() {
      return mock(CustomerContactService.class);
    }

    @Bean
    UserRepository userRepository() {
      return mock(UserRepository.class);
    }

    @Bean
    PointLedgerService pointLedgerService() {
      var ledger = mock(PointLedgerService.class);
      when(ledger.usageUnit()).thenReturn(100);
      return ledger;
    }

    @Bean
    OrderServiceCatalog orderServiceCatalog() {
      return mock(OrderServiceCatalog.class);
    }

    @Bean
    MemberLookupService memberLookupService() {
      return mock(MemberLookupService.class);
    }

    /** 受注・顧客・在籍・申請の行に触れる協力者だけを実物にし、金額の試算は模擬のカタログで通す。 */
    @Bean
    OrderService orderService(
        OrderRepository orderRepository,
        OrderApplicationRepository applications,
        CustomerRepository customerRepository,
        CastEnrollmentRepository enrollments,
        CastProfileRepository profiles,
        StoreRepository stores,
        CustomerMemberLinkRepository links,
        PointLedgerService ledger,
        OrderServiceCatalog catalog,
        BusinessAudit audit)
        throws ReflectiveOperationException {
      var specialServices = mock(OrderSpecialServices.class);
      var generator = mock(ReceiptTokenGenerator.class);
      when(generator.generate())
          .thenAnswer(
              call ->
                  new ReceiptTokenGenerator.GeneratedToken(
                      UUID.randomUUID().toString(), UUID.randomUUID().toString()));
      var businessDates = mock(BusinessDateService.class);
      when(businessDates.currentBusinessDate()).thenReturn(LocalDate.now());
      var shifts = mock(ConfirmedShiftLookupService.class);
      when(shifts.hasConfirmedShift(any(), any(), any())).thenReturn(true);
      var receptionists = mock(ReceptionistEligibilityService.class);
      when(receptionists.isEligible(any(), any())).thenReturn(true);
      Map<Class<?>, Object> real = new HashMap<>();
      real.put(OrderRepository.class, orderRepository);
      real.put(OrderApplicationRepository.class, applications);
      real.put(CustomerRepository.class, customerRepository);
      real.put(CustomerMemberLinkRepository.class, links);
      real.put(
          NominatableCastLookup.class, new NominatableCastLookup(enrollments, stores, profiles));
      real.put(PointLedgerService.class, ledger);
      real.put(BusinessAudit.class, audit);
      real.put(ReceiptTokenGenerator.class, generator);
      real.put(OrderSpecialServices.class, specialServices);
      real.put(BusinessDateService.class, businessDates);
      real.put(ConfirmedShiftLookupService.class, shifts);
      real.put(ReceptionistEligibilityService.class, receptionists);
      real.put(
          OrderCalculation.class,
          new OrderCalculation(
              catalog,
              mock(OrderConfirmation.class),
              new OrderFeeLineSelection(catalog),
              specialServices));
      var constructor = OrderService.class.getConstructors()[0];
      var types = constructor.getParameterTypes();
      var arguments = new Object[types.length];
      for (int i = 0; i < types.length; i++) {
        arguments[i] = real.containsKey(types[i]) ? real.get(types[i]) : mock(types[i]);
      }
      return (OrderService) constructor.newInstance(arguments);
    }
  }
}
