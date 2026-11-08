'use client';

import { useState } from 'react';
import { notify } from '@/shared/notify';
import { authApi, lineApi } from '@/entities/user';
import {
  isLineAppHost,
  startLineAuthorization,
  useAppDomain,
  useResource,
  type LineLinkReturn,
} from '@/shared/lib';

/**
 * 本人の LINE 連携の状態。unavailable は店舗ドメイン上・公開設定が無効のどちらかで、
 * どちらも連携の入口を出してはいけない。
 */
export type LineLinkStatus = 'loading' | 'failed' | 'unavailable' | 'unlinked' | 'linked';

interface LinkSource {
  channelId: string;
  linked: boolean;
  subject: string;
}

/** LINE 連携の状態と開始操作。returnTo は連携の完了・失敗後にコールバックが戻す画面。 */
export function useLineLink(returnTo: LineLinkReturn) {
  const appDomain = useAppDomain();
  const [isRedirecting, setIsRedirecting] = useState(false);
  // useResource は null を失敗と数えるため、「連携できない」は source: null の包みで返す
  const { data, isLoading, failure, reload } = useResource<{
    source: LinkSource | null;
  }>(async () => {
    // 店舗ドメイン上ではコールバック URL がチャネル登録のアプリ origin と食い違い認可が成立しないため、入口を出さない
    if (!isLineAppHost(appDomain)) return { source: null };
    const [config, me] = await Promise.all([lineApi.config(), authApi.me()]);
    if (!config.enabled || !config.channel_id) return { source: null };
    return {
      source: { channelId: config.channel_id, linked: me.line_linked, subject: me.email ?? '' },
    };
  }, [appDomain]);
  const source = data?.source ?? null;

  const status: LineLinkStatus = isLoading
    ? 'loading'
    : failure !== null
      ? 'failed'
      : source === null
        ? 'unavailable'
        : source.linked
          ? 'linked'
          : 'unlinked';

  const start = async () => {
    if (source === null) return;
    setIsRedirecting(true);
    try {
      // 発起主体を認可記録に束縛し、外部認可の往復中に別アカウントへ切り替わった場合の誤連携を防ぐ
      await startLineAuthorization(source.channelId, {
        intent: 'link',
        subject: source.subject,
        returnTo,
      });
    } catch {
      // PKCE の生成には SubtleCrypto が要り、安全な接続でないと利用できない
      setIsRedirecting(false);
      notify.error('LINE連携を開始できませんでした。安全な接続でお試しください');
    }
  };

  return { status, isRedirecting, start, reload };
}
