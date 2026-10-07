package com.kizuna.recruitment.domain;

import com.kizuna.shared.exception.ServiceException;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

public record ApplicantInterview(
    OffsetDateTime interviewAt, String interviewer, String notes, Map<String, Boolean> checklist)
    implements Serializable {
  public ApplicantInterview {
    interviewer = ApplicantIntake.text(interviewer, 100);
    notes = ApplicantIntake.text(notes, 5000);
    if (interviewAt == null || interviewer == null) throw new ServiceException("面接日時と面接担当者は必須です");
    if (checklist == null
        || checklist.size() > 30
        || checklist.entrySet().stream()
            .anyMatch(
                e ->
                    e.getKey() == null
                        || e.getKey().isBlank()
                        || Set.of("__proto__", "constructor", "prototype").contains(e.getKey())
                        || e.getKey().length() > 100
                        || e.getKey().indexOf('\0') >= 0
                        || e.getValue() == null))
      throw new ServiceException("確認項目は項目名と確認状況の組で30件以内にしてください");
    checklist = Map.copyOf(checklist);
  }
}
