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
 * <p>根拠は契機となった帰属記録を指す。昇格は帰属が成立した瞬間にしか起きないため、付与の有無に依らず必ず在る。
 *
 * <p>付与を伴う昇格ではその付与仕訳も併せて残す。帰属記録から仕訳を辿る線は無いため、これが無いと同じ受注に同じ会員の付与が二本並ぶ場合（無効化 → 再発行 →
 * 本人が申領し直す）にどちらが契機だったか読めない。
 *
 * <p>不変条件（構築時に検証、違反は 400 系ドメイン例外 {@link InvalidMemberException}）: 会員
 * ID・根拠の帰属記録・遷移時刻が必須で、遷移先は遷移前より上位。
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

  /** 判定の契機となった帰属記録。 */
  @Column(name = "triggering_attribution_id", nullable = false, updatable = false)
  private Long triggeringAttributionId;

  /** 契機と同時に記帳された付与仕訳（ORDER_GRANT）。付与の無い来店では欠落する。 */
  @Column(name = "triggering_entry_id", updatable = false)
  private Long triggeringEntryId;

  @Column(name = "promoted_at", nullable = false, updatable = false)
  private OffsetDateTime promotedAt;

  private MemberRankHistory(
      Long memberId,
      MemberRank previousRank,
      MemberRank newRank,
      Long triggeringAttributionId,
      Long triggeringEntryId,
      OffsetDateTime promotedAt) {
    if (memberId == null) {
      throw new InvalidMemberException("会員 ID は必須です");
    }
    if (triggeringAttributionId == null) {
      throw new InvalidMemberException("昇格の契機となる帰属記録は必須です");
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
    this.triggeringAttributionId = triggeringAttributionId;
    this.triggeringEntryId = triggeringEntryId;
    this.promotedAt = promotedAt;
  }

  public static MemberRankHistory promoted(
      Long memberId,
      MemberRank previousRank,
      MemberRank newRank,
      Long triggeringAttributionId,
      Long triggeringEntryId,
      OffsetDateTime promotedAt) {
    return new MemberRankHistory(
        memberId, previousRank, newRank, triggeringAttributionId, triggeringEntryId, promotedAt);
  }
}
