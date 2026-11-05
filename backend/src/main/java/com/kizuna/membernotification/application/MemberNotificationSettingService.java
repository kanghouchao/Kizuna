package com.kizuna.membernotification.application;

import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactPurpose;
import com.kizuna.member.application.MemberLoginAddress;
import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.application.MemberLookupService.MemberLookup;
import com.kizuna.membernotification.domain.MemberNotificationSetting;
import com.kizuna.membernotification.domain.MemberNotificationSettingRepository;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.application.BusinessAudit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会員本人による通知設定の照会と変更（GLOSSARY「会員の通知設定」）。対象は検証済みトークンの利用者 ID で固定し、職員の閲覧・代理変更の口は持たない。
 * 会員宛の通知の送信判定には用途ごとの実効値だけを渡す。店舗の顧客連絡先の連絡可否は読みも書きもしない。
 */
@Service
@RequiredArgsConstructor
public class MemberNotificationSettingService {

  private final MemberLookupService memberLookup;
  private final MemberLoginAddress addresses;
  private final MemberNotificationSettingRepository settings;
  private final BusinessAudit audit;

  @Transactional(readOnly = true)
  public MemberNotificationSettings view(long userId) {
    long memberId = memberIdOf(userId);
    return effective(settings.findByMemberId(memberId), emailVerified(memberId));
  }

  /** 用途の選択を書く。行の有無の読みから挿入までを会員行のロックで直列化し、値が変わったときだけ業務監査に残す。 */
  @Transactional
  public MemberNotificationSettings choose(
      long userId, ContactPurpose purpose, ContactPermissionStatus status) {
    long memberId = memberIdOf(userId);
    memberLookup.lockForNotificationSettingWrite(memberId);
    Optional<MemberNotificationSetting> current =
        settings.findByMemberIdAndPurpose(memberId, purpose);
    ContactPermissionStatus before =
        current
            .map(MemberNotificationSetting::getStatus)
            .orElseGet(
                () -> MemberNotificationSetting.defaultFor(purpose, emailVerified(memberId)));
    current.ifPresentOrElse(
        row -> row.choose(status),
        () -> settings.save(MemberNotificationSetting.chosen(memberId, purpose, status)));
    if (before != status) {
      audit.recordById(
          userId,
          null,
          "MEMBER_NOTIFICATION_SETTING_CHANGED",
          "MEMBER",
          Long.toString(memberId),
          null,
          null,
          Map.of("purpose", purpose.name(), "status", before.name()),
          Map.of("purpose", purpose.name(), "status", status.name()));
    }
    return effective(settings.findByMemberId(memberId), emailVerified(memberId));
  }

  /** 会員 ID で引く用途の実効値。会員宛の通知の送信直前の判定が読む。 */
  @Transactional(readOnly = true)
  public ContactPermissionStatus effective(long memberId, ContactPurpose purpose) {
    return settings
        .findByMemberIdAndPurpose(memberId, purpose)
        .map(MemberNotificationSetting::getStatus)
        .orElseGet(() -> MemberNotificationSetting.defaultFor(purpose, emailVerified(memberId)));
  }

  // 既定は解決のたびに今の検証状態から導く（行に写さない理由は MemberNotificationSetting に記す）。
  private boolean emailVerified(long memberId) {
    return addresses
        .of(memberId)
        .map(MemberLoginAddress.Address::emailVerified)
        .orElseThrow(() -> new IllegalStateException("会員の宛先が存在しません"));
  }

  private long memberIdOf(long userId) {
    return memberLookup
        .findByUserId(userId)
        .map(MemberLookup::memberId)
        .orElseThrow(() -> new StaleSessionException("会員情報が存在しません"));
  }

  private static MemberNotificationSettings effective(
      List<MemberNotificationSetting> rows, boolean emailVerified) {
    return new MemberNotificationSettings(
        statusOf(rows, ContactPurpose.BUSINESS, emailVerified),
        statusOf(rows, ContactPurpose.MARKETING, emailVerified));
  }

  private static ContactPermissionStatus statusOf(
      List<MemberNotificationSetting> rows, ContactPurpose purpose, boolean emailVerified) {
    return rows.stream()
        .filter(row -> row.getPurpose() == purpose)
        .map(MemberNotificationSetting::getStatus)
        .findFirst()
        .orElse(MemberNotificationSetting.defaultFor(purpose, emailVerified));
  }
}
