package com.kizuna.cast.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CastInvitationTest {

  private static final OffsetDateTime ISSUED_AT = OffsetDateTime.parse("2026-07-15T00:00:00Z");
  private static final OffsetDateTime EXPIRES_AT = ISSUED_AT.plus(CastInvitation.VALIDITY);

  private CastInvitation invitation(CastInvitation.Status status) {
    return CastInvitation.builder()
        .token("token-1")
        .castId("cast-1")
        .status(status)
        .expiresAt(EXPIRES_AT)
        .build();
  }

  @Test
  @DisplayName("有効期間の定数は 72 時間であること")
  void validityIs72Hours() {
    assertThat(CastInvitation.VALIDITY).isEqualTo(Duration.ofHours(72));
  }

  @Test
  @DisplayName("ビルダー構築の既定状態が PENDING であること")
  void defaultStatusIsPending() {
    CastInvitation created =
        CastInvitation.builder().token("t").castId("c").expiresAt(EXPIRES_AT).build();
    assertThat(created.getStatus()).isEqualTo(CastInvitation.Status.PENDING);
  }

  @Test
  @DisplayName("PENDING かつ未期限の招待を受諾でき、受諾時刻が記録されること")
  void accept_pendingNotExpired() {
    CastInvitation invitation = invitation(CastInvitation.Status.PENDING);
    OffsetDateTime now = ISSUED_AT.plusHours(1);

    invitation.accept(now);

    assertThat(invitation.getStatus()).isEqualTo(CastInvitation.Status.ACCEPTED);
    assertThat(invitation.getAcceptedAt()).isEqualTo(now);
  }

  @Test
  @DisplayName("有効期限ちょうど（72h 境界）は未期限として受諾できること")
  void accept_atExactExpiry() {
    CastInvitation invitation = invitation(CastInvitation.Status.PENDING);

    invitation.accept(EXPIRES_AT);

    assertThat(invitation.getStatus()).isEqualTo(CastInvitation.Status.ACCEPTED);
  }

  @Test
  @DisplayName("有効期限を超過した招待は受諾できず、状態が変わらないこと")
  void accept_afterExpiry_isRejected() {
    CastInvitation invitation = invitation(CastInvitation.Status.PENDING);

    assertThatThrownBy(() -> invitation.accept(EXPIRES_AT.plusSeconds(1)))
        .isInstanceOf(CastInvitationStateException.class);
    assertThat(invitation.getStatus()).isEqualTo(CastInvitation.Status.PENDING);
    assertThat(invitation.getAcceptedAt()).isNull();
  }

  @Test
  @DisplayName("受諾済みの招待は再受諾できないこと")
  void accept_alreadyAccepted_isRejected() {
    CastInvitation invitation = invitation(CastInvitation.Status.ACCEPTED);

    assertThatThrownBy(() -> invitation.accept(ISSUED_AT.plusHours(1)))
        .isInstanceOf(CastInvitationStateException.class);
  }

  @Test
  @DisplayName("失効済みの招待は受諾できないこと")
  void accept_invalidated_isRejected() {
    CastInvitation invitation = invitation(CastInvitation.Status.INVALIDATED);

    assertThatThrownBy(() -> invitation.accept(ISSUED_AT.plusHours(1)))
        .isInstanceOf(CastInvitationStateException.class);
  }

  @Test
  @DisplayName("PENDING の招待を失効させられること")
  void invalidate_pending() {
    CastInvitation invitation = invitation(CastInvitation.Status.PENDING);

    invitation.invalidate();

    assertThat(invitation.getStatus()).isEqualTo(CastInvitation.Status.INVALIDATED);
  }

  @Test
  @DisplayName("受諾済みの招待は失効できないこと")
  void invalidate_accepted_isRejected() {
    CastInvitation invitation = invitation(CastInvitation.Status.ACCEPTED);

    assertThatThrownBy(invitation::invalidate).isInstanceOf(CastInvitationStateException.class);
  }

  @Test
  @DisplayName("失効済みの招待は再度失効できないこと")
  void invalidate_invalidated_isRejected() {
    CastInvitation invitation = invitation(CastInvitation.Status.INVALIDATED);

    assertThatThrownBy(invitation::invalidate).isInstanceOf(CastInvitationStateException.class);
  }

  @Test
  @DisplayName("有効期限の前後で isExpired が正しく判定すること")
  void isExpired_boundary() {
    CastInvitation invitation = invitation(CastInvitation.Status.PENDING);

    assertThat(invitation.isExpired(EXPIRES_AT.minusSeconds(1))).isFalse();
    assertThat(invitation.isExpired(EXPIRES_AT)).isFalse();
    assertThat(invitation.isExpired(EXPIRES_AT.plusSeconds(1))).isTrue();
  }
}
