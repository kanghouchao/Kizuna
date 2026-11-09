package com.kizuna.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.review.domain.ReviewCommand;
import com.kizuna.review.domain.ReviewInput;
import com.kizuna.review.domain.ReviewValues.Basis;
import com.kizuna.review.domain.ReviewValues.Operation;
import com.kizuna.review.domain.ReviewValues.ReceivedVia;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.publication.Publication;
import com.kizuna.user.domain.PermissionCode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ReviewCommandTest {
  private static final OffsetDateTime AT =
      OffsetDateTime.parse("2026-01-01T09:00:00.1234567+09:00");

  private static ReviewInput input(String origin) {
    return new ReviewInput("本文", null, ReceivedVia.PAPER, AT, origin);
  }

  @Test
  void eachVariantNormalizesOnlyItsOwnFields() {
    var decide =
        new ReviewCommand.Decide(
            "00000000-0000-7000-8000-000000000001", 0L, Operation.APPROVED, "  理由  ", " key ");
    assertThat(decide.reason()).isEqualTo("理由");
    assertThat(decide.key()).isEqualTo("key");
    var grant =
        new ReviewCommand.Grant(
            "00000000-0000-7000-8000-000000000001", 0L, Basis.WRITTEN, AT, "  根拠  ", "k");
    assertThat(grant.evidence()).isEqualTo("根拠");
    assertThat(grant.grantedAt()).isEqualTo(OffsetDateTime.parse("2026-01-01T00:00:00.123456Z"));
    var revoke =
        new ReviewCommand.Revoke("00000000-0000-7000-8000-000000000001", 0L, AT, "  撤回  ", "k");
    assertThat(revoke.reason()).isEqualTo("撤回");
    assertThat(revoke.receivedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    var correct =
        new ReviewCommand.Correct(
            "00000000-0000-7000-8000-000000000001", 0L, "  訂正  ", input(null), "k");
    assertThat(correct.reason()).isEqualTo("訂正");
  }

  @Test
  void variantsRejectMissingOrForeignValues() {
    assertThatThrownBy(
            () ->
                new ReviewCommand.Decide(
                    "00000000-0000-7000-8000-000000000001", 0L, Operation.RECEIVED, "理由", "k"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("審査結果を指定してください");
    assertThatThrownBy(
            () ->
                new ReviewCommand.Decide(
                    "00000000-0000-7000-8000-000000000001", 0L, null, "理由", "k"))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(
            () -> new ReviewCommand.Withdraw("00000000-0000-7000-8000-000000000001", 0L, " ", "k"))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(
            () ->
                new ReviewCommand.Withdraw("00000000-0000-7000-8000-000000000001", -1L, "理由", "k"))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(
            () ->
                new ReviewCommand.Withdraw("00000000-0000-7000-8000-000000000001", null, "理由", "k"))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> new ReviewCommand.Withdraw("a1", 0L, "理由", "k"))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(
            () -> new ReviewCommand.Withdraw("00000000-0000-7000-8000-000000000001", 0L, "理由", "鍵"))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(
            () ->
                new ReviewCommand.Grant(
                    "00000000-0000-7000-8000-000000000001", 0L, null, AT, "根拠", "k"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("許可の取得根拠を指定してください");
    assertThatThrownBy(
            () ->
                new ReviewCommand.Grant(
                    "00000000-0000-7000-8000-000000000001", 0L, Basis.WRITTEN, AT, null, "k"))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(
            () ->
                new ReviewCommand.Grant(
                    "00000000-0000-7000-8000-000000000001", 0L, Basis.WRITTEN, null, "根拠", "k"))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(
            () ->
                new ReviewCommand.Revoke(
                    "00000000-0000-7000-8000-000000000001", 0L, null, "理由", "k"))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(
            () -> new ReviewCommand.Publish("00000000-0000-7000-8000-000000000001", 0L, null, "k"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("公開設定を指定してください");
    assertThatThrownBy(
            () ->
                new ReviewCommand.Correct(
                    "00000000-0000-7000-8000-000000000001", 0L, "訂正", null, "k"))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(
            () ->
                new ReviewCommand.Correct(
                    "00000000-0000-7000-8000-000000000001", 0L, null, input(null), "k"))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> new ReviewCommand.Receive(null, "k"))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void fingerprintFollowsNormalizedContentAndDistinguishesOperations() {
    assertThat(
            new ReviewCommand.Decide(
                    "00000000-0000-7000-8000-000000000001", 0L, Operation.APPROVED, " 理由 ", "a")
                .fingerprint())
        .isEqualTo(
            new ReviewCommand.Decide(
                    "00000000-0000-7000-8000-000000000001", 0L, Operation.APPROVED, "理由", "b")
                .fingerprint())
        .isNotEqualTo(
            new ReviewCommand.Decide(
                    "00000000-0000-7000-8000-000000000001", 0L, Operation.REJECTED, "理由", "a")
                .fingerprint())
        .isNotEqualTo(
            new ReviewCommand.Decide(
                    "00000000-0000-7000-8000-000000000001", 1L, Operation.APPROVED, "理由", "a")
                .fingerprint())
        .isNotEqualTo(
            new ReviewCommand.Withdraw("00000000-0000-7000-8000-000000000001", 0L, "理由", "a")
                .fingerprint());
    var shown = new Publication(true, AT, null, 1);
    assertThat(
            new ReviewCommand.Publish("00000000-0000-7000-8000-000000000001", 0L, shown, "a")
                .fingerprint())
        .isEqualTo(
            new ReviewCommand.Publish("00000000-0000-7000-8000-000000000001", 0L, shown, "b")
                .fingerprint())
        .isNotEqualTo(
            new ReviewCommand.Publish(
                    "00000000-0000-7000-8000-000000000001", 0L, Publication.HIDDEN, "a")
                .fingerprint());
    assertThat(
            new ReviewCommand.Grant(
                    "00000000-0000-7000-8000-000000000001", 0L, Basis.WRITTEN, AT, "根拠", "a")
                .fingerprint())
        .isNotEqualTo(
            new ReviewCommand.Grant(
                    "00000000-0000-7000-8000-000000000001", 0L, Basis.VERBAL, AT, "根拠", "a")
                .fingerprint());
    assertThat(
            new ReviewCommand.Revoke("00000000-0000-7000-8000-000000000001", 0L, AT, "理由", "a")
                .fingerprint())
        .isNotEqualTo(
            new ReviewCommand.Revoke(
                    "00000000-0000-7000-8000-000000000001", 0L, AT.plusDays(1), "理由", "a")
                .fingerprint());
    assertThat(new ReviewCommand.Receive(input(null), "a").fingerprint())
        .isEqualTo(new ReviewCommand.Receive(input(null), "b").fingerprint())
        .isNotEqualTo(
            new ReviewCommand.Receive(input("00000000-0000-7000-8000-000000000009"), "a")
                .fingerprint());
    assertThat(
            new ReviewCommand.Correct(
                    "00000000-0000-7000-8000-000000000001", 0L, "訂正", input(null), "a")
                .fingerprint())
        .isNotEqualTo(
            new ReviewCommand.Correct(
                    "00000000-0000-7000-8000-000000000001",
                    0L,
                    "訂正",
                    input("00000000-0000-7000-8000-000000000009"),
                    "a")
                .fingerprint())
        .isNotEqualTo(
            new ReviewCommand.Correct(
                    "00000000-0000-7000-8000-000000000002", 0L, "訂正", input(null), "a")
                .fingerprint());
  }

  @Test
  void eachVariantNamesThePermissionsItRequires() {
    assertThat(
            new ReviewCommand.Decide(
                    "00000000-0000-7000-8000-000000000001", 0L, Operation.APPROVED, "理由", "k")
                .permissions())
        .containsExactly(PermissionCode.REVIEW_MODERATE);
    assertThat(
            new ReviewCommand.Publish(
                    "00000000-0000-7000-8000-000000000001", 0L, Publication.HIDDEN, "k")
                .permissions())
        .containsExactly(PermissionCode.REVIEW_MODERATE);
    assertThat(
            new ReviewCommand.Withdraw("00000000-0000-7000-8000-000000000001", 0L, "理由", "k")
                .permissions())
        .containsExactly(PermissionCode.REVIEW_MANAGE);
    assertThat(
            new ReviewCommand.Grant(
                    "00000000-0000-7000-8000-000000000001", 0L, Basis.WRITTEN, AT, "根拠", "k")
                .permissions())
        .containsExactly(PermissionCode.REVIEW_MANAGE);
    assertThat(
            new ReviewCommand.Revoke("00000000-0000-7000-8000-000000000001", 0L, AT, "理由", "k")
                .permissions())
        .containsExactly(PermissionCode.REVIEW_MANAGE);
    assertThat(new ReviewCommand.Receive(input(null), "k").permissions())
        .containsExactly(PermissionCode.REVIEW_MANAGE);
    assertThat(
            new ReviewCommand.Receive(input("00000000-0000-7000-8000-000000000009"), "k")
                .permissions())
        .containsExactly(PermissionCode.REVIEW_MANAGE, PermissionCode.ORDER_MANAGE);
    assertThat(
            new ReviewCommand.Correct(
                    "00000000-0000-7000-8000-000000000001", 0L, "訂正", input(null), "k")
                .permissions())
        .containsExactly(PermissionCode.REVIEW_MANAGE);
    assertThat(
            new ReviewCommand.Correct(
                    "00000000-0000-7000-8000-000000000001",
                    0L,
                    "訂正",
                    input("00000000-0000-7000-8000-000000000009"),
                    "k")
                .permissions())
        .containsExactly(PermissionCode.REVIEW_MANAGE, PermissionCode.ORDER_MANAGE);
  }
}
