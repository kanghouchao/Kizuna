package com.kizuna.medium.domain;

import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "t_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Medium extends StoreScopedEntity {
  public static final int NAME_MAX = 100;

  @Column(nullable = false, length = NAME_MAX)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MediumStatus status;

  @Builder
  public Medium(String name) {
    this.name = name;
    this.status = MediumStatus.ACTIVE;
  }

  /** 名前は前後の空白（全角を含む）だけを除き、大小文字・全半角は原文のまま保つ。 */
  public static String normalizeName(String name) {
    return name == null ? null : name.strip();
  }

  public void requireVersion(long expected) {
    if (!getVersion().equals(expected)) throw new ConflictException("媒体が更新されています。再読み込みしてください");
  }

  public void rename(String name) {
    this.name = name;
  }

  public void stop() {
    if (status == MediumStatus.STOPPED) throw new ConflictException("この媒体は既に停止しています");
    status = MediumStatus.STOPPED;
  }

  public void resume() {
    if (status == MediumStatus.ACTIVE) throw new ConflictException("この媒体は既に有効です");
    status = MediumStatus.ACTIVE;
  }
}
