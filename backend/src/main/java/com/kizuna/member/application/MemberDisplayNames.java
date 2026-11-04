package com.kizuna.member.application;

import com.kizuna.member.domain.MemberDisplayNameView;
import com.kizuna.member.domain.MemberRepository;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 会員宛の通知の文案に入れる表示名だけを返す。宛先や退会の有無は {@link MemberLoginAddress} の領分。 */
@Service
@RequiredArgsConstructor
public class MemberDisplayNames {
  private final MemberRepository memberRepository;

  /** 存在しない会員 ID は結果に含まれない。 */
  @Transactional(readOnly = true)
  public Map<Long, String> of(Collection<Long> memberIds) {
    if (memberIds.isEmpty()) return Map.of();
    return memberRepository.findDisplayNames(memberIds).stream()
        .collect(
            Collectors.toUnmodifiableMap(
                MemberDisplayNameView::getMemberId, MemberDisplayNameView::getDisplayName));
  }
}
