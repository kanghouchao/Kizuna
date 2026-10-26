package com.kizuna.memberwithdrawal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.customer.application.CustomerMemberLinkDirectory;
import com.kizuna.customer.application.CustomerMemberLinkService;
import com.kizuna.customer.application.CustomerMemberLinkWithdrawal;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.member.api.dto.MemberRegistrationRequest;
import com.kizuna.member.application.MemberDirectoryService;
import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.application.MemberRegistrationService;
import com.kizuna.member.application.MemberStatusFilter;
import com.kizuna.member.application.MemberWithdrawal;
import com.kizuna.member.domain.MemberAlreadyWithdrawnException;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.memberconsole.api.dto.MemberDetailResponse;
import com.kizuna.memberconsole.application.MemberConsoleService;
import com.kizuna.order.domain.OrderApplicationRepository;
import com.kizuna.order.withdrawal.MemberWithdrawalApplications;
import com.kizuna.point.application.MemberLedgerQueryService;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.point.domain.PointEntry;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.settings.application.PointSettings;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.application.CredentialOperations;
import com.kizuna.user.domain.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** 実 PostgreSQL で、退会の同一取引（台帳・関連・申請・利用者）と退会後の不変量、識別子の部分一意を確かめる。 */
@EnabledIfEnvironmentVariable(named = "KIZUNA_MEMBER_WITHDRAWAL_TEST_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberWithdrawalPostgresTest {
  private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
  private static final LocalDate TODAY = LocalDate.now(ZONE);
  private static final String HASH = new BCryptPasswordEncoder(4).encode("pass12345");
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private TransactionTemplate tx;
  private MemberWithdrawalService service;
  private PointLedgerService ledger;
  private UserRepository users;
  private long store;
  private long otherStore;
  private long staff;
  private String staffEmail;

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = context.getBean(JdbcTemplate.class);
    tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    service = context.getBean(MemberWithdrawalService.class);
    ledger = context.getBean(PointLedgerService.class);
    users = context.getBean(UserRepository.class);
    store = store();
    otherStore = store();
    staffEmail = UUID.randomUUID() + "@example.test";
    staff =
        jdbc.queryForObject(
            "insert into t_users(email,password,display_name,user_type,store_scope_type) values (?,?,'検証職員','STAFF','ALL_STORES') returning id",
            Long.class,
            staffEmail,
            HASH);
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @Test
  void withdrawalClearsPointsReleasesLinksWithdrawsPendingAndStopsUserInOneTransaction() {
    var member = member();
    long lot = credit(member.memberId, 300, null);
    long expiring = credit(member.memberId, 200, TODAY.plusDays(30));
    String customer = customer(store);
    String otherCustomer = customer(otherStore);
    String link = link(store, customer, member);
    String otherLink = link(otherStore, otherCustomer, member);
    String pending = application(member, "PENDING");
    String confirmed = application(member, "CONFIRMED");

    service.withdrawSelf(member.userId, "引っ越しのため");

    assertThat(
            jdbc.queryForMap(
                "select withdrawn_at is not null as withdrawn, withdrawal_reason, withdrawn_by from t_members where id=?",
                member.memberId))
        .containsEntry("withdrawn", true)
        .containsEntry("withdrawal_reason", "引っ越しのため")
        .containsEntry("withdrawn_by", member.userId);
    assertThat(
            jdbc.queryForMap(
                "select enabled, withdrawn_at is not null as withdrawn, credential_version from t_users where id=?",
                member.userId))
        .containsEntry("enabled", false)
        .containsEntry("withdrawn", true)
        .containsEntry("credential_version", 1L);
    var clears =
        jdbc.queryForList(
            "select amount, reason, actor_user_id from t_point_entries where member_id=? and entry_type='WITHDRAWAL_CLEAR'",
            member.memberId);
    assertThat(clears).hasSize(1);
    assertThat(clears.getFirst())
        .containsEntry("amount", -500)
        .containsEntry("reason", "会員退会")
        .containsEntry("actor_user_id", member.userId);
    assertThat(
            jdbc.queryForList(
                "select a.source_entry_id, a.amount from t_point_usage_allocations a join t_point_entries e on e.id=a.entry_id where e.entry_type='WITHDRAWAL_CLEAR' and e.member_id=? order by a.source_entry_id",
                member.memberId))
        .extracting(row -> row.get("source_entry_id"), row -> row.get("amount"))
        .containsExactly(tuple(lot, 300), tuple(expiring, 200));
    assertThat(ledger.balance(member.memberId)).isZero();
    assertThat(
            jdbc.queryForList(
                "select status, release_reason, released_by from t_customer_member_links where id in (?,?)",
                link,
                otherLink))
        .allSatisfy(
            row ->
                assertThat(row)
                    .containsEntry("status", "RELEASED")
                    .containsEntry("release_reason", "会員退会")
                    .containsEntry("released_by", member.userId));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_customers where id in (?,?)",
                Long.class,
                customer,
                otherCustomer))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select status from t_order_applications where id=?", String.class, pending))
        .isEqualTo("WITHDRAWN");
    assertThat(
            jdbc.queryForObject(
                "select status from t_order_applications where id=?", String.class, confirmed))
        .isEqualTo("CONFIRMED");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_audit_events where action='CUSTOMER_MEMBER_LINK_RELEASED' and target_id in (?,?)",
                Long.class,
                link,
                otherLink))
        .isEqualTo(2);
  }

  @Test
  void withdrawnMemberCannotBeLinkedByCodeAnyMore() {
    var member = member();
    String customer = customer(store);
    service.withdrawSelf(member.userId, "やめる");

    assertThatThrownBy(
            () ->
                context
                    .getBean(CustomerMemberLinkService.class)
                    .link(store, customer, member.memberCode, null, null, staffEmail))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("退会済み");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_customer_member_links where member_id=?",
                Long.class,
                member.memberId))
        .isZero();
  }

  @Test
  void zeroBalanceMemberGetsNoEntryAndSecondWithdrawalConflicts() {
    var member = member();

    service.withdrawSelf(member.userId, "利用しなくなった");

    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_point_entries where member_id=?",
                Long.class,
                member.memberId))
        .isZero();
    assertThatThrownBy(() -> service.withdrawSelf(member.userId, "二度目"))
        .as("対象は利用者 ID で固定され、退会済みの会員行は 409 で撥ねる")
        .isInstanceOf(MemberAlreadyWithdrawnException.class);
  }

  @Test
  void sameEmailRegistersAFreshMemberAndOnlyTheActiveRowIsFoundByIdentifier() {
    var member = member();
    jdbc.update(
        "update t_users set line_user_id=? where id=?", "line-" + member.userId, member.userId);
    credit(member.memberId, 100, null);
    service.withdrawSelf(member.userId, "やめる");

    var request = new MemberRegistrationRequest();
    request.setEmail(member.email);
    request.setPassword("pass12345");
    request.setDisplayName("再登録");
    String newCode =
        tx.execute(
            status ->
                context.getBean(MemberRegistrationService.class).register(request).memberCode());
    assertThat(newCode).isNotEqualTo(member.memberCode);
    long newUser = users.findByEmail(member.email).orElseThrow().getId();
    assertThat(newUser).isNotEqualTo(member.userId);
    assertThat(
            jdbc.queryForMap("select rank, withdrawn_at from t_members where user_id=?", newUser))
        .containsEntry("rank", "BRONZE")
        .containsEntry("withdrawn_at", null);
    long newMember =
        jdbc.queryForObject("select id from t_members where user_id=?", Long.class, newUser);
    assertThat(ledger.balance(newMember)).isZero();
    assertThat(users.findCredentialVersionById(member.userId)).contains(1L);
    assertThat(users.findCredentialVersionById(newUser)).contains(0L);
    Long locked =
        tx.execute(status -> users.findByEmailForUpdate(member.email).orElseThrow().getId());
    assertThat(locked).isEqualTo(newUser);
    assertThat(users.findByLineUserId("line-" + member.userId)).isEmpty();
    assertThat(users.existsByLineUserId("line-" + member.userId)).isFalse();
    // 旧行は識別子を保持したまま残る（ADR 0029）。
    assertThat(
            jdbc.queryForObject(
                "select email from t_users where id=?", String.class, member.userId))
        .isEqualTo(member.email);
    // 未退会の行の間では電子メールが一意。
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into t_users(email,password,display_name,user_type,store_scope_type) values (?,?,'重複','MEMBER','SPECIFIC_STORES')",
                    member.email,
                    HASH))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("uq_t_users_email");
  }

  @Test
  void rollbackOfAWithdrawnMembersOrderReclearsWhatItReturnsInTheSameTransaction() {
    var member = member();
    credit(member.memberId, 500, null);
    String order = order(store);
    tx.executeWithoutResult(
        status -> ledger.useForOrder(member.memberId, order, store, 200, staff));
    service.withdrawSelf(member.userId, "やめる");
    assertThat(ledger.balance(member.memberId)).isZero();

    var result = tx.execute(status -> ledger.rollbackForOrder(order, "誤完了", staff, 10000));

    assertThat(result.restoredPoints()).isEqualTo(200);
    assertThat(ledger.balance(member.memberId)).isZero();
    assertThat(
            jdbc.queryForList(
                "select entry_type, amount, reason from t_point_entries where member_id=? order by id",
                member.memberId))
        .extracting(
            row -> row.get("entry_type"), row -> row.get("amount"), row -> row.get("reason"))
        .containsExactly(
            tuple("MANUAL_ADJUST", 500, "検証付与"),
            tuple("USE", -200, null),
            tuple("WITHDRAWAL_CLEAR", -300, "会員退会"),
            tuple("USE_CANCEL", 200, "誤完了"),
            tuple("WITHDRAWAL_CLEAR", -200, PointLedgerService.WITHDRAWN_RECLEAR_REASON));
  }

  @Test
  void failureInLinkReleaseRollsBackEverything() {
    var member = member();
    credit(member.memberId, 100, null);
    String link = link(store, customer(store), member);
    String pending = application(member, "PENDING");
    jdbc.execute(
        "create function withdrawal_test_reject() returns trigger language plpgsql as $$ begin if NEW.status='RELEASED' and NEW.release_reason='会員退会' then raise exception '検証用の解除障害'; end if; return NEW; end $$");
    jdbc.execute(
        "create trigger withdrawal_test_reject before update on t_customer_member_links for each row execute function withdrawal_test_reject()");
    try {
      assertThatThrownBy(() -> service.withdrawSelf(member.userId, "やめる"))
          .isInstanceOf(DataAccessException.class);
    } finally {
      jdbc.execute("drop trigger withdrawal_test_reject on t_customer_member_links");
      jdbc.execute("drop function withdrawal_test_reject()");
    }
    assertThat(
            jdbc.queryForObject(
                "select withdrawn_at from t_members where id=?", Object.class, member.memberId))
        .isNull();
    assertThat(
            jdbc.queryForMap(
                "select enabled, withdrawn_at, credential_version from t_users where id=?",
                member.userId))
        .containsEntry("enabled", true)
        .containsEntry("withdrawn_at", null)
        .containsEntry("credential_version", 0L);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_point_entries where member_id=? and entry_type='WITHDRAWAL_CLEAR'",
                Long.class,
                member.memberId))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select status from t_customer_member_links where id=?", String.class, link))
        .isEqualTo("ACTIVE");
    assertThat(
            jdbc.queryForObject(
                "select status from t_order_applications where id=?", String.class, pending))
        .isEqualTo("PENDING");
    assertThat(users.findByEmail(member.email)).isPresent();
  }

  @Test
  void staffWithdrawalRecordsStaffAsActorAndConsoleShowsWithdrawalFacts() {
    var member = member();
    credit(member.memberId, 300, null);
    String customer = customer(store);
    String link = link(store, customer, member);
    String otherLink = link(otherStore, customer(otherStore), member);
    // 同じ店舗の古い解除済み区間。詳細は店舗ごとの最新だけを出す。
    jdbc.update(
        "insert into t_customer_member_links(id,store_id,customer_id,member_id,member_code,status,reason,linked_by,linked_at,released_by,released_at,release_reason) values (?,?,?,?,?,'RELEASED','MEMBER_CODE',?,current_timestamp - interval '1 day',?,current_timestamp - interval '12 hours','やり直し')",
        UUID.randomUUID().toString(),
        store,
        customer,
        member.memberId,
        member.memberCode,
        staff,
        staff);
    String pending = application(member, "PENDING");

    service.withdrawByStaff(member.memberId, staff, "本人からの電話依頼");

    assertThat(
            jdbc.queryForMap(
                "select withdrawal_reason, withdrawn_by from t_members where id=?",
                member.memberId))
        .containsEntry("withdrawal_reason", "本人からの電話依頼")
        .containsEntry("withdrawn_by", staff);
    assertThat(
            jdbc.queryForMap(
                "select enabled, withdrawn_at is not null as withdrawn, credential_version from t_users where id=?",
                member.userId))
        .containsEntry("enabled", false)
        .containsEntry("withdrawn", true)
        .containsEntry("credential_version", 1L);
    assertThat(
            jdbc.queryForMap(
                "select amount, actor_user_id from t_point_entries where member_id=? and entry_type='WITHDRAWAL_CLEAR'",
                member.memberId))
        .containsEntry("amount", -300)
        .containsEntry("actor_user_id", staff);
    assertThat(
            jdbc.queryForList(
                "select status, released_by from t_customer_member_links where id in (?,?)",
                link,
                otherLink))
        .allSatisfy(
            row ->
                assertThat(row)
                    .containsEntry("status", "RELEASED")
                    .containsEntry("released_by", staff));
    assertThat(
            jdbc.queryForObject(
                "select status from t_order_applications where id=?", String.class, pending))
        .isEqualTo("WITHDRAWN");
    assertThatThrownBy(() -> service.withdrawByStaff(member.memberId, staff, "二度目"))
        .isInstanceOf(MemberAlreadyWithdrawnException.class);
    assertThatThrownBy(() -> service.withdrawByStaff(-1L, staff, "居ない"))
        .isInstanceOf(NotFoundException.class);

    // 退会済みでも当時の電子メールで引け、既定の有効一覧には出ない。
    var directory = context.getBean(MemberDirectoryService.class);
    assertThat(directory.search(MemberStatusFilter.ACTIVE, member.email, PageRequest.of(0, 10)))
        .isEmpty();
    assertThat(directory.search(MemberStatusFilter.WITHDRAWN, member.email, PageRequest.of(0, 10)))
        .extracting(MemberDirectoryService.MemberSummary::memberCode)
        .containsExactly(member.memberCode);
    assertThat(
            directory.search(
                MemberStatusFilter.ALL, member.memberCode.substring(2, 8), PageRequest.of(0, 10)))
        .extracting(MemberDirectoryService.MemberSummary::id)
        .contains(member.memberId);
    assertThat(
            directory
                .search(MemberStatusFilter.ALL, "検証会員", PageRequest.of(0, 10))
                .getTotalElements())
        .isGreaterThanOrEqualTo(1);
    assertThat(directory.search(MemberStatusFilter.ALL, "100%_no_match!", PageRequest.of(0, 10)))
        .isEmpty();

    // 詳細: 退会情報は職員の表示名で、関連は閲覧者の店舗集合内だけ展開し集合外は件数のみ、明細は WITHDRAWAL_CLEAR を含む。
    var console = context.getBean(MemberConsoleService.class);
    MemberDetailResponse detail =
        console.detail(member.memberId, new StoreScope(false, java.util.Set.of(store)));
    assertThat(detail.email()).isEqualTo(member.email);
    assertThat(detail.withdrawal().reason()).isEqualTo("本人からの電話依頼");
    assertThat(detail.withdrawal().bySelf()).isFalse();
    assertThat(detail.withdrawal().withdrawnByName()).isEqualTo("検証職員");
    assertThat(detail.balance()).isZero();
    assertThat(detail.balances()).isEmpty();
    assertThat(detail.links())
        .singleElement()
        .satisfies(
            l -> {
              assertThat(l.storeId()).isEqualTo(store);
              assertThat(l.storeName()).isEqualTo("検証店舗");
              assertThat(l.customerName()).isEqualTo("検証顧客");
              assertThat(l.status()).isEqualTo("RELEASED");
              assertThat(l.releaseReason()).isEqualTo("会員退会");
            });
    assertThat(detail.otherStoreLinkCount()).isEqualTo(1);
    assertThat(
            console
                .detail(member.memberId, new StoreScope(false, java.util.Set.of(otherStore)))
                .otherStoreLinkCount())
        .as("集合外は区間ではなく店舗を数える")
        .isEqualTo(1);
    assertThat(console.detail(member.memberId, new StoreScope(true, java.util.Set.of())).links())
        .hasSize(2);
    var entries = console.entries(member.memberId, null, 20);
    assertThat(entries.content())
        .extracting(e -> e.entryType(), e -> e.amount(), e -> e.actorName())
        .containsExactly(
            tuple("WITHDRAWAL_CLEAR", -300, "検証職員"), tuple("MANUAL_ADJUST", 300, "検証職員"));
    assertThat(entries.content().getFirst().reason()).isEqualTo("会員退会");
  }

  private record MemberFixture(long memberId, long userId, String email, String memberCode) {}

  private MemberFixture member() {
    String key = UUID.randomUUID().toString();
    String email = key + "@example.test";
    long user =
        jdbc.queryForObject(
            "insert into t_users(display_name,user_type,store_scope_type,email,password) values ('検証会員','MEMBER','SPECIFIC_STORES',?,?) returning id",
            Long.class,
            email,
            HASH);
    String code = key.replace("-", "").substring(0, 12);
    long member =
        jdbc.queryForObject(
            "insert into t_members(member_code,user_id) values (?,?) returning id",
            Long.class,
            code,
            user);
    return new MemberFixture(member, user, email, code);
  }

  private long store() {
    return jdbc.queryForObject(
        "insert into t_stores(name,domain) values ('検証店舗',?) returning id",
        Long.class,
        UUID.randomUUID() + ".invalid");
  }

  private String customer(long storeId) {
    String id = UUID.randomUUID().toString();
    jdbc.update("insert into t_customers(id,store_id,name) values (?,?,'検証顧客')", id, storeId);
    return id;
  }

  private String link(long storeId, String customerId, MemberFixture member) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "insert into t_customer_member_links(id,store_id,customer_id,member_id,member_code,status,reason,linked_by,linked_at) values (?,?,?,?,?,'ACTIVE','MEMBER_CODE',?,current_timestamp)",
        id,
        storeId,
        customerId,
        member.memberId,
        member.memberCode,
        staff);
    return id;
  }

  private String application(MemberFixture member, String status) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "insert into t_order_applications(id,store_id,status,business_date,requester_member_id,requester_member_code,requester_declared_name) values (?,?,?,?,?,?,'名乗り')",
        id,
        store,
        status,
        TODAY.plusDays(3),
        member.memberId,
        member.memberCode);
    return id;
  }

  private long credit(long member, int amount, LocalDate expiry) {
    return tx.execute(
        status ->
            context
                .getBean(PointEntryRepository.class)
                .saveAndFlush(
                    PointEntry.manualAdjust(
                        member,
                        null,
                        amount,
                        "検証付与",
                        expiry,
                        List.of(),
                        staff,
                        UUID.randomUUID().toString()))
                .getId());
  }

  private String order(long storeId) {
    String order = UUID.randomUUID().toString();
    String course = UUID.randomUUID().toString();
    String revision = UUID.randomUUID().toString();
    jdbc.update(
        "insert into t_services(id,store_id,kind,name,duration_minutes,price,remuneration,revision_number) values (?,?,'COURSE','検証コース',60,100,0,1)",
        course,
        storeId);
    jdbc.update(
        "insert into t_service_revisions(id,store_id,service_id,revision_number,operation,actor_id,occurred_at,after_kind,after_name,after_duration_minutes,after_price,after_remuneration) values (?,?,?,1,'CREATED',?,current_timestamp,'COURSE','検証コース',60,100,0)",
        revision,
        storeId,
        course,
        staff);
    jdbc.update(
        "insert into t_orders(id,store_id,business_date,course_name,course_minutes,course_service_id,course_revision_id,course_revision_number,course_price,course_remuneration,course_adoption_basis,course_adopted_at) values (?,?,?,'検証コース',60,?,?,1,100,0,'CURRENT_SETTING',current_timestamp)",
        order,
        storeId,
        TODAY,
        course,
        revision);
    return order;
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(
      basePackageClasses = {
        UserRepository.class,
        MemberRepository.class,
        PointEntryRepository.class,
        CustomerMemberLinkRepository.class,
        OrderApplicationRepository.class,
        AuditEventRepository.class
      })
  @Import({
    MemberWithdrawalService.class,
    MemberWithdrawal.class,
    MemberLookupService.class,
    MemberRegistrationService.class,
    CredentialOperations.class,
    PointLedgerService.class,
    CustomerMemberLinkWithdrawal.class,
    CustomerMemberLinkService.class,
    MemberWithdrawalApplications.class,
    MemberDirectoryService.class,
    MemberLedgerQueryService.class,
    CustomerMemberLinkDirectory.class,
    MemberConsoleService.class,
    BusinessAudit.class,
    AuditWriter.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      var url = System.getenv("KIZUNA_MEMBER_WITHDRAWAL_TEST_JDBC_URL");
      var source = new DriverManagerDataSource(url, "postgres", "");
      var schema = "withdrawal_" + UUID.randomUUID().toString().replace("-", "");
      new JdbcTemplate(source).execute("create schema " + schema);
      source.setUrl(url + "?currentSchema=" + schema);
      return source;
    }

    @Bean
    SpringLiquibase liquibase(DataSource source) {
      var migration = new SpringLiquibase();
      migration.setDataSource(source);
      migration.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
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
    PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
      return new JpaTransactionManager(factory);
    }

    @Bean
    JdbcTemplate jdbc(DataSource source) {
      return new JdbcTemplate(source);
    }

    @Bean
    Clock clock() {
      return Clock.system(ZONE);
    }

    @Bean
    AppProperties properties() {
      return new AppProperties();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
      return new BCryptPasswordEncoder(4);
    }

    @Bean
    SystemConfigService settings() {
      var settings = mock(SystemConfigService.class);
      when(settings.pointSettings()).thenReturn(new PointSettings(100, 1, 1));
      return settings;
    }
  }
}
