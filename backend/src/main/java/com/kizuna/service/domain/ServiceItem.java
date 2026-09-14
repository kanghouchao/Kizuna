package com.kizuna.service.domain;

import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_services")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServiceItem extends StoreScopedEntity {
  @Embedded private ServiceTerms terms;

  @Column(nullable = false)
  private long revisionNumber = 1;

  @Column(nullable = false)
  private boolean deleted;

  @Builder
  private ServiceItem(ServiceTerms terms) {
    this.terms = Objects.requireNonNull(terms);
  }

  public static ServiceItem create(ServiceTerms terms) {
    return ServiceItem.builder().terms(terms).build();
  }

  public boolean replace(ServiceTerms next, long expectedVersion) {
    requireEditable(expectedVersion);
    if (terms.getKind() != next.getKind()) throw new ServiceException("サービス種別は変更できません");
    if (terms.equals(next)) return false;
    terms = next;
    revisionNumber++;
    return true;
  }

  public void delete(long expectedVersion) {
    requireEditable(expectedVersion);
    deleted = true;
    revisionNumber++;
  }

  private void requireEditable(long expectedVersion) {
    if (deleted) throw new ServiceException("削除済みのサービスは変更できません");
    if (expectedVersion != revisionNumber)
      throw new ConflictException("設定が変更されています。最新の内容を再取得して確認してください");
  }
}
