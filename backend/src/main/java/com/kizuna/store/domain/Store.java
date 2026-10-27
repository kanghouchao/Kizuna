package com.kizuna.store.domain;

import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "t_stores")
@Getter
@Setter
@NoArgsConstructor
public class Store extends BaseEntity {

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String domain;

  /** 稼働状態。新規登録は必ず準備中から始まるため構築引数には取らず、遷移は {@link #activate()} だけが行う。 */
  @Setter(AccessLevel.NONE)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private StoreStatus status = StoreStatus.PREPARING;

  public Store(String name, String domain) {
    this.name = name;
    this.domain = domain;
  }

  /** 稼働中へ遷移する。店長の明示操作であり、不可逆な遷移なので繰り返しは 400 で拒む。 */
  public void activate() {
    if (this.status == StoreStatus.ACTIVE) {
      throw new ServiceException("この店舗は既に稼働中です");
    }
    this.status = StoreStatus.ACTIVE;
  }
}
