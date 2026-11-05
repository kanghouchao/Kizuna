package com.kizuna.membernotification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactPurpose;
import com.kizuna.member.application.MemberLoginAddress;
import com.kizuna.member.application.MemberLoginAddress.Address;
import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.application.MemberLookupService.MemberLookup;
import com.kizuna.membernotification.domain.MemberNotificationSetting;
import com.kizuna.membernotification.domain.MemberNotificationSettingRepository;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.application.BusinessAudit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberNotificationSettingServiceTest {

  @Mock private MemberLookupService memberLookup;
  @Mock private MemberLoginAddress addresses;
  @Mock private MemberNotificationSettingRepository settings;
  @Mock private BusinessAudit audit;
  @InjectMocks private MemberNotificationSettingService service;

  @Test
  void viewFillsMissingPurposesWithTheUnverifiedDefault() {
    when(memberLookup.findByUserId(10L)).thenReturn(Optional.of(new MemberLookup(3L, "C")));
    when(addresses.of(3L)).thenReturn(Optional.of(new Address("m@example.test", false, false)));
    when(settings.findByMemberId(3L))
        .thenReturn(
            List.of(
                MemberNotificationSetting.chosen(
                    3L, ContactPurpose.MARKETING, ContactPermissionStatus.DENIED)));

    assertThat(service.view(10L))
        .isEqualTo(
            new MemberNotificationSettings(
                ContactPermissionStatus.UNKNOWN, ContactPermissionStatus.DENIED));
  }

  @Test
  void verifiedMemberWithoutChoiceIsAllowedBusinessButMarketingStaysUnknown() {
    when(memberLookup.findByUserId(10L)).thenReturn(Optional.of(new MemberLookup(3L, "C")));
    when(addresses.of(3L)).thenReturn(Optional.of(new Address("m@example.test", true, false)));
    when(settings.findByMemberId(3L)).thenReturn(List.of());

    assertThat(service.view(10L))
        .isEqualTo(
            new MemberNotificationSettings(
                ContactPermissionStatus.ALLOWED, ContactPermissionStatus.UNKNOWN));
  }

  @Test
  void verifiedMemberWhoDeniedBusinessIsNotAllowed() {
    lenient()
        .when(addresses.of(3L))
        .thenReturn(Optional.of(new Address("m@example.test", true, false)));
    when(settings.findByMemberIdAndPurpose(3L, ContactPurpose.BUSINESS))
        .thenReturn(
            Optional.of(
                MemberNotificationSetting.chosen(
                    3L, ContactPurpose.BUSINESS, ContactPermissionStatus.DENIED)));

    assertThat(service.effective(3L, ContactPurpose.BUSINESS))
        .isEqualTo(ContactPermissionStatus.DENIED);
  }

  @Test
  void firstChoiceLocksTheMemberBeforeInsertingAndAuditsFromTheDefault() {
    when(memberLookup.findByUserId(10L)).thenReturn(Optional.of(new MemberLookup(3L, "C")));
    when(addresses.of(3L)).thenReturn(Optional.of(new Address("m@example.test", false, false)));
    when(settings.findByMemberIdAndPurpose(3L, ContactPurpose.BUSINESS))
        .thenReturn(Optional.empty());

    service.choose(10L, ContactPurpose.BUSINESS, ContactPermissionStatus.ALLOWED);

    var order = inOrder(memberLookup, settings);
    order.verify(memberLookup).lockForNotificationSettingWrite(3L);
    order.verify(settings).findByMemberIdAndPurpose(3L, ContactPurpose.BUSINESS);
    order.verify(settings).save(any(MemberNotificationSetting.class));
    verify(audit)
        .recordById(
            10L,
            null,
            "MEMBER_NOTIFICATION_SETTING_CHANGED",
            "MEMBER",
            "3",
            null,
            null,
            Map.of("purpose", "BUSINESS", "status", "UNKNOWN"),
            Map.of("purpose", "BUSINESS", "status", "ALLOWED"));
  }

  @Test
  void repeatingTheSameChoiceIsNotAudited() {
    var existing =
        MemberNotificationSetting.chosen(
            3L, ContactPurpose.BUSINESS, ContactPermissionStatus.ALLOWED);
    when(memberLookup.findByUserId(10L)).thenReturn(Optional.of(new MemberLookup(3L, "C")));
    when(addresses.of(3L)).thenReturn(Optional.of(new Address("m@example.test", false, false)));
    when(settings.findByMemberIdAndPurpose(3L, ContactPurpose.BUSINESS))
        .thenReturn(Optional.of(existing));
    when(settings.findByMemberId(3L)).thenReturn(List.of(existing));

    var result = service.choose(10L, ContactPurpose.BUSINESS, ContactPermissionStatus.ALLOWED);

    assertThat(result.business()).isEqualTo(ContactPermissionStatus.ALLOWED);
    verify(settings, never()).save(any());
    verify(audit, never())
        .recordById(
            any(), any(), anyString(), anyString(), anyString(), any(), any(), anyMap(), anyMap());
  }

  @Test
  void callerWithoutMemberIdentityIsAStaleSession() {
    when(memberLookup.findByUserId(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.view(10L)).isInstanceOf(StaleSessionException.class);
    assertThatThrownBy(
            () -> service.choose(10L, ContactPurpose.BUSINESS, ContactPermissionStatus.ALLOWED))
        .isInstanceOf(StaleSessionException.class);
  }
}
