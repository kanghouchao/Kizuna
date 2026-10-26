package com.kizuna.member.application;

import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRankHistory;
import com.kizuna.member.domain.MemberRankHistoryRepository;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.member.domain.MemberSearchView;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 職員向けの会員の検索・照会の読み口。退会済みの会員も当時の電子メールで引ける（ADR 0029）。
 *
 * <p>返すのは会員身分の側（コード・利用者属性・ランクと履歴・退会情報）だけで、残高・明細・関連は各所有モジュールが別に読む。 会員の実体は返さない。
 */
@Service
@RequiredArgsConstructor
public class MemberDirectoryService {

  private final MemberRepository memberRepository;
  private final MemberRankHistoryRepository memberRankHistoryRepository;
  private final UserRepository userRepository;

  /** 会員一覧 1 行。 */
  public record MemberSummary(
      long id,
      String memberCode,
      String displayName,
      String email,
      String rank,
      OffsetDateTime registeredAt,
      OffsetDateTime withdrawnAt) {}

  /** 昇格の事実 1 件。契機の帰属記録・仕訳は職員の照会に要らないので持たない。 */
  /** ランクは enum 名の文字列で渡す — 会員の集約内部（domain）を他モジュールへ露出しないため。 */
  public record RankTransition(String previousRank, String newRank, OffsetDateTime promotedAt) {}

  /**
   * 会員 1 人の身分の照会結果。
   *
   * @param withdrawnByName 退会の操作者の表示名。本人退会なら本人の表示名、操作者の行が消えていれば null
   */
  public record MemberProfile(
      long id,
      String memberCode,
      long userId,
      String displayName,
      String email,
      boolean lineLinked,
      String rank,
      OffsetDateTime registeredAt,
      OffsetDateTime withdrawnAt,
      String withdrawalReason,
      Long withdrawnBy,
      boolean withdrawnBySelf,
      String withdrawnByName,
      List<RankTransition> rankHistory) {}

  /**
   * 会員コード・電子メール・表示名の部分一致で検索する。並びは登録の新しい順に固定し、呼出側の sort は受けない — 並びの列は 利用者行に跨るため、Pageable の sort
   * をそのまま JPQL に足すと存在しない属性で落ちる。
   */
  @Transactional(readOnly = true)
  public Page<MemberSummary> search(MemberStatusFilter status, String search, Pageable pageable) {
    String pattern = likePattern(search);
    Pageable window = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    Page<MemberSearchView> page =
        switch (status) {
          case ACTIVE -> memberRepository.searchActive(pattern, window);
          case WITHDRAWN -> memberRepository.searchWithdrawn(pattern, window);
          case ALL -> memberRepository.searchAll(pattern, window);
        };
    return page.map(
        view ->
            new MemberSummary(
                view.getId(),
                view.getMemberCode(),
                view.getDisplayName(),
                view.getEmail(),
                view.getRank().name(),
                view.getCreatedAt(),
                view.getWithdrawnAt()));
  }

  /** 会員 1 人の身分。存在しなければ空。 */
  @Transactional(readOnly = true)
  public Optional<MemberProfile> findProfile(long memberId) {
    return memberRepository.findById(memberId).map(this::toProfile);
  }

  private MemberProfile toProfile(Member member) {
    User user =
        userRepository
            .findById(member.getUserId())
            .orElseThrow(() -> new IllegalStateException("会員の利用者行が存在しません"));
    boolean bySelf =
        member.getWithdrawnBy() != null && member.getWithdrawnBy().equals(user.getId());
    String withdrawnByName =
        member.getWithdrawnBy() == null
            ? null
            : bySelf
                ? user.getDisplayName()
                : userRepository
                    .findById(member.getWithdrawnBy())
                    .map(User::getDisplayName)
                    .orElse(null);
    List<RankTransition> history =
        memberRankHistoryRepository.findByMemberIdOrderByIdAsc(member.getId()).stream()
            .map(MemberDirectoryService::toTransition)
            .toList();
    return new MemberProfile(
        member.getId(),
        member.getMemberCode(),
        user.getId(),
        user.getDisplayName(),
        user.getEmail(),
        user.getLineUserId() != null,
        member.getRank().name(),
        member.getCreatedAt(),
        member.getWithdrawnAt(),
        member.getWithdrawalReason(),
        member.getWithdrawnBy(),
        bySelf,
        withdrawnByName,
        history);
  }

  private static RankTransition toTransition(MemberRankHistory history) {
    return new RankTransition(
        history.getPreviousRank().name(), history.getNewRank().name(), history.getPromotedAt());
  }

  /** 検索語を小文字の LIKE パターンにする。'!' をエスケープ記号に使い、語の中の % と _ を文字として扱う。 */
  static String likePattern(String search) {
    if (search == null || search.isBlank()) {
      return "%";
    }
    String escaped =
        search
            .strip()
            .toLowerCase(Locale.ROOT)
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_");
    return "%" + escaped + "%";
  }
}
