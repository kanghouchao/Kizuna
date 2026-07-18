package com.kizuna.user.domain;

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
 * 授権の付与・変更・停止・再開の追記専用履歴（#382 / #398）。停止後も「誰が・いつ・何を」の実行主体記録を保持するための正本であり、更新・削除は行わない。
 *
 * <p>対象ユーザーは跨集約 ID 参照（{@code platformUserId}）。detail には束名・店舗集合・精算範囲の快照（JSON 文字列）を保存する。
 */
@Entity
@Table(name = "t_grant_history")
@Getter
@NoArgsConstructor
public class GrantHistory extends BaseEntity {

  @Column(name = "platform_user_id", nullable = false)
  private Long platformUserId;

  /** 操作を行った利用者のメールアドレス（実行主体）。 */
  @Column(name = "actor_email", nullable = false, length = 255)
  private String actorEmail;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private GrantAction action;

  /** 授権内容の快照（JSON 文字列: 束名・店舗集合・精算範囲）。 */
  @Column(nullable = false)
  private String detail;

  @Builder
  public GrantHistory(Long platformUserId, String actorEmail, GrantAction action, String detail) {
    this.platformUserId = platformUserId;
    this.actorEmail = actorEmail;
    this.action = action;
    this.detail = detail;
  }
}
