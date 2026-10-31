'use client';

import { RegionError } from '@/shared/ui';
import { useMe } from '../model/MeContext';

/**
 * 権限で出し分けるページが /me を待つ間の表示。取得に失敗したら再試行だけを出し、
 * 権限で出し分ける中身は取得できるまで描かない。
 */
export function MePending() {
  const { failure, reload } = useMe();
  return failure !== null ? (
    <RegionError message="権限を確認できませんでした" onRetry={() => void reload()} />
  ) : (
    <p role="status">読み込み中...</p>
  );
}
