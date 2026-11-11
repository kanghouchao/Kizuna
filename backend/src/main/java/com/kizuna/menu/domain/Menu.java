package com.kizuna.menu.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kizuna.user.domain.PermissionTree;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** メニューの 1 節。根はグループか店舗のどちらかの木に属し、子はその木の区分見出しの下の項目。 */
@Getter
@Setter
@Entity
@Table(name = "t_menus")
public class Menu {

  @Id private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id")
  @JsonIgnore
  private Menu parent;

  /** 根の節だけが属する木を持つ。子は親の木に属し、この列は NULL。 */
  @Setter(AccessLevel.NONE)
  @Enumerated(EnumType.STRING)
  private PermissionTree tree;

  @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
  @OrderBy("sortOrder ASC")
  private List<Menu> children = new ArrayList<>();

  @Column(nullable = false)
  private String label;

  private String path;

  private String icon;

  private String permission;

  @Column(name = "sort_order", nullable = false)
  private Integer sortOrder;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  /** 楽観ロック用バージョン（全実体共通）。 */
  @Setter(AccessLevel.NONE) // 新規 public setter 禁止規約: バージョンは JPA が管理し外部から設定させない
  @Version
  @Column(nullable = false)
  private Long version;
}
