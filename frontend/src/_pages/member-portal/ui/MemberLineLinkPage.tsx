'use client';

import { useLineLink } from '@/features/line-auth';
import {
  Button,
  Card,
  CardContent,
  KeyValueList,
  KeyValueRow,
  RegionError,
  Skeleton,
} from '@/shared/ui';

/** LINE連携（アカウントタブの下位画面）。連携済みなら状態だけを出し、解除は提供しない。 */
export function MemberLineLinkPage() {
  const { status, isRedirecting, start, reload } = useLineLink('member-account');

  if (status === 'loading')
    return (
      <div role="status" aria-label="読み込み中">
        <Skeleton className="h-40 w-full rounded-xl" />
      </div>
    );
  if (status === 'failed')
    return (
      <RegionError message="LINE連携の状態を取得できませんでした" onRetry={() => void reload()} />
    );
  if (status === 'unavailable')
    return (
      <Card>
        <CardContent>
          <p>この画面からはLINE連携を利用できません。</p>
        </CardContent>
      </Card>
    );

  return (
    <Card>
      <CardContent className="space-y-4">
        <KeyValueList className="text-lg">
          <KeyValueRow label="状態">{status === 'linked' ? '連携済み' : '未連携'}</KeyValueRow>
        </KeyValueList>
        <p className="text-muted-foreground">
          連携すると、次回からLINEアカウントでログインできます。
        </p>
        {status === 'unlinked' && (
          <Button
            type="button"
            onClick={() => void start()}
            disabled={isRedirecting}
            className="h-12 w-full text-lg"
          >
            {isRedirecting ? 'LINEへ移動中...' : 'LINEを連携する'}
          </Button>
        )}
      </CardContent>
    </Card>
  );
}
