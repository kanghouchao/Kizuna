package com.kizuna.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmergencyElevationTest {

  private static final OffsetDateTime ACTIVATED_AT = OffsetDateTime.parse("2026-08-31T10:00:00Z");

  private static EmergencyElevation activated() {
    return EmergencyElevation.activate(7L, 3L, "決済障害の一次対応", ACTIVATED_AT);
  }

  @Test
  @DisplayName("発動した昇格は有効で、期限が発動時刻の 60 分後になること")
  void activateIsActiveWithSixtyMinuteExpiry() {
    EmergencyElevation elevation = activated();

    assertThat(elevation.getActivatedBy()).isEqualTo(7L);
    assertThat(elevation.getTargetStoreId()).isEqualTo(3L);
    assertThat(elevation.getReason()).isEqualTo("決済障害の一次対応");
    assertThat(elevation.getActivatedAt()).isEqualTo(ACTIVATED_AT);
    assertThat(elevation.getExpiresAt()).isEqualTo(OffsetDateTime.parse("2026-08-31T11:00:00Z"));
    assertThat(elevation.getStatus()).isEqualTo(EmergencyElevationStatus.ACTIVE);
    assertThat(elevation.getRevokedBy()).isNull();
    assertThat(elevation.getRevokedAt()).isNull();
  }

  @Test
  @DisplayName("理由の無い発動は記録できないこと")
  void missingReasonIsRejected() {
    assertThatThrownBy(() -> EmergencyElevation.activate(7L, 3L, " ", ACTIVATED_AT))
        .isInstanceOf(InvalidEmergencyElevationException.class)
        .hasMessageContaining("理由");
  }

  @Test
  @DisplayName("対象店舗の無い発動は記録できないこと")
  void missingTargetStoreIsRejected() {
    assertThatThrownBy(() -> EmergencyElevation.activate(7L, null, "決済障害の一次対応", ACTIVATED_AT))
        .isInstanceOf(InvalidEmergencyElevationException.class)
        .hasMessageContaining("対象店舗");
  }

  @Test
  @DisplayName("期限内の撤回は状態を倒し、撤回者と時刻を記録すること")
  void revokeBeforeExpiryRecordsTheActorAndTime() {
    EmergencyElevation elevation = activated();
    OffsetDateTime revokedAt = ACTIVATED_AT.plusMinutes(10);

    elevation.revoke(9L, revokedAt);

    assertThat(elevation.getStatus()).isEqualTo(EmergencyElevationStatus.REVOKED);
    assertThat(elevation.getRevokedBy()).isEqualTo(9L);
    assertThat(elevation.getRevokedAt()).isEqualTo(revokedAt);
  }

  @Test
  @DisplayName("二度目の撤回は撥ね、初回の撤回者と時刻を書き換えないこと")
  void secondRevokeIsRejected() {
    EmergencyElevation elevation = activated();
    OffsetDateTime firstRevokedAt = ACTIVATED_AT.plusMinutes(10);
    elevation.revoke(9L, firstRevokedAt);

    assertThatThrownBy(() -> elevation.revoke(11L, ACTIVATED_AT.plusMinutes(20)))
        .isInstanceOf(InvalidEmergencyElevationException.class);

    assertThat(elevation.getRevokedBy()).isEqualTo(9L);
    assertThat(elevation.getRevokedAt()).isEqualTo(firstRevokedAt);
  }

  @Test
  @DisplayName("期限の瞬間の撤回は撥ねること")
  void revokeAtExpiryIsRejected() {
    EmergencyElevation elevation = activated();

    assertThatThrownBy(() -> elevation.revoke(9L, elevation.getExpiresAt()))
        .isInstanceOf(InvalidEmergencyElevationException.class);

    assertThat(elevation.getStatus()).isEqualTo(EmergencyElevationStatus.ACTIVE);
  }

  @Test
  @DisplayName("期限切れ後の撤回は撥ねること")
  void revokeAfterExpiryIsRejected() {
    EmergencyElevation elevation = activated();

    assertThatThrownBy(() -> elevation.revoke(9L, elevation.getExpiresAt().plusSeconds(1)))
        .isInstanceOf(InvalidEmergencyElevationException.class);

    assertThat(elevation.getStatus()).isEqualTo(EmergencyElevationStatus.ACTIVE);
    assertThat(elevation.getRevokedBy()).isNull();
  }
}
