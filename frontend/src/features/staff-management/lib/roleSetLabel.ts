import { RoleRef } from '@/entities/user';

/**
 * ロールの表示文字列（ロール名を「・」区切り）。
 * name はサーバの non_null 方針でキーごと欠落しうるため、欠けた場合は id を代替表示する
 * （素直に name を並べると undefined が画面に出る）。
 */
export function roleSetLabel(roles: RoleRef[]): string {
  return roles.length > 0 ? roles.map(role => role.name ?? String(role.id)).join('・') : '未選択';
}
