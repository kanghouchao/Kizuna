package com.kizuna.memberconsole.api.dto;

import com.kizuna.memberconsole.application.MemberDetail;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 会員 1 人の照会結果。明細は件数が伸びるので別の端点（{@code /point-entries}）でページ付きに読む。
 *
 * @param withdrawal 退会情報。未退会なら null で応答から消える
 * @param balances 期限別の利用可能残高（合計は {@code balance}）
 * @param rankHistory 昇格の履歴（古い順。3 段のランクなので高々 2 件）
 * @param links 閲覧者の店舗集合内の店舗ごとの最新の関連（新しい順、解除済みを含む。全区間の履歴は顧客側の紐づけ履歴）
 * @param otherStoreLinkCount 店舗集合外で関連を持つ店舗の数
 */
public record MemberDetailResponse(
    long id,
    String memberCode,
    String displayName,
    String email,
    boolean lineLinked,
    String rank,
    OffsetDateTime registeredAt,
    Withdrawal withdrawal,
    long balance,
    List<ExpiryBalance> balances,
    List<RankTransition> rankHistory,
    List<Link> links,
    long otherStoreLinkCount) {

  public static MemberDetailResponse of(MemberDetail detail) {
    var profile = detail.profile();
    return new MemberDetailResponse(
        profile.id(),
        profile.memberCode(),
        profile.displayName(),
        profile.email(),
        profile.lineLinked(),
        profile.rank(),
        profile.registeredAt(),
        profile.withdrawnAt() == null
            ? null
            : new Withdrawal(
                profile.withdrawnAt(),
                profile.withdrawalReason(),
                profile.withdrawnBySelf(),
                profile.withdrawnByName()),
        detail.balance(),
        detail.balances().stream().map(b -> new ExpiryBalance(b.expiresOn(), b.points())).toList(),
        profile.rankHistory().stream()
            .map(t -> new RankTransition(t.previousRank(), t.newRank(), t.promotedAt()))
            .toList(),
        detail.links().segments().stream()
            .map(
                s ->
                    new Link(
                        s.id(),
                        s.storeId(),
                        s.storeName(),
                        s.customerId(),
                        s.customerName(),
                        s.status().name(),
                        s.reason().name(),
                        s.linkedAt(),
                        s.releasedAt(),
                        s.releaseReason()))
            .toList(),
        detail.links().otherStoreCount());
  }

  /** 退会の事実。操作者の表示名は行が消えていれば null。 */
  public record Withdrawal(
      OffsetDateTime withdrawnAt, String reason, boolean bySelf, String withdrawnByName) {}

  public record ExpiryBalance(LocalDate expiresOn, long points) {}

  public record RankTransition(String previousRank, String newRank, OffsetDateTime promotedAt) {}

  public record Link(
      String id,
      long storeId,
      String storeName,
      String customerId,
      String customerName,
      String status,
      String reason,
      OffsetDateTime linkedAt,
      OffsetDateTime releasedAt,
      String releaseReason) {}
}
