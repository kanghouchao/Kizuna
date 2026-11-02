package com.kizuna.medium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.medium.lookup.MediumLookup;
import com.kizuna.medium.lookup.MediumReference;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.ServiceException;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class MediumLookupTest {

  @Test
  void flushRunsTheActionOnce() {
    var calls = new AtomicInteger();
    MediumLookup.flush(calls::incrementAndGet, DbConstraint.FK_T_ORDERS_MEDIUM);
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void flushTurnsTheCallersMediumFkViolationIntoTheMissingInputError() {
    assertThatThrownBy(
            () ->
                MediumLookup.flush(
                    () -> {
                      throw violation(DbConstraint.FK_T_APPLICANTS_MEDIUM);
                    },
                    DbConstraint.FK_T_APPLICANTS_MEDIUM))
        .isInstanceOf(ServiceException.class)
        .hasMessage("媒体が見つかりません。一覧から選択してください");
  }

  @Test
  void flushKeepsAViolationOfAnotherConstraint() {
    var other = violation(DbConstraint.FK_T_ORDERS_MEDIUM);
    assertThatThrownBy(
            () ->
                MediumLookup.flush(
                    () -> {
                      throw other;
                    },
                    DbConstraint.FK_T_APPLICANTS_MEDIUM))
        .isSameAs(other);
  }

  @Test
  void referencedResolvesTheRecordsMediumAndLeavesANullIdNull() {
    var medium = new MediumReference("m1", "求人誌", false);
    var media = Map.of("m1", medium);
    assertThat(MediumLookup.referenced(media, "m1")).isSameAs(medium);
    assertThat(MediumLookup.referenced(media, null)).isNull();
  }

  @Test
  void referencedRejectsAMediumTheRecordPointsToButTheLookupLacks() {
    assertThatThrownBy(() -> MediumLookup.referenced(Map.of(), "m1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("記録の媒体が見つかりません");
    assertThatThrownBy(() -> MediumLookup.referenced(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("記録の媒体が見つかりません");
  }

  private static DataIntegrityViolationException violation(DbConstraint constraint) {
    return new DataIntegrityViolationException(
        "flush failed",
        new ConstraintViolationException(
            "could not execute statement", new SQLException("fk"), constraint.sqlName()));
  }
}
