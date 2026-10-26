package com.kizuna.member.application;

import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.application.CredentialOperations;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会員退会のうち会員身分の側（会員行の終端化と利用者の停止・識別子の解放）。
 *
 * <p>ポイント消去・関連解除・予約申請の取り下げは point / customer / order が持ち、それらを一つの取引に束ねる編排は member の上位の
 * モジュールが行う（member はそれらに依存できない）。本サービスは編排の取引へ合流するだけで、自分では取引を始めない。
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class MemberWithdrawal {

  private final UserRepository userRepository;
  private final MemberRepository memberRepository;
  private final CredentialOperations credentialOperations;

  /** 退会した会員と利用者の識別子。編排側がポイント消去・関連解除・申請取り下げの宛先と操作者に使う。 */
  public record WithdrawnMember(long memberId, long userId, OffsetDateTime withdrawnAt) {}

  /**
   * 本人による退会。対象は検証済みトークンの利用者 ID で固定する（電子メールは退会で後継へ移りうる）。会員行を先に押さえて
   * 二重退会（409）と並行する帰属の物化を直列化し、利用者を停止して退会済みにする。
   *
   * <p>会員行のロックは台帳への書き込みより前に取る — 仕訳の外部キー検査は会員行へ KEY SHARE を置くため、後から FOR UPDATE を求めると死錠する。
   */
  public WithdrawnMember withdrawSelf(long userId, String reason) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"));
    Member member =
        memberRepository
            .findByUserIdForUpdate(user.getId())
            .orElseThrow(() -> new StaleSessionException("会員情報が存在しません"));
    OffsetDateTime now = OffsetDateTime.now();
    member.withdraw(reason, user.getId(), now);
    credentialOperations.withdraw(user, now);
    memberRepository.save(member);
    userRepository.save(user);
    return new WithdrawnMember(member.getId(), user.getId(), now);
  }

  /** 職員による代行退会。対象は会員 ID で名指しし、操作者は職員の利用者 ID で記録する。効果と直列化は本人退会と同じで、 存在しない会員は 404、退会済みは 409。 */
  public WithdrawnMember withdrawByStaff(long memberId, long actorUserId, String reason) {
    Member member =
        memberRepository
            .findByIdForUpdate(memberId)
            .orElseThrow(() -> new NotFoundException("会員が見つかりません"));
    User user =
        userRepository
            .findById(member.getUserId())
            .orElseThrow(() -> new NotFoundException("会員の利用者が見つかりません"));
    OffsetDateTime now = OffsetDateTime.now();
    member.withdraw(reason, actorUserId, now);
    credentialOperations.withdraw(user, now);
    memberRepository.save(member);
    userRepository.save(user);
    return new WithdrawnMember(member.getId(), user.getId(), now);
  }
}
