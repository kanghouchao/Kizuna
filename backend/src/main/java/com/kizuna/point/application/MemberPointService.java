package com.kizuna.point.application;

import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.application.MemberLookupService.MemberLookup;
import com.kizuna.point.api.dto.MemberPointBalanceResponse;
import com.kizuna.point.api.dto.MemberPointEntryResponse;
import com.kizuna.point.domain.MemberPointEntryView;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.user.domain.PlatformUserRepository;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会員ポータルからのポイント読み取りユースケース。
 *
 * <p>台帳は店舗で分割されない（ADR 0006）ため storeFilter は働かない。本人の会員 ID の一致が唯一の隔離境界であり、必ず問い合わせ自体に載せる。
 *
 * <p>返すのは残高と表示用の明細だけで、書き込みの口は持たない。引き当て・元取引・理由・実行者といった台帳内部の事情は projection の段階で落ちる。
 */
@Service
@RequiredArgsConstructor
public class MemberPointService {

  private final PlatformUserRepository platformUserRepository;
  private final MemberLookupService memberLookupService;
  private final PointEntryRepository pointEntryRepository;
  private final PointLedgerService pointLedgerService;
  private final AppProperties appProperties;

  /** 本人の現在残高。 */
  @Transactional(readOnly = true)
  public MemberPointBalanceResponse balance(String email) {
    return new MemberPointBalanceResponse(pointLedgerService.balance(resolveMemberId(email)));
  }

  /**
   * 本人のポイント明細（全種別・跨店集約）。
   *
   * <p>続きの指定は予約一覧と同じくカーソル（並びの鍵）で受ける。台帳は追加型で行が消えないが、記帳は明細を見ている 最中にも増えるため、件数で位置を指すと続きを取った時点で境界の行を飛ばす。
   *
   * @param cursor 続きの位置。null なら先頭から
   * @param requestedSize 1 回に返す件数の希望値（上限に丸められる）
   */
  @Transactional(readOnly = true)
  public CursorPage<MemberPointEntryResponse> entries(
      String email, String cursor, int requestedSize) {
    Long memberId = resolveMemberId(email);
    int size = CursorPage.clampSize(requestedSize);
    // 続きの有無は上限より 1 件多く取って判る。総件数の問い合わせを毎回撒かずに済む。
    Limit limit = Limit.of(size + 1);
    List<MemberPointEntryView> fetched =
        cursor == null
            ? pointEntryRepository.findMemberEntryViews(memberId, limit)
            : fetchAfter(memberId, PageCursor.decode(cursor), limit);
    return CursorPage.of(fetched, size, MemberPointService::cursorOf).map(this::toResponse);
  }

  private List<MemberPointEntryView> fetchAfter(Long memberId, PageCursor cursor, Limit limit) {
    // 続きの取得でも本人の一致は問い合わせに載せ続ける — カーソルは位置を指すだけで、隔離境界にはならない。
    return pointEntryRepository.findMemberEntryViewsAfter(
        memberId, cursor.timestampKey(), cursor.longId(), limit);
  }

  /** 続きの位置は一覧の並び（記帳時刻 + id）と同じ組で作る。組が並びとずれると、続きが手前へ戻るか行を飛ばす。 */
  private static String cursorOf(MemberPointEntryView view) {
    return new PageCursor(view.getCreatedAt().toString(), String.valueOf(view.getId())).encode();
  }

  private Long resolveMemberId(String email) {
    Long platformUserId =
        platformUserRepository
            .findByEmail(email)
            .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"))
            .getId();
    return memberLookupService
        .findByPlatformUserId(platformUserId)
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
