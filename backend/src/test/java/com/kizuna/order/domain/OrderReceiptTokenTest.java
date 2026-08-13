package com.kizuna.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderReceiptTokenTest {

  private static final OffsetDateTime ISSUED_AT = OffsetDateTime.parse("2026-08-12T19:00:00Z");

  private static final String DIGEST = "ZGlnZXN0LW9mLXRoZS1yYXctdG9rZW4";

  @Test
  @DisplayName("発行されたトークンは未申領で、申領期限を発行から 90 日後に持つこと")
  void issuedTokenExpiresNinetyDaysAfterIssuance() {
    OrderReceiptToken token = OrderReceiptToken.issueFor("o1", DIGEST, 120, ISSUED_AT);

    assertThat(token.getOrderId()).isEqualTo("o1");
    assertThat(token.getTokenDigest()).isEqualTo(DIGEST);
    assertThat(token.getPlannedPoints()).isEqualTo(120);
    assertThat(token.getIssuedAt()).isEqualTo(ISSUED_AT);
    assertThat(token.getExpiresAt()).isEqualTo(ISSUED_AT.plusDays(90));
    assertThat(token.getStatus()).isEqualTo(OrderReceiptTokenStatus.ISSUED);
  }

  @Test
  @DisplayName("0 円完了の無帰属受注にも付与予定額 0 で発行できること")
  void zeroPlannedPointsIsIssuable() {
    // 申領の効果は来店の可視化に閉じる。付与が 0 でも発行しないと、その来店を取り戻す経路が無い
    assertThat(OrderReceiptToken.issueFor("o1", DIGEST, 0, ISSUED_AT).getPlannedPoints()).isZero();
  }

  @Test
  @DisplayName("受注 ID の無いトークンは発行できないこと")
  void missingOrderIdIsRejected() {
    assertThatThrownBy(() -> OrderReceiptToken.issueFor(" ", DIGEST, 120, ISSUED_AT))
        .isInstanceOf(InvalidOrderReceiptTokenException.class)
        .hasMessageContaining("受注 ID");
  }

  @Test
  @DisplayName("ダイジェストの無いトークンは発行できないこと（照合の術が無くなる）")
  void missingDigestIsRejected() {
    assertThatThrownBy(() -> OrderReceiptToken.issueFor("o1", " ", 120, ISSUED_AT))
        .isInstanceOf(InvalidOrderReceiptTokenException.class)
        .hasMessageContaining("ダイジェスト");
  }

  @Test
  @DisplayName("負の付与予定額は発行できないこと")
  void negativePlannedPointsIsRejected() {
    assertThatThrownBy(() -> OrderReceiptToken.issueFor("o1", DIGEST, -1, ISSUED_AT))
        .isInstanceOf(InvalidOrderReceiptTokenException.class)
        .hasMessageContaining("付与予定額");
  }

  @Test
  @DisplayName("発行時刻の無いトークンは発行できないこと（期限を数える起点が無くなる）")
  void missingIssuedAtIsRejected() {
    assertThatThrownBy(() -> OrderReceiptToken.issueFor("o1", DIGEST, 120, null))
        .isInstanceOf(InvalidOrderReceiptTokenException.class)
        .hasMessageContaining("発行の日時");
  }

  @Test
  @DisplayName("期限内の未申領トークンは申領でき、申領済みになること")
  void issuedTokenIsClaimableWithinTheValidityWindow() {
    OrderReceiptToken token = OrderReceiptToken.issueFor("o1", DIGEST, 120, ISSUED_AT);

    assertThat(token.isClaimableAt(ISSUED_AT.plusDays(89))).isTrue();
    token.claim(ISSUED_AT.plusDays(89));

    assertThat(token.getStatus()).isEqualTo(OrderReceiptTokenStatus.CLAIMED);
  }

  @Test
  @DisplayName("申領済みのトークンは二度目の申領ができないこと")
  void claimedTokenCannotBeClaimedAgain() {
    // 再送の遮断は冪等キーではなく前提状態の消滅が担う（ADR 0007 の判定基準）
    OrderReceiptToken token = OrderReceiptToken.issueFor("o1", DIGEST, 120, ISSUED_AT);
    token.claim(ISSUED_AT);

    assertThat(token.isClaimableAt(ISSUED_AT)).isFalse();
    assertThatThrownBy(() -> token.claim(ISSUED_AT))
        .isInstanceOf(InvalidOrderReceiptTokenException.class);
  }

  @Test
  @DisplayName("期限を過ぎたトークンは申領できないこと（期限の瞬間を含む）")
  void expiredTokenIsNotClaimable() {
    OrderReceiptToken token = OrderReceiptToken.issueFor("o1", DIGEST, 120, ISSUED_AT);

    assertThat(token.isClaimableAt(token.getExpiresAt())).isFalse();
    assertThatThrownBy(() -> token.claim(token.getExpiresAt()))
        .isInstanceOf(InvalidOrderReceiptTokenException.class);
    assertThat(token.getStatus()).isEqualTo(OrderReceiptTokenStatus.ISSUED);
  }

  @Test
  @DisplayName("未申領のトークンは失効させられ、以後は申領できないこと")
  void issuedTokenCanBeRevoked() {
    OrderReceiptToken token = OrderReceiptToken.issueFor("o1", DIGEST, 120, ISSUED_AT);

    token.revoke();

    assertThat(token.getStatus()).isEqualTo(OrderReceiptTokenStatus.REVOKED);
    assertThat(token.isClaimableAt(ISSUED_AT.plusDays(1))).isFalse();
  }

  @Test
  @DisplayName("期限切れの未申領トークンも失効させられること（申領できるかで判じない）")
  void expiredButUnclaimedTokenCanBeRevoked() {
    // 期限を倒す機構は無いので、90 日を過ぎた行も ISSUED のまま残る。ここで撥ねると再発行が
    // その行を倒せず、「受注ごとに ISSUED は高々 1 本」を DB へ委ねられなくなる
    OrderReceiptToken token = OrderReceiptToken.issueFor("o1", DIGEST, 120, ISSUED_AT);
    assertThat(token.isClaimableAt(token.getExpiresAt())).as("前提: 期限切れであること").isFalse();

    token.revoke();

    assertThat(token.getStatus()).isEqualTo(OrderReceiptTokenStatus.REVOKED);
  }

  @Test
  @DisplayName("申領済みのトークンは失効させられないこと（成立した帰属の根拠を書き換えない）")
  void claimedTokenCannotBeRevoked() {
    OrderReceiptToken token = OrderReceiptToken.issueFor("o1", DIGEST, 120, ISSUED_AT);
    token.claim(ISSUED_AT.plusDays(1));

    assertThatThrownBy(token::revoke).isInstanceOf(InvalidOrderReceiptTokenException.class);
    assertThat(token.getStatus()).isEqualTo(OrderReceiptTokenStatus.CLAIMED);
  }

  @Test
  @DisplayName("失効済みのトークンは二度目の失効を受け付けないこと")
  void revokedTokenCannotBeRevokedAgain() {
    OrderReceiptToken token = OrderReceiptToken.issueFor("o1", DIGEST, 120, ISSUED_AT);
    token.revoke();

    assertThatThrownBy(token::revoke).isInstanceOf(InvalidOrderReceiptTokenException.class);
  }

  @Test
  @DisplayName("診断出力にダイジェストが載らないこと")
  void toStringOmitsTheDigest() {
    // 生値は元より持たないが、ダイジェストも照合の鍵そのもの。ログへ流れると保存を絞った意味が消える
    assertThat(OrderReceiptToken.issueFor("o1", DIGEST, 120, ISSUED_AT).toString())
        .doesNotContain(DIGEST)
        .contains("o1");
  }
}
