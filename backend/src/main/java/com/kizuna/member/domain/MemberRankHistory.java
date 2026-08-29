package com.kizuna.member.domain;

import com.kizuna.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 会員ランクが上がった事実。追加型の記録で、行は書き換えない。
 *
 * <p>根拠は契機となった付与仕訳そのものを指す。受注 ID で指すと、同じ受注に同じ会員の付与が二本並ぶ場合（無効化 → 再発行 → 本人が申領し直す）に どちらが契機だったか辿れない。
 *
 * <p>不変条件（構築時に検証、違反は 400 系ドメイン例外 {@link InvalidMemberException}）: 会員 ID・根拠の仕訳・遷移時刻が必須で、遷移先は遷移前より上位。
 */
@Entity
@Table(name = "t_member_rank_histories")
@Getter
@NoArgsConstructor
public class MemberRankHistory extends BaseEntity {

  @Column(name = "member_id", nullable = false, updatable = false)
  private Long memberId;

  @Enumerated(EnumType.STRING)
  @Column(name = "previous_rank", nullable = false, updatable = false, length = 20)
  private MemberRank previousRank;

  @Enumerated(EnumType.STRING)
  @Column(name = "new_rank", nullable = false, updatable = false, length = 20)
  private MemberRank newRank;

  /** 判定の契機となった付与仕訳（ORDER_GRANT）。 */
  @Column(name = "triggering_entry_id", nullable = false, updatable = false)
  private Long triggeringEntryId;

  @Column(name = "promoted_at", nullable = false, updatable = false)
  private OffsetDateTime promotedAt;

  private MemberRankHistory(
      Long memberId,
      MemberRank previousRank,
      MemberRank newRank,
      Long triggeringEntryId,
      OffsetDateTime promotedAt) {
    if (memberId == null) {
      throw new InvalidMemberException("会員 ID は必須です");
    }
    if (triggeringEntryId == null) {
      throw new InvalidMemberException("昇格の根拠となる付与仕訳は必須です");
    }
    if (promotedAt == null) {
      throw new InvalidMemberException("昇格の日時は必須です");
    }
    if (previousRank == null || newRank == null || !newRank.isAbove(previousRank)) {
      throw new InvalidMemberException("昇格の記録は上位への遷移でなければなりません");
    }
    this.memberId = memberId;
    this.previousRank = previousRank;
    this.newRank = newRank;
    this.triggeringEntryId = triggeringEntryId;
    this.promotedAt = promotedAt;
  }

  public static MemberRankHistory promoted(
      Long memberId,
      MemberRank previousRank,
      MemberRank newRank,
      Long triggeringEntryId,
      OffsetDateTime promotedAt) {
    return new MemberRankHistory(memberId, previousRank, newRank, triggeringEntryId, promotedAt);
  }
}
