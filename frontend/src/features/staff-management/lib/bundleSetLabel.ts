import { CapabilityBundleRef } from '@/entities/user';

/** 権限束の表示文字列（束名を「・」区切り）。 */
export function bundleSetLabel(bundles: CapabilityBundleRef[]): string {
  return bundles.length > 0 ? bundles.map(bundle => bundle.name).join('・') : '未選択';
}
