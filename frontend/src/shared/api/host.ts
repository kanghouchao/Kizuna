import { cache } from 'react';
import { headers } from 'next/headers';
import { backendUrl } from '../lib/backend-url';

export interface PublicStore {
  id: string;
  name: string;
}

/**
 * リクエストの Host の役割。'unknown' はどの店舗ドメインにも一致しないホストで、公開サイトは 404 を返す
 * （空の店舗サイトを描くと「キャストがいない」と区別できない）。
 */
export type HostResolution =
  { kind: 'app' } | { kind: 'store'; store: PublicStore } | { kind: 'unknown' };

/**
 * アプリドメイン。実行時の env が唯一の根拠で、動的描画の中で読む
 * （`NEXT_PUBLIC_*` や静的プリレンダではビルド時の値に固定される）。
 */
export function appDomain(): string {
  return process.env.APP_DOMAIN || 'kizuna.test';
}

async function requestHostname(): Promise<string> {
  const requestHeaders = await headers();
  const rawHost = requestHeaders.get('x-forwarded-host') || requestHeaders.get('host') || '';
  return rawHost.split(',')[0].trim().split(':')[0].toLowerCase();
}

/**
 * Host から店舗を解決する。lookup は毎回バックエンドへ問い合わせる — 削除した店舗を次のリクエストから
 * 404 にするため（バックエンドの店舗キャッシュは削除時に失効する）。同一リクエスト内は cache で 1 回にまとめる。
 * 未登録と判定するのは lookup の 404 だけで、障害まで未登録に倒すと実在する店舗が障害中に 404 になる。
 */
export const resolveHost = cache(async (): Promise<HostResolution> => {
  const hostname = await requestHostname();
  if (hostname === appDomain()) {
    return { kind: 'app' };
  }

  const res = await fetch(`${backendUrl()}/stores/lookup?domain=${encodeURIComponent(hostname)}`, {
    cache: 'no-store',
  });
  if (res.status === 404) {
    return { kind: 'unknown' };
  }
  if (!res.ok) {
    throw new Error(`店舗の解決に失敗しました: ${res.status}`);
  }

  const data = await res.json();
  return { kind: 'store', store: { id: String(data.id), name: String(data.name ?? '') } };
});
