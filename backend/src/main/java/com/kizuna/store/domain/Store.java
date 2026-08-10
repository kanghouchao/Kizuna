package com.kizuna.store.domain;

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

  @Column private String email;

  /** 稼働状態。新規登録は必ず準備中から始まるため構築引数には取らず、遷移は {@link #activate()} だけが行う。 */
  @Setter(AccessLevel.NONE)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private StoreStatus status = StoreStatus.PREPARING;

  public Store(String name, String domain, String email) {
    this.name = name;
    this.domain = domain;
    this.email = email;
  }

  /**
   * 稼働中へ遷移する。
   *
   * <p>既に稼働中なら何もしない。遷移の引き金は店舗側利用者の店舗コンソール着地で、同じ利用者が繰り返し訪れるたびに 呼ばれるため、冪等でなければならない。
   */
  public void activate() {
    this.status = StoreStatus.ACTIVE;
  }
}
