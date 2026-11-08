'use client';

import { useLineLink } from '@/features/line-auth';
import { Button, KeyValueList, KeyValueRow, RegionError, Skeleton } from '@/shared/ui';
import { PANEL } from './RemunerationDetail';

/** LINE連携（アカウントタブの下位画面）。連携済みなら状態だけを出し、解除は提供しない。 */
export function CastLineLinkPage() {
  const { status, isRedirecting, start, reload } = useLineLink('cast-account');

  if (status === 'loading')
    return (
      <div role="status" aria-label="読み込み中">
        <Skeleton className="h-36 w-full rounded-xl" />
      </div>
    );
  if (status === 'failed')
    return (
      <RegionError message="LINE連携の状態を取得できませんでした" onRetry={() => void reload()} />
    );
  if (status === 'unavailable')
    return (
      <div className={PANEL}>
        <p>この画面からはLINE連携を利用できません。</p>
      </div>
    );

  return (
    <div className={PANEL}>
      <KeyValueList className="text-base">
        <KeyValueRow label="状態">{status === 'linked' ? '連携済み' : '未連携'}</KeyValueRow>
      </KeyValueList>
      <p className="text-sm text-muted-foreground">
        連携すると、次回からLINEアカウントでログインできます。
      </p>
      {status === 'unlinked' && (
        <Button
          type="button"
          onClick={() => void start()}
          disabled={isRedirecting}
          className="min-h-11 w-full text-base"
        >
          {isRedirecting ? 'LINEへ移動中...' : 'LINEを連携する'}
        </Button>
      )}
    </div>
  );
}
