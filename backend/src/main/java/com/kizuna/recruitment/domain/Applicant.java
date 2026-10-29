package com.kizuna.recruitment.domain;

import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "t_applicants")
@Getter
@NoArgsConstructor
public class Applicant extends StoreScopedEntity {
  /** 匿名化後の氏名。本人を特定できない固定文で、一覧の表示と検索にそのまま出る。 */
  public static final String ANONYMIZED_NAME = "匿名化済み応募者";

  @Column(nullable = false, length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ReceptionChannel channel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ApplicantSourceType sourceType;

  @Column(length = 200)
  private String sourceMedia;

  @Column(length = 100)
  private String referrer;

  @Column(length = 100)
  private String assignee;

  @Column(length = 50)
  private String phone;

  @Column(length = 254)
  private String email;

  @Column(length = 500)
  private String address;

  @Column(length = 3000)
  private String experience;

  @Column(length = 3000)
  private String desiredConditions;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ApplicantStatus status = ApplicantStatus.RECEIVED;

  @Type(JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  private ApplicantInterview interview;

  @Column(nullable = false)
  private long editSequence;

  @Column(nullable = false)
  private Long modifiedBy;

  @Column(length = 64)
  private String enrollmentId;

  private OffsetDateTime anonymizedAt;

  public void recordEditor(Long actorId) {
    modifiedBy = Objects.requireNonNull(actorId);
    editSequence++;
  }

  public static Applicant receive(Long storeId, ApplicantIntake intake) {
    Applicant applicant = new Applicant();
    applicant.setStoreId(storeId);
    applicant.replaceIntake(intake);
    return applicant;
  }

  public void replaceIntake(ApplicantIntake intake) {
    requireEditable();
    name = intake.name();
    channel = intake.channel();
    sourceType = intake.sourceType();
    sourceMedia = intake.sourceMedia();
    referrer = intake.referrer();
    assignee = intake.assignee();
    phone = intake.phone();
    email = intake.email();
    address = intake.address();
    experience = intake.experience();
    desiredConditions = intake.desiredConditions();
  }

  public void recordInterview(ApplicantInterview interview) {
    requireEditable();
    this.interview = Objects.requireNonNull(interview);
  }

  public void requireVersion(Long expected) {
    if (expected == null || !Objects.equals(getVersion(), expected))
      throw new ConflictException("応募者情報が更新されています。再読み込みして確認してください");
  }

  public void transition(ApplicantStatus target, String reason) {
    requireEditable();
    if (target == status) throw new ConflictException("既に同じ選考状態です");
    if (ApplicantIntake.text(reason, 1000) == null) throw new ServiceException("変更理由は必須です");
    if (target == ApplicantStatus.HIRED || target == ApplicantStatus.REJECTED)
      throw new ServiceException("採否の確定には専用操作が必要です");
    boolean allowed =
        target == ApplicantStatus.WITHDRAWN
            || (status == ApplicantStatus.RECEIVED && target == ApplicantStatus.SCREENING)
            || (status == ApplicantStatus.SCREENING && target == ApplicantStatus.INTERVIEWED)
            || (status == ApplicantStatus.INTERVIEWED && target == ApplicantStatus.SCREENING);
    if (!allowed) throw new ServiceException("この選考状態には変更できません");
    if (target == ApplicantStatus.INTERVIEWED && interview == null)
      throw new ServiceException("面接記録を登録してから面接済に変更してください");
    status = target;
  }

  public void decide(ApplicantStatus target, String reason) {
    requireEditable();
    if (ApplicantIntake.text(reason, 1000) == null) throw new ServiceException("確定理由は必須です");
    if (target != ApplicantStatus.HIRED && target != ApplicantStatus.REJECTED)
      throw new ServiceException("採否の確定には採用または不採用を指定してください");
    if (target == ApplicantStatus.HIRED && status != ApplicantStatus.INTERVIEWED)
      throw new ServiceException("採用は面接済の応募者だけに確定できます");
    status = target;
  }

  /** 在籍登録は選考の終端後の操作なので、終端を拒む requireEditable とは別の前提で判定する。 */
  public void requireEnrollable() {
    if (anonymizedAt != null) throw new ConflictException("匿名化済みの応募者は在籍登録できません");
    if (status != ApplicantStatus.HIRED) throw new ServiceException("在籍登録は採用済みの応募者だけに行えます");
    if (enrollmentId != null) throw new ConflictException("この応募者は既に在籍登録されています");
  }

  public void recordEnrollment(String enrollmentId) {
    requireEnrollable();
    this.enrollmentId = Objects.requireNonNull(enrollmentId);
  }

  /** 選考の経緯（状態・担当者・面接日時と面接担当者・在籍 ID）は残し、本人を特定できる項目だけを消す。 */
  public void anonymize(OffsetDateTime now) {
    if (!status.isTerminal() || anonymizedAt != null)
      throw new IllegalStateException("匿名化できるのは未匿名化の終端状態の応募者だけです");
    name = ANONYMIZED_NAME;
    referrer = null;
    phone = null;
    email = null;
    address = null;
    experience = null;
    desiredConditions = null;
    if (interview != null)
      interview =
          new ApplicantInterview(interview.interviewAt(), interview.interviewer(), null, Map.of());
    anonymizedAt = Objects.requireNonNull(now);
  }

  private void requireEditable() {
    if (status.isTerminal()) throw new ConflictException("選考が終了した応募者は変更できません");
  }
}
