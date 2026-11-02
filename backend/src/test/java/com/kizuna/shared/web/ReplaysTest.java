package com.kizuna.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.exception.DbConstraint;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ReplaysTest {
  private static DataIntegrityViolationException violation(String constraint) {
    return new DataIntegrityViolationException(
        "duplicate",
        new ConstraintViolationException(
            "could not execute statement",
            new SQLException("duplicate key value", "23505"),
            constraint));
  }

  @Test
  void onlyTheNamedKeyViolationIsResolvedAsAReplay() {
    assertThat(
            Replays.write(
                DbConstraint.UQ_T_OPERATION_RECEIPTS_KEY,
                () -> {
                  throw violation("uq_t_operation_receipts_key");
                },
                () -> "replayed"))
        .isEqualTo("replayed");
    assertThat(Replays.write(DbConstraint.UQ_T_OPERATION_RECEIPTS_KEY, () -> "first", () -> "x"))
        .isEqualTo("first");

    var other = violation("uq_t_other");
    assertThatThrownBy(
            () ->
                Replays.write(
                    DbConstraint.UQ_T_OPERATION_RECEIPTS_KEY,
                    () -> {
                      throw other;
                    },
                    () -> "replayed"))
        .isSameAs(other);
  }
}
