package com.kizuna.membernotification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactPurpose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MemberNotificationSettingTest {

  @ParameterizedTest
  @CsvSource({
    "BUSINESS, true, ALLOWED",
    "BUSINESS, false, UNKNOWN",
    "MARKETING, true, UNKNOWN",
    "MARKETING, false, UNKNOWN"
  })
  void defaultDerivesBusinessFromVerificationAndNeverAllowsMarketing(
      ContactPurpose purpose, boolean verified, ContactPermissionStatus expected) {
    assertThat(MemberNotificationSetting.defaultFor(purpose, verified)).isEqualTo(expected);
  }

  @Test
  void holdsAndChangesAnExplicitChoice() {
    var setting =
        MemberNotificationSetting.chosen(
            1L, ContactPurpose.BUSINESS, ContactPermissionStatus.ALLOWED);

    setting.choose(ContactPermissionStatus.DENIED);

    assertThat(setting.getMemberId()).isEqualTo(1L);
    assertThat(setting.getPurpose()).isEqualTo(ContactPurpose.BUSINESS);
    assertThat(setting.getStatus()).isEqualTo(ContactPermissionStatus.DENIED);
  }

  @Test
  void refusesUnknownOrMissingAsAChoice() {
    assertThatThrownBy(
            () ->
                MemberNotificationSetting.chosen(
                    1L, ContactPurpose.BUSINESS, ContactPermissionStatus.UNKNOWN))
        .isInstanceOf(InvalidMemberNotificationSettingException.class);
    var setting =
        MemberNotificationSetting.chosen(
            1L, ContactPurpose.MARKETING, ContactPermissionStatus.DENIED);
    assertThatThrownBy(() -> setting.choose(null))
        .isInstanceOf(InvalidMemberNotificationSettingException.class);
    assertThat(setting.getStatus()).isEqualTo(ContactPermissionStatus.DENIED);
  }

  @Test
  void requiresMemberAndPurpose() {
    assertThatThrownBy(
            () ->
                MemberNotificationSetting.chosen(
                    null, ContactPurpose.BUSINESS, ContactPermissionStatus.ALLOWED))
        .isInstanceOf(InvalidMemberNotificationSettingException.class);
    assertThatThrownBy(
            () -> MemberNotificationSetting.chosen(1L, null, ContactPermissionStatus.ALLOWED))
        .isInstanceOf(InvalidMemberNotificationSettingException.class);
  }
}
