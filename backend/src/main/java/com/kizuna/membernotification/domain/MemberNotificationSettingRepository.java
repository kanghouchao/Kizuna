package com.kizuna.membernotification.domain;

import com.kizuna.customer.domain.ContactPurpose;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 会員の通知設定。グループ級の行で、境界は会員 ID。 */
public interface MemberNotificationSettingRepository
    extends JpaRepository<MemberNotificationSetting, Long> {

  List<MemberNotificationSetting> findByMemberId(Long memberId);

  Optional<MemberNotificationSetting> findByMemberIdAndPurpose(
      Long memberId, ContactPurpose purpose);
}
