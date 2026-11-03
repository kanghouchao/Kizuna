package com.kizuna.remuneration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.receipt.FingerprintContributions;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RemunerationCommandTest {
  private static final UUID KEY = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

  private static final RemunerationCommand.CreateGuarantee CREATE_GUARANTEE =
      new RemunerationCommand.CreateGuarantee(
          7L, DAY, GuaranteeState.ACTIVE, 10000L, "理由", 2L, KEY);
  private static final RemunerationCommand.CorrectGuarantee CORRECT_GUARANTEE =
      new RemunerationCommand.CorrectGuarantee(
          "g1", DAY, GuaranteeState.ACTIVE, 10000L, "理由", "訂正", 2L, KEY);
  private static final RemunerationCommand.CancelGuarantee CANCEL_GUARANTEE =
      new RemunerationCommand.CancelGuarantee("g1", "理由", 2L, KEY);
  private static final RemunerationCommand.CreateBonus CREATE_BONUS =
      new RemunerationCommand.CreateBonus(7L, DAY, 5000L, "理由", KEY);
  private static final RemunerationCommand.CorrectBonus CORRECT_BONUS =
      new RemunerationCommand.CorrectBonus("b1", DAY, 5000L, "理由", "訂正", 2L, KEY);
  private static final RemunerationCommand.CancelBonus CANCEL_BONUS =
      new RemunerationCommand.CancelBonus("b1", "理由", 2L, KEY);

  // 既存の受領行と照合が続くので、移設前の算出が出していた値そのものを固定する。
  @Test
  void fingerprintsMatchTheValuesAlreadyStoredInReceipts() {
    assertThat(CREATE_GUARANTEE.fingerprint())
        .isEqualTo("75a962cbe32324f16d41e6a2870274f083022a4cafd4625b8e6fdbe26a3d44d4");
    assertThat(
            new RemunerationCommand.CreateGuarantee(
                    7L, DAY, GuaranteeState.ACTIVE, null, "理由", 2L, KEY)
                .fingerprint())
        .isEqualTo("f4f2aaaabc08548769560b4bc7afd686533473ef0f75f5d8510de7c32070de85");
    assertThat(CORRECT_GUARANTEE.fingerprint())
        .isEqualTo("ee0fab0a82155f5b1873bcd515bf60d5c6589590ac239ba82be6378cd03faa1d");
    assertThat(CANCEL_GUARANTEE.fingerprint())
        .isEqualTo("108f2fb7694dead4339d4423362187ca72002e5ed0b452a13d7d6a28a7fbd92a");
    assertThat(CREATE_BONUS.fingerprint())
        .isEqualTo("00027002e58b508f0eb46fe9a37953dc6b03de87fd0b0cae2aa15c79d208f4fd");
    assertThat(CORRECT_BONUS.fingerprint())
        .isEqualTo("41499acd6641789babbee7b9b874d5d58e8480b6a6c4df7176177a93ad1d4c3e");
    assertThat(CANCEL_BONUS.fingerprint())
        .isEqualTo("e4fd52551b10c96d619928afb48dd51fbf2b45972fdbc03268edcd52e1fcd519");
  }

  @Test
  void everyItemButTheRequestKeyContributesToTheFingerprint() {
    assertThat(FingerprintContributions.ignored(CREATE_GUARANTEE, RemunerationCommand::fingerprint))
        .containsExactly("requestId");
    assertThat(
            FingerprintContributions.ignored(CORRECT_GUARANTEE, RemunerationCommand::fingerprint))
        .containsExactly("requestId");
    assertThat(FingerprintContributions.ignored(CANCEL_GUARANTEE, RemunerationCommand::fingerprint))
        .containsExactly("requestId");
    assertThat(FingerprintContributions.ignored(CREATE_BONUS, RemunerationCommand::fingerprint))
        .containsExactly("requestId");
    assertThat(FingerprintContributions.ignored(CORRECT_BONUS, RemunerationCommand::fingerprint))
        .containsExactly("requestId");
    assertThat(FingerprintContributions.ignored(CANCEL_BONUS, RemunerationCommand::fingerprint))
        .containsExactly("requestId");
  }

  @Test
  void cancellingAGuaranteeAndABonusWithTheSameValuesAreDifferentOperations() {
    assertThat(CANCEL_GUARANTEE.fingerprint())
        .isNotEqualTo(new RemunerationCommand.CancelBonus("g1", "理由", 2L, KEY).fingerprint());
  }
}
