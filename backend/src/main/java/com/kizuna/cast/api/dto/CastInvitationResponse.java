package com.kizuna.cast.api.dto;

import com.kizuna.cast.domain.CastInvitation;
import java.time.OffsetDateTime;

/**
 * 招待発行の応答。JSON キーは Jackson 設定により snake_case（expires_at）。 招待リンク URL はバックエンドが持たず、フロントが {@code origin
 * + /auth/invite/{token}} で組む。
 */
public record CastInvitationResponse(String token, OffsetDateTime expiresAt) {

  public static CastInvitationResponse of(CastInvitation invitation) {
    return new CastInvitationResponse(invitation.getToken(), invitation.getExpiresAt());
  }
}
