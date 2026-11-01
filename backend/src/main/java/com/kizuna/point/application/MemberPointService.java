package com.kizuna.point.application;

import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.application.MemberLookupService.MemberLookup;
import com.kizuna.point.api.dto.MemberPointBalanceResponse;
import com.kizuna.point.api.dto.MemberPointEntryResponse;
import com.kizuna.point.domain.MemberPointEntryView;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.domain.UserRepository;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会員ポータルからのポイント読み取りユースケース。
 *
 * <p>台帳は店舗で分割されない（ADR 0006）。本人の会員 ID の一致が唯一の隔離境界であり、必ず問い合わせ自体に載せる。
 *
 * <p>返すのは残高と表示用の明細だけで、書き込みの口は持たない。引き当て・元取引・理由・実行者といった台帳内部の事情は projection の段階で落ちる。
 */
@Service
@RequiredArgsConstructor
public class MemberPointService {

  private final UserRepository userRepository;
  private final MemberLookupService memberLookupService;
  private final PointEntryRepository pointEntryRepository;
  private final PointLedgerService pointLedgerService;
  private final AppProperties appProperties;

  /** 本人の現在残高。 */
  @Transactional(readOnly = true)
  public MemberPointBalanceResponse balance(String email) {
    return new MemberPointBalanceResponse(pointLedgerService.balance(resolveMemberId(email)));
  }

  /** 本人のポイント明細（全種別・跨店集約）。 */
  @Transactional(readOnly = true)
  public Page<MemberPointEntryResponse> entries(String email, Pageable pageable) {
    return pointEntryRepository
        .findMemberEntryViews(resolveMemberId(email), pageable)
        .map(this::toResponse);
  }

  private Long resolveMemberId(String email) {
    Long userId =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"))
            .getId();
    return memberLookupService
        .findByUserId(userId)
        .map(MemberLookup::memberId)
        .orElseThrow(() -> new StaleSessionException("会員情報が存在しません"));
  }

  /** 記帳時刻は業務のタイムゾーンで日付へ畳む — JVM のタイムゾーンで畳むと、深夜の記帳が 1 日ずれた日付で並ぶ。 */
  private MemberPointEntryResponse toResponse(MemberPointEntryView view) {
    return new MemberPointEntryResponse(
        view.getCreatedAt().atZoneSameInstant(ZoneId.of(appProperties.getTimezone())).toLocalDate(),
        view.getStoreName(),
        view.getEntryType().name(),
        view.getAmount(),
        view.getExpiresOn());
  }
}
