package com.kizuna.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.customer.api.dto.ContactRequest;
import com.kizuna.customer.api.dto.CustomerCreateRequest;
import com.kizuna.customer.api.dto.CustomerUpdateRequest;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.storescope.StoreContext;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 実DBの顧客・連絡先履歴・公共監査を同じ取引で確定し、拒否時の全rollbackを検証する。 */
@EnabledIfEnvironmentVariable(named = "KIZUNA_CONTACT_AUDIT_TEST_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerAuditPostgresTest {
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private CustomerService customers;
  private TransactionTemplate transactions;

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(CustomerContactAuditPostgresTest.Config.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    customers = context.getBean(CustomerService.class);
    transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
  }

  @BeforeEach
  void authenticate() {
    context.getBean(StoreContext.class).setStoreId(1L);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken("operator@example.test", null, List.of()));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
    context.getBean(StoreContext.class).clear();
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @Test
  void committedCrudVersionsSurvivePhysicalDeletionAndNoOpsAddNothing() {
    var id = customers.create(request(false)).getId();
    var patch = new CustomerUpdateRequest();
    patch.setAddress("監査へ残さない住所");
    customers.update(id, patch);
    customers.update(id, patch);
    customers.update(id, new CustomerUpdateRequest());
    customers.delete(id);
    assertThat(jdbc.queryForObject("select count(*) from t_customers where id = ?", Long.class, id))
        .isZero();
    var events =
        jdbc.queryForList(
            "select action, before_values ->> 'version' as old_version, after_values ->> 'version' as new_version, after_values ->> 'exists' as present from t_audit_events where target_type = 'CUSTOMER' and target_id = ? order by id",
            id);
    assertThat(events).hasSize(3);
    assertThat(events.get(0))
        .containsEntry("action", "CUSTOMER_CREATED")
        .containsEntry("old_version", null)
        .containsEntry("new_version", "0");
    assertThat(events.get(1))
        .containsEntry("action", "CUSTOMER_UPDATED")
        .containsEntry("old_version", "0")
        .containsEntry("new_version", "1");
    assertThat(events.get(2))
        .containsEntry("action", "CUSTOMER_DELETED")
        .containsEntry("old_version", "1")
        .containsEntry("new_version", null)
        .containsEntry("present", "false");
  }

  @Test
  void customerAuditRejectionRollsBackParentContactsAndTheirEarlierAudit() {
    var request = request(true);
    long contacts = count("t_customer_contacts");
    long histories = count("t_customer_contact_history");
    long audits = count("t_audit_events");
    reject("CUSTOMER_CREATED");
    try {
      assertThatThrownBy(() -> customers.create(request)).isInstanceOf(RuntimeException.class);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from t_customers where name = ?", Long.class, request.getName()))
          .isZero();
      assertThat(count("t_customer_contacts")).isEqualTo(contacts);
      assertThat(count("t_customer_contact_history")).isEqualTo(histories);
      assertThat(count("t_audit_events")).isEqualTo(audits);
    } finally {
      allow();
    }
  }

  @Test
  void outerFailureRollsBackCustomerAndMultipleContactAudits() {
    var request = request(true);
    request.setContacts(
        List.of(
            new ContactRequest(ContactType.LINE, "私的LINE"),
            new ContactRequest(ContactType.EMAIL, "private@example.test")));
    long contacts = count("t_customer_contacts");
    long histories = count("t_customer_contact_history");
    long audits = count("t_audit_events");
    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    status -> {
                      customers.create(request);
                      assertThat(count("t_audit_events")).isEqualTo(audits + 3);
                      throw new IllegalStateException("後続失敗");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_customers where name = ?", Long.class, request.getName()))
        .isZero();
    assertThat(count("t_customer_contacts")).isEqualTo(contacts);
    assertThat(count("t_customer_contact_history")).isEqualTo(histories);
    assertThat(count("t_audit_events")).isEqualTo(audits);
  }

  @Test
  void updateAuditRejectionRestoresAttributesAndVersion() {
    var request = request(false);
    var id = customers.create(request).getId();
    long audits = count("t_audit_events");
    var patch = new CustomerUpdateRequest();
    patch.setName("確定しない秘密名");
    reject("CUSTOMER_UPDATED");
    try {
      assertThatThrownBy(() -> customers.update(id, patch)).isInstanceOf(RuntimeException.class);
      assertThat(jdbc.queryForMap("select name, version from t_customers where id = ?", id))
          .containsEntry("name", request.getName())
          .containsEntry("version", 0L);
      assertThat(count("t_audit_events")).isEqualTo(audits);
    } finally {
      allow();
    }
  }

  @Test
  void deleteAuditRejectionRestoresThePhysicallyRemovedCustomer() {
    var id = customers.create(request(false)).getId();
    long audits = count("t_audit_events");
    reject("CUSTOMER_DELETED");
    try {
      assertThatThrownBy(() -> customers.delete(id)).isInstanceOf(RuntimeException.class);
      assertThat(
              jdbc.queryForObject("select version from t_customers where id = ?", Long.class, id))
          .isZero();
      assertThat(count("t_audit_events")).isEqualTo(audits);
    } finally {
      allow();
    }
  }

  @Test
  void existingContactHistoryProtectsItsParentWithoutASuccessfulDeletionAudit() {
    var id = customers.create(request(true)).getId();
    long audits = count("t_audit_events");
    assertThatThrownBy(() -> customers.delete(id)).isInstanceOf(ConflictException.class);
    assertThat(jdbc.queryForObject("select count(*) from t_customers where id = ?", Long.class, id))
        .isEqualTo(1);
    assertThat(count("t_audit_events")).isEqualTo(audits);
  }

  @Test
  void databaseReferenceRejectsPhysicalDeletionWithoutAddingAnAudit() {
    var id = customers.create(request(false)).getId();
    long audits = count("t_audit_events");
    jdbc.execute(
        "create table customer_audit_test_reference (customer_id varchar(64) references t_customers(id))");
    try {
      jdbc.update("insert into customer_audit_test_reference(customer_id) values (?)", id);
      assertThatThrownBy(() -> customers.delete(id)).isInstanceOf(RuntimeException.class);
      assertThat(
              jdbc.queryForObject("select count(*) from t_customers where id = ?", Long.class, id))
          .isEqualTo(1);
      assertThat(count("t_audit_events")).isEqualTo(audits);
      jdbc.update("delete from customer_audit_test_reference where customer_id = ?", id);
      customers.delete(id);
      assertThat(
              jdbc.queryForObject("select count(*) from t_customers where id = ?", Long.class, id))
          .isZero();
      assertThat(count("t_audit_events")).isEqualTo(audits + 1);
    } finally {
      jdbc.execute("drop table customer_audit_test_reference");
    }
  }

  private long count(String table) {
    return jdbc.queryForObject("select count(*) from " + table, Long.class);
  }

  private void reject(String action) {
    jdbc.execute(
        "alter table t_audit_events add constraint ck_customer_audit_test check (action <> '"
            + action
            + "') not valid");
  }

  private void allow() {
    jdbc.execute("alter table t_audit_events drop constraint ck_customer_audit_test");
  }

  private CustomerCreateRequest request(boolean withContact) {
    var request = new CustomerCreateRequest();
    request.setName("顧客取引-" + UUID.randomUUID());
    if (withContact)
      request.setContacts(List.of(new ContactRequest(ContactType.LINE, "秘密-" + UUID.randomUUID())));
    return request;
  }
}
