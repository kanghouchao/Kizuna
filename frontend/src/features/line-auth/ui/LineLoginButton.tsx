'use client';

import { useEffect, useState } from 'react';
import { notify } from '@/shared/notify';
import { platformLineApi } from '@/entities/user';
import { isLinePlatformHost, startLineAuthorization } from '@/shared/lib';

/**
 * 統一ログイン画面の LINE ログイン入口。
 * 公開設定が無効・チャネル未設定・設定の取得失敗のいずれでも何も描画しない
 * （押しても始まらないボタンを見せない。パスワードログインは影響を受けない）。
 */
export function LineLoginButton() {
  const [channelId, setChannelId] = useState('');
  const [isRedirecting, setIsRedirecting] = useState(false);

  useEffect(() => {
    // 店舗ドメイン上ではコールバック URL がチャネル登録の平台 origin と食い違い認可が成立しないため、入口を出さない
    if (!isLinePlatformHost()) return;
    const load = async () => {
      try {
        const config = await platformLineApi.config();
        if (config.enabled && config.channel_id) {
          setChannelId(config.channel_id);
        }
      } catch {
        // 設定を取得できない場合は入口ごと出さない
      }
    };
    void load();
  }, []);

  if (!channelId) return null;

  const start = async () => {
    setIsRedirecting(true);
    try {
      await startLineAuthorization(channelId, 'login');
    } catch {
      // PKCE の生成には SubtleCrypto が要り、安全な接続でないと利用できない
      setIsRedirecting(false);
      notify.error('LINEログインを開始できませんでした。安全な接続でお試しください');
    }
  };

  return (
    <div className="mt-8 space-y-6">
      <div className="auth-divider">または</div>
      <button
        type="button"
        onClick={start}
        disabled={isRedirecting}
        className="auth-btn auth-btn--line"
      >
        {isRedirecting ? (
          <span className="flex items-center justify-center gap-2.5">
            <span className="auth-spinner" />
            LINEへ移動中...
          </span>
        ) : (
          'LINEでログイン'
        )}
      </button>
    </div>
  );
}
