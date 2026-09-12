package com.kizuna.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** {@link IntegrityViolations} の抽出（制約名の取り出しと等値照合）を固定する単体テスト。 */
class IntegrityViolationsTest {

  @Test
  @DisplayName("操作を一回実行し、戻り値をそのまま返す")
  void returnsOperationResult() {
    Object expected = new Object();
    AtomicInteger calls = new AtomicInteger();
    Object result =
        IntegrityViolations.translateOnFailure(
            () -> {
              calls.incrementAndGet();
              return expected;
            },
            Map.of());
    assertThat(result).isSameAs(expected);
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("操作の整合性違反を対応する業務例外へ変換して送出する")
  void throwsMappedOperationFailure() {
    DataIntegrityViolationException failure =
        new DataIntegrityViolationException(
            "save failed", hibernateViolation(DbConstraint.UQ_T_USERS_EMAIL.sqlName()));
    ConflictException expected = new ConflictException("重複しています");
    assertThatThrownBy(
            () ->
                IntegrityViolations.translateOnFailure(
                    () -> {
                      throw failure;
                    },
                    Map.of(DbConstraint.UQ_T_USERS_EMAIL, () -> expected)))
        .isSameAs(expected);
  }

  @Test
  @DisplayName("操作の未対応違反は元の例外を送出する")
  void propagatesUnmappedOperationFailure() {
    DataIntegrityViolationException failure =
        new DataIntegrityViolationException("save failed", hibernateViolation("uq_t_other_key"));
    assertThatThrownBy(
            () ->
                IntegrityViolations.translateOnFailure(
                    () -> {
                      throw failure;
                    },
                    Map.of(DbConstraint.UQ_T_USERS_EMAIL, () -> new ConflictException("重複しています"))))
        .isSameAs(failure);
  }

  @Test
  @DisplayName("操作の整合性違反以外の例外はそのまま送出する")
  void propagatesOtherOperationFailure() {
    IllegalStateException failure = new IllegalStateException("operation failed");
    assertThatThrownBy(
            () ->
                IntegrityViolations.translateOnFailure(
                    () -> {
                      throw failure;
                    },
                    Map.of()))
        .isSameAs(failure);
  }

  private static ConstraintViolationException hibernateViolation(String constraintName) {
    return new ConstraintViolationException(
        "could not execute statement",
        new SQLException("integrity constraint violation"),
        constraintName);
  }

  @Test
  @DisplayName("原因連鎖の途中にある Hibernate の違反から制約名を取り出し、対応する業務例外を返す")
  void translatesConstraintFoundDeeperInCauseChain() {
    // Spring の変換で包まれる層数は経路により異なるため、最深層だけを見る抽出では取りこぼす。
    DataIntegrityViolationException ex =
        new DataIntegrityViolationException(
            "save failed",
            new IllegalStateException(
                "wrapped", hibernateViolation(DbConstraint.UQ_T_USERS_EMAIL.sqlName())));

    RuntimeException translated =
        IntegrityViolations.translate(
            ex, Map.of(DbConstraint.UQ_T_USERS_EMAIL, () -> new ConflictException("重複しています")));

    assertThat(translated).isInstanceOf(ConflictException.class).hasMessage("重複しています");
  }

  @Test
  @DisplayName("対応表に複数の制約があっても、実際に違反した制約の写像だけを選ぶ")
  void selectsMappingOfTheViolatedConstraintOnly() {
    DataIntegrityViolationException ex =
        new DataIntegrityViolationException(
            "save failed", hibernateViolation(DbConstraint.FK_T_USER_STORES_STORE.sqlName()));

    RuntimeException translated =
        IntegrityViolations.translate(
            ex,
            Map.of(
                DbConstraint.UQ_T_USERS_EMAIL,
                () -> new ConflictException("メール重複"),
                DbConstraint.FK_T_USER_STORES_STORE,
                () -> new ServiceException("店舗が存在しません")));

    assertThat(translated).isInstanceOf(ServiceException.class).hasMessage("店舗が存在しません");
  }

  @Test
  @DisplayName("原因連鎖に Hibernate の違反が無ければ元の例外をそのまま返す")
  void returnsOriginalWhenChainHasNoHibernateViolation() {
    DataIntegrityViolationException ex =
        new DataIntegrityViolationException("save failed", new SQLException("integrity violation"));

    RuntimeException translated =
        IntegrityViolations.translate(
            ex, Map.of(DbConstraint.UQ_T_USERS_EMAIL, () -> new ConflictException("重複しています")));

    assertThat(translated).isSameAs(ex);
  }

  @Test
  @DisplayName("DB が制約名を報告しなかった場合は元の例外をそのまま返す")
  void returnsOriginalWhenConstraintNameIsUnknown() {
    DataIntegrityViolationException ex =
        new DataIntegrityViolationException("save failed", hibernateViolation(null));

    RuntimeException translated =
        IntegrityViolations.translate(
            ex, Map.of(DbConstraint.UQ_T_USERS_EMAIL, () -> new ConflictException("重複しています")));

    assertThat(translated).isSameAs(ex);
  }

  @Test
  @DisplayName("violates は違反した制約が指定の制約かどうかだけを答える")
  void violatesAnswersWhetherTheNamedConstraintWasViolated() {
    DataIntegrityViolationException ex =
        new DataIntegrityViolationException(
            "save failed", hibernateViolation(DbConstraint.UQ_T_USERS_EMAIL.sqlName()));

    assertThat(IntegrityViolations.violates(ex, DbConstraint.UQ_T_USERS_EMAIL)).isTrue();
    assertThat(IntegrityViolations.violates(ex, DbConstraint.UQ_T_ROLES_NAME)).isFalse();
  }

  @Test
  @DisplayName("violates は制約名の取れない違反を「違反していない」と答える")
  void violatesIsFalseWhenConstraintNameIsUnknown() {
    DataIntegrityViolationException ex =
        new DataIntegrityViolationException("save failed", new SQLException("integrity violation"));

    assertThat(IntegrityViolations.violates(ex, DbConstraint.UQ_T_USERS_EMAIL)).isFalse();
  }

  @Test
  @DisplayName("対応表に無い制約の違反は元の例外をそのまま返す（写像先の無い違反を握りつぶさない）")
  void returnsOriginalWhenConstraintIsNotMapped() {
    DataIntegrityViolationException ex =
        new DataIntegrityViolationException(
            "save failed", hibernateViolation("uq_t_some_other_table_key"));

    RuntimeException translated =
        IntegrityViolations.translate(
            ex, Map.of(DbConstraint.UQ_T_USERS_EMAIL, () -> new ConflictException("重複しています")));

    assertThat(translated).isSameAs(ex);
  }

  @Test
  @DisplayName("制約名の照合は部分一致ではなく等値で行う")
  void matchesConstraintNameByEqualityNotSubstring() {
    DataIntegrityViolationException ex =
        new DataIntegrityViolationException(
            "save failed", hibernateViolation(DbConstraint.UQ_T_USERS_EMAIL.sqlName() + "_v2"));

    RuntimeException translated =
        IntegrityViolations.translate(
            ex, Map.of(DbConstraint.UQ_T_USERS_EMAIL, () -> new ConflictException("重複しています")));

    assertThat(translated).isSameAs(ex);
  }
}
