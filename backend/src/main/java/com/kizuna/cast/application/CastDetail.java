package com.kizuna.cast.application;

import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastInvitationStatus;
import com.kizuna.cast.domain.CastProfile;

/** 在籍とプロフィールに、読み出した時点の招待状態と削除可否を添えたもの。作成・更新の直後は招待状態を導出しないので null。 */
public record CastDetail(
    CastEnrollment enrollment,
    CastProfile profile,
    CastInvitationStatus invitationStatus,
    boolean deletable) {}
