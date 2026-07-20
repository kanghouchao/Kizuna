import { PlatformCapability } from './types';

/**
 * 実運用の店舗コンソール能力（Capability.Console.STORE のうち標識能力 STORE_MENU_VIEW を除く）。
 * バックエンド PlatformAuthService.hasStoreConsole() の除外規則と同型で保つ（#413 Fix4）。
 */
const STORE_CONSOLE_CAPABILITIES: readonly PlatformCapability[] = [
  'ORDER_MANAGE',
  'CUSTOMER_MANAGE',
  'SHIFT_MANAGE',
  'CAST_MANAGE',
  'CAST_INVITE',
  'CAST_FIELD_DEF_VIEW',
  'CAST_FIELD_DEF_MANAGE',
  'STORE_PROFILE_MANAGE',
];

/**
 * 実運用の店舗コンソール能力を1つでも持つか（=storeBridge 相当）。
 * SHARED 能力（STORE_VIEW/ORDER_SET_MANAGE）や標識能力 STORE_MENU_VIEW 単独では false。
 * store-scoped ページへの到達資格が無いユーザーに店舗切替を出さないための判定に使う（#413 Fix4）。
 */
export function hasStoreConsoleCapability(capabilities: PlatformCapability[]): boolean {
  return capabilities.some(capability => STORE_CONSOLE_CAPABILITIES.includes(capability));
}
