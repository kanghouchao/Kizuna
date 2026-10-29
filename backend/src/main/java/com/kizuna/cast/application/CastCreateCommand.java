package com.kizuna.cast.application;

import com.kizuna.cast.domain.CastEnrollmentStatus;
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
    Integer displayOrder) {}
