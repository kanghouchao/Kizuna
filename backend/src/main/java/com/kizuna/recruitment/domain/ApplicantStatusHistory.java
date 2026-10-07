package com.kizuna.recruitment.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_applicant_status_histories")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicantStatusHistory extends StoreScopedEntity {
  @Column(nullable = false, updatable = false)
  private String applicantId;

  @Column(nullable = false, updatable = false)
  private Long actorId;

  @Enumerated(EnumType.STRING)
  @Column(updatable = false)
  private ApplicantStatus previousStatus;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private ApplicantStatus newStatus;

  @Column(length = 1000, nullable = false, updatable = false)
  private String reason;
}
