'use client';

import { Toaster } from 'react-hot-toast';

/**
 * Global toast provider for consistent top-center notifications.
 * Keep visual styles and durations centralized here.
 */
export function ToastProvider() {
  return (
    <Toaster
      position="top-center"
      toastOptions={{
        duration: 4000,
        // 色は token class ではなくインライン style の CSS 変数で渡す。react-hot-toast が
        // goober 生成 class で背景を当てるため、class を重ねると勝敗がソース順に依存する。
        // 採る組み合わせと適用範囲は DESIGN.md の contrast matrix 注記が正本。
        style: {
          background: 'var(--card)',
          color: 'var(--card-foreground)',
          borderRadius: '10px',
        },
        success: {
          duration: 2500,
          style: { background: 'var(--success)', color: 'var(--success-foreground)' },
        },
        error: {
          duration: 5000,
          // color も明示する。型別 style は基底 style へ重ねられるだけなので、background だけ
          // 差し替えると文字色が基底のまま残り、赤地の上に matrix にない前景色が乗る。
          style: { background: 'var(--destructive)', color: 'var(--destructive-foreground)' },
        },
      }}
    />
  );
}
