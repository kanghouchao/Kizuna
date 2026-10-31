import type { MeResponse, PermissionCode } from './types';

/**
 * 権限で出し分ける操作の判定。取得前・取得失敗（me が null）は持たない扱いにし、操作を出さない。
 * 表示の出し分けにだけ使う — 権限の強制は各エンドポイントのサーバ側認可。
 */
export function hasPermission(me: MeResponse | null, permission: PermissionCode): boolean {
  return me?.permissions?.includes(permission) ?? false;
}
