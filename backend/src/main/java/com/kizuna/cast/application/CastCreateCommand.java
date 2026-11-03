package com.kizuna.cast.application;

import com.kizuna.cast.domain.CastEnrollmentStatus;
import com.kizuna.cast.domain.CastProfile;
import java.time.LocalDate;

/** 在籍と未公開プロフィールを起こす入力。直接登録と採用済み応募者からの在籍登録が共有する。 */
public record CastCreateCommand(
    String name,
    CastEnrollmentStatus status,
    LocalDate startedOn,
    String photoUrl,
    String introduction,
    Integer age,
    Integer height,
    Integer bust,
    Integer waist,
    Integer hip,
    Integer displayOrder) {

  /** 未公開のプロフィールを起こす。表示順の省略は 0。 */
  CastProfile profile(String enrollmentId) {
    return CastProfile.builder()
        .enrollmentId(enrollmentId)
        .name(name)
        .photoUrl(photoUrl)
        .introduction(introduction)
        .age(age)
        .height(height)
        .bust(bust)
        .waist(waist)
        .hip(hip)
        .displayOrder(displayOrder == null ? 0 : displayOrder)
        .build();
  }
}
