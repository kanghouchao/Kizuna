'use client';

import { useEffect, useState } from 'react';
import { toast } from 'react-hot-toast';
import { platformAuthApi, platformLineApi } from '@/entities/user';
import { startLineAuthorization } from '@/shared/lib';
import {
  Badge,
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/shared/ui';

/**
 * アカウント設定の LINE 連携ブロック。連携済みなら状態表示のみで、解除は提供しない。
 * 公開設定が無効・取得失敗のときはブロックごと描画しない。
 */
export function LineLinkSection() {
  const [channelId, setChannelId] = useState('');
  const [linked, setLinked] = useState(false);
  const [isRedirecting, setIsRedirecting] = useState(false);

  useEffect(() => {
    const load = async () => {
      try {
        const [config, me] = await Promise.all([platformLineApi.config(), platformAuthApi.me()]);
        setLinked(me.line_linked);
        if (config.enabled && config.channel_id) {
          setChannelId(config.channel_id);
        }
      } catch {
        // 設定ないし本人情報を取得できない場合はブロックを出さない
      }
    };
    void load();
  }, []);

  if (!channelId) return null;

  const start = async () => {
    setIsRedirecting(true);
    try {
      await startLineAuthorization(channelId, 'link');
    } catch {
      // PKCE の生成には SubtleCrypto が要り、安全な接続でないと利用できない
      setIsRedirecting(false);
      toast.error('LINE連携を開始できませんでした。安全な接続でお試しください');
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle role="heading" aria-level={2}>
          LINE連携
        </CardTitle>
        <CardDescription>連携すると、次回からLINEアカウントでログインできます。</CardDescription>
      </CardHeader>
      <CardContent>
        {linked ? (
          <Badge variant="secondary">連携済み</Badge>
        ) : (
          <Button type="button" onClick={start} disabled={isRedirecting}>
            {isRedirecting ? 'LINEへ移動中...' : 'LINEを連携する'}
          </Button>
        )}
      </CardContent>
    </Card>
  );
}
