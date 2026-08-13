package com.kizuna.cast.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * トークンだけを運ぶ招待リクエスト（照会・既存アカウント受諾）。
 *
 * <p>トークンはパスではなく本文に載せる。パスも問い合わせ文字列もリクエストターゲットとして送られ、リバースプロキシとアプリのアクセスログに 72 時間有効な生値がそのまま残るため、
 * 招待が読まれるたびにログへクレデンシャルを書くことになる。所持だけで受諾が通る資格情報のため、GET から POST へ移して本文で受け取る（応答が中間キャッシュに載る経路も同時に断つ）。
 */
@Data
public class CastInvitationTokenRequest {

  @NotBlank(message = "token is required")
  // t_cast_invitations.token VARCHAR(64) と同じ上限。
  @Size(max = 64)
  private String token;
}
