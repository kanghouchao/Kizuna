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
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "t_applicants")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor
public class Applicant extends StoreScopedEntity {
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

  public void recordEditor(Long actorId) {
    modifiedBy = Objects.requireNonNull(actorId);
    editSequence++;
  }

  public static Applicant receive(ApplicantIntake intake) {
    Applicant applicant = new Applicant();
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

  private void requireEditable() {
    if (status.isTerminal()) throw new ConflictException("選考が終了した応募者は変更できません");
  }
}
