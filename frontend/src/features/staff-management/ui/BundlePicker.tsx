'use client';

import { CapabilityBundleResponse } from '@/entities/user';

interface BundlePickerProps {
  bundles: CapabilityBundleResponse[];
  isLoading: boolean;
  bundleIds: number[];
  onChange: (bundleIds: number[]) => void;
}

/**
 * 権限束のチェックボックス複数選択（束はデータ — 選択肢は GET /platform/capability-bundles から取得。#398）。
 * 一覧の取得は親（モーダル/ドロワー）が行い、要約表示と選択肢で同じデータを共有する。
 */
export function BundlePicker({ bundles, isLoading, bundleIds, onChange }: BundlePickerProps) {
  const toggle = (id: number) => {
    onChange(
      bundleIds.includes(id) ? bundleIds.filter(bundleId => bundleId !== id) : [...bundleIds, id]
    );
  };

  return (
    <div>
      <span className="mb-1 block text-sm font-medium text-gray-700">権限束</span>
      <div className="space-y-1 rounded-md border border-gray-200 p-3">
        {isLoading ? (
          <p className="text-sm text-gray-500">読み込み中...</p>
        ) : (
          bundles.map(bundle => (
            <label key={bundle.id} className="flex items-center gap-2 text-sm text-gray-700">
              <input
                type="checkbox"
                checked={bundleIds.includes(bundle.id)}
                onChange={() => toggle(bundle.id)}
                className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
              />
              {bundle.name}
            </label>
          ))
        )}
      </div>
      <p className="mt-1 text-xs text-gray-500">1 つ以上を選択してください（兼務は複数選択）。</p>
    </div>
  );
}
