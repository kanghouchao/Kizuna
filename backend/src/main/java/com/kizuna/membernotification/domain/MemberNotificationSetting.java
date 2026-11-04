package com.kizuna.membernotification.domain;

import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactPurpose;
import com.kizuna.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 会員の通知設定のうち、一つの用途についての本人の明示の選択（GLOSSARY「会員の通知設定」）。
 *
 * <p>行は許可か拒否だけを持ち、未確認は行の不在で表す。既定を行に写さないのは、業務の既定がメールアドレスの検証状態から解決のたびに導かれ、
 * 写した時点の値で固まると検証が既定に届かなくなるため。
 */
@Entity
@Table(name = "t_member_notification_settings")
@Getter
@NoArgsConstructor
public class MemberNotificationSetting extends BaseEntity {

  @Column(name = "member_id", nullable = false, updatable = false)
  private Long memberId;

  @Enumerated(EnumType.STRING)
  @Column(name = "purpose", nullable = false, updatable = false, length = 20)
  private ContactPurpose purpose;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ContactPermissionStatus status;

  private MemberNotificationSetting(
      Long memberId, ContactPurpose purpose, ContactPermissionStatus status) {
    if (memberId == null || purpose == null) {
      throw new InvalidMemberNotificationSettingException("会員と用途は必須です");
    }
    this.memberId = memberId;
    this.purpose = purpose;
    this.status = explicit(status);
  }

  public static MemberNotificationSetting chosen(
      Long memberId, ContactPurpose purpose, ContactPermissionStatus status) {
    return new MemberNotificationSetting(memberId, purpose, status);
  }

  public void choose(ContactPermissionStatus next) {
    this.status = explicit(next);
  }

  /** 明示の選択が無い用途の既定。業務は検証済みなら許可・未検証なら未確認、販促は検証状態に依らず未確認 — 業務の許可から販促の許可を推定しない（ADR 0039・0040）。 */
  public static ContactPermissionStatus defaultFor(ContactPurpose purpose, boolean emailVerified) {
    return purpose == ContactPurpose.BUSINESS && emailVerified
        ? ContactPermissionStatus.ALLOWED
        : ContactPermissionStatus.UNKNOWN;
  }

  private static ContactPermissionStatus explicit(ContactPermissionStatus status) {
    if (status != ContactPermissionStatus.ALLOWED && status != ContactPermissionStatus.DENIED) {
      throw new InvalidMemberNotificationSettingException("通知の設定は許可か拒否を選んでください");
    }
    return status;
  }
}
