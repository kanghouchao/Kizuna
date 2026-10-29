package com.kizuna.cast.application;

import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastProfile;

/** 作成した在籍とプロフィール。応答は作成と同じトランザクションで得た値から組む。 */
public record CastCreation(CastEnrollment enrollment, CastProfile profile, boolean deletable) {}
