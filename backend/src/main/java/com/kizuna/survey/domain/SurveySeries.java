package com.kizuna.survey.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_surveys")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveySeries extends StoreScopedEntity {

  @Column(length = 32)
  private String latestRevisionId;

  @Column(nullable = false, updatable = false)
  private Long recordedBy;

  @Column(nullable = false, updatable = false, length = 255)
  private String recorderName;

  public static SurveySeries create(Long actorId, String name) {
    return builder().recordedBy(actorId).recorderName(name).build();
  }

  public void advance(String revisionId) {
    latestRevisionId = SurveyInput.id(revisionId);
  }
}
