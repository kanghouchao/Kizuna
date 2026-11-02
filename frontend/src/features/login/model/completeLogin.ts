import { LoginResponse, MeResponse, authApi } from '@/entities/user';
import {
  endSession,
  landSession as landSessionOf,
  storeSessionToken,
  type SessionKind,
} from '@/shared/api';

/** ログイン完了の結果。unsupported は着地先の無い利用者種別で、セッションは破棄済み。 */
export type LoginCompletion = { status: 'ok'; path: string } | { status: 'unsupported' };

function sessionKindOf(userType: MeResponse['user_type']): SessionKind | undefined {
  if (userType === 'STAFF') return 'admin';
  if (userType === 'CAST') return 'cast';
  if (userType === 'MEMBER') return 'member';
  return undefined;
}

/**
 * ログイン応答のトークンをメモリへ置き、着地先パスを返す。
 * パスワードログインと LINE ログインが共有する唯一の後処理で、遷移自体は呼び出し元が行う。
 */
export async function completeLogin(response: LoginResponse): Promise<LoginCompletion> {
  storeSessionToken(response);
  return landSession(await authApi.me(), response.refresh_expires_at);
}

/**
 * トークンを持った後の着地を GET /me の利用者種別で決める。ログイン画面を開いた時点でリフレッシュに成功した
 * 場合も、ここから着地する。想定外の種別は着地先が無いのでセッションを破棄する。
 */
export function landSession(me: MeResponse, refreshExpiresAt: number): LoginCompletion {
  const kind = sessionKindOf(me.user_type);
  if (!kind) {
    endSession('unsupported');
    return { status: 'unsupported' };
  }
  return { status: 'ok', path: landSessionOf(kind, refreshExpiresAt) };
}
