'use client';

import { Toaster, type DefaultToastOptions } from 'react-hot-toast';

/**
 * ライブラリが型別キーを持つ二段（success / error）の姿。三段目 warning は型別キーが無いため
 * ここには現れず、shared/notify が姿ごと持つ。判定条文と各値の根拠は DESIGN.md が正本。
 * テストから直接読めるよう名前付きで公開する。
 */
export const toastOptions: DefaultToastOptions = {
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
    duration: 3000,
    style: { background: 'var(--success)', color: 'var(--success-foreground)' },
    // ライブラリ既定はハードコードの緑地に白チェックで 1.92——図形閾 3:1 に届かず、
    // どちらの色も本ドキュメントの測定外。primary（円の塗り）を透明にして形はそのままに、
    // secondary（線）だけ本文と同じ token へ移す。
    iconTheme: { primary: 'transparent', secondary: 'var(--success-foreground)' },
  },
  error: {
    duration: 5000,
    // color も明示する。型別 style は基底 style へ重ねられるだけなので、background だけ
    // 差し替えると文字色が基底のまま残り、赤地の上に matrix にない前景色が乗る。
    style: { background: 'var(--destructive)', color: 'var(--destructive-foreground)' },
    // success と同じ是正。既定の白バツは暗モードで 2.89 しかなく、token へ移すと 6.88。
    iconTheme: { primary: 'transparent', secondary: 'var(--destructive-foreground)' },
  },
};

/**
 * Global toast provider for consistent top-center notifications.
 * Keep visual styles and durations centralized here.
 */
export function ToastProvider() {
  return <Toaster position="top-center" toastOptions={toastOptions} />;
}
