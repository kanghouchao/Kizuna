package com.kizuna.member.domain;

import com.kizuna.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 会員集約。プラットフォーム層の会員身分であり、店舗 CRM 台帳（Customer）とは別概念。
 *
 * <p>PlatformUser（user_type=MEMBER）と 1 対 1 で対応する（跨集約 ID 参照）。会員コードは来店時に提示する一意の識別子で、
 * 店舗側の顧客台帳との紐づけはこのコードの読み取りによる明示操作のみで成立する（電話番号等による自動マッチングは行わない既定）。
 *
 * <p>ランクはプラットフォーム級の等級で、跨店舗の累計実績から昇格する。店舗級に置くと「ポイントは共有・ランクは店ごとに別」という 語義の撕裂が生じるため、帰属先はここでしかありえない。
 *
 * <p>不変条件（構築時に検証、違反は 400 系ドメイン例外）: 会員コード非空・プラットフォームユーザー ID 必須（{@link InvalidMemberException}）。
 * ランクは構築時に最低位で決まり、builder では受け取らない — 列を写像している以上、作成経路に委ねると省略が DB 既定ではなく null になる。
 */
@Entity
@Table(name = "t_members")
@Getter
@NoArgsConstructor
public class Member extends BaseEntity {

  @Column(name = "member_code", nullable = false, unique = true, length = 20)
  private String memberCode;

  @Column(name = "platform_user_id", nullable = false, unique = true)
  private Long platformUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "rank", nullable = false, length = 20)
  private MemberRank rank;

  @Builder
  public Member(String memberCode, Long platformUserId) {
    if (memberCode == null || memberCode.isBlank()) {
      throw new InvalidMemberException("会員コードは必須です");
    }
    if (platformUserId == null) {
      throw new InvalidMemberException("プラットフォームユーザー ID は必須です");
    }
    this.memberCode = memberCode;
    this.platformUserId = platformUserId;
    this.rank = MemberRank.BRONZE;
  }

  /** 上位のランクへ昇格する。降格も同格への書き換えも受け付けない — ランクの単調性は集約自身が守る不変条件である。 */
  public void promoteTo(MemberRank next) {
    if (next == null || !next.isAbove(rank)) {
      throw new InvalidMemberException("会員ランクは現在より上位へしか変更できません");
    }
    this.rank = next;
  }
}
