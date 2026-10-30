'use client';

import { useEffect, useState } from 'react';
import { notify } from '@/shared/notify';
import { authApi, lineApi } from '@/entities/user';
import { isLineAppHost, startLineAuthorization, useAppDomain } from '@/shared/lib';
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
  const [subject, setSubject] = useState('');
  const [isRedirecting, setIsRedirecting] = useState(false);
  const appDomain = useAppDomain();

  useEffect(() => {
    // 店舗ドメイン上ではコールバック URL がチャネル登録のアプリ origin と食い違い認可が成立しないため、入口を出さない
    if (!isLineAppHost(appDomain)) return;
    const load = async () => {
      try {
        const [config, me] = await Promise.all([lineApi.config(), authApi.me()]);
        setLinked(me.line_linked);
        setSubject(me.email ?? '');
        if (config.enabled && config.channel_id) {
          setChannelId(config.channel_id);
        }
      } catch {
        // 設定ないし本人情報を取得できない場合はブロックを出さない
      }
    };
    void load();
  }, [appDomain]);

  if (!channelId) return null;

  const start = async () => {
    setIsRedirecting(true);
    try {
      // 発起主体を認可記録に束縛し、外部認可の往復中に別アカウントへ切り替わった場合の誤連携を防ぐ
      await startLineAuthorization(channelId, 'link', subject);
    } catch {
      // PKCE の生成には SubtleCrypto が要り、安全な接続でないと利用できない
      setIsRedirecting(false);
      notify.error('LINE連携を開始できませんでした。安全な接続でお試しください');
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
