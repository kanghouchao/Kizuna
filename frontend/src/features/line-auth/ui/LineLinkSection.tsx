'use client';

import {
  Badge,
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/shared/ui';
import { useLineLink } from '../model/useLineLink';

/**
 * アカウント設定の LINE 連携ブロック。連携済みなら状態表示のみで、解除は提供しない。
 * 公開設定が無効・取得失敗のときはブロックごと描画しない。
 */
export function LineLinkSection() {
  const { status, isRedirecting, start } = useLineLink('account-settings');

  if (status !== 'linked' && status !== 'unlinked') return null;

  return (
    <Card>
      <CardHeader>
        <CardTitle role="heading" aria-level={2}>
          LINE連携
        </CardTitle>
        <CardDescription>連携すると、次回からLINEアカウントでログインできます。</CardDescription>
      </CardHeader>
      <CardContent>
        {status === 'linked' ? (
          <Badge variant="secondary">連携済み</Badge>
        ) : (
          <Button type="button" onClick={() => void start()} disabled={isRedirecting}>
            {isRedirecting ? 'LINEへ移動中...' : 'LINEを連携する'}
          </Button>
        )}
      </CardContent>
    </Card>
  );
}
