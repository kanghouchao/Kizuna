package com.kizuna.recruitment.domain;

import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.validation.ContactValues;

public record ApplicantIntake(
    String name,
    ReceptionChannel channel,
    ApplicantSourceType sourceType,
    String sourceMedia,
    String referrer,
    String assignee,
    String phone,
    String email,
    String address,
    String experience,
    String desiredConditions) {
  public ApplicantIntake {
    name = text(name, 100);
    sourceMedia = text(sourceMedia, 200);
    referrer = text(referrer, 100);
    assignee = text(assignee, 100);
    phone = text(phone, 50);
    email = ContactValues.email(text(email, 254), "email");
    address = text(address, 500);
    experience = text(experience, 3000);
    desiredConditions = text(desiredConditions, 3000);
    if (name == null || channel == null || sourceType == null)
      throw new ServiceException("氏名・受付チャネル・応募元区分は必須です");
    if (sourceType == ApplicantSourceType.MEDIA && sourceMedia == null)
      throw new ServiceException("媒体経由の応募には媒体名が必要です");
    if (sourceType != ApplicantSourceType.MEDIA && sourceMedia != null)
      throw new ServiceException("媒体名は媒体経由の応募にのみ指定できます");
    boolean introduced =
        sourceType == ApplicantSourceType.REFERRAL || sourceType == ApplicantSourceType.SCOUT;
    if (introduced != (referrer != null))
      throw new ServiceException("紹介・スカウトの場合だけ紹介者またはスカウト名を指定してください");
  }

  static String text(String value, int max) {
    if (value == null || value.isBlank()) return null;
    if (value.length() > max || value.indexOf('\0') >= 0)
      throw new ServiceException("入力の長さまたは文字が不正です");
    return value.strip();
  }
}
