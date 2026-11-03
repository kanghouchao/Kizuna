package com.kizuna.recruitment.application;

import com.kizuna.medium.lookup.MediumReference;
import com.kizuna.recruitment.domain.Applicant;
import com.kizuna.recruitment.domain.ApplicantInterview;
import com.kizuna.recruitment.domain.ApplicantSourceType;
import com.kizuna.recruitment.domain.ApplicantStatus;
import com.kizuna.recruitment.domain.ApplicantSummaryView;
import com.kizuna.recruitment.domain.ReceptionChannel;
import java.time.OffsetDateTime;

/** 応募者の読み取り結果。媒体は現在の媒体を指し、応募元の媒体が無ければ null。 */
public final class ApplicantViews {
  private ApplicantViews() {}

  public record Detail(
      String id,
      String name,
      ApplicantStatus status,
      ReceptionChannel channel,
      ApplicantSourceType sourceType,
      MediumReference medium,
      String assignee,
      OffsetDateTime createdAt,
      Long version,
      String referrer,
      String phone,
      String email,
      String address,
      String experience,
      String desiredConditions,
      ApplicantInterview interview,
      Long modifiedBy,
      OffsetDateTime updatedAt,
      String enrollmentId,
      OffsetDateTime anonymizedAt) {
    static Detail of(Applicant a, MediumReference medium) {
      return new Detail(
          a.getId(),
          a.getName(),
          a.getStatus(),
          a.getChannel(),
          a.getSourceType(),
          medium,
          a.getAssignee(),
          a.getCreatedAt(),
          a.getVersion(),
          a.getReferrer(),
          a.getPhone(),
          a.getEmail(),
          a.getAddress(),
          a.getExperience(),
          a.getDesiredConditions(),
          a.getInterview(),
          a.getModifiedBy(),
          a.getUpdatedAt(),
          a.getEnrollmentId(),
          a.getAnonymizedAt());
    }
  }

  public record Summary(
      String id,
      String name,
      ApplicantStatus status,
      ReceptionChannel channel,
      ApplicantSourceType sourceType,
      MediumReference medium,
      String assignee,
      OffsetDateTime createdAt,
      Long version,
      String enrollmentId,
      OffsetDateTime anonymizedAt) {
    static Summary of(ApplicantSummaryView a, MediumReference medium) {
      return new Summary(
          a.getId(),
          a.getName(),
          a.getStatus(),
          a.getChannel(),
          a.getSourceType(),
          medium,
          a.getAssignee(),
          a.getCreatedAt(),
          a.getVersion(),
          a.getEnrollmentId(),
          a.getAnonymizedAt());
    }
  }
}
