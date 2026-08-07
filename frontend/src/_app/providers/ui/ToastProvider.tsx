'use client';

import { Toaster as LegacyToaster, type DefaultToastOptions } from 'react-hot-toast';

import { toastManager } from '@/shared/notify';
import { Toaster } from '@/shared/ui';

/**
 * **旧ライブラリ側**の姿。語義層へ移り切っていない呼び出しがまだ 100 箇所以上あり、それらは
 * 全てここを通る——移行が終われば旧ライブラリごと消えるが、それまでは現役なので規範値との
 * 差は直しておく。テストから直接読めるよう公開する。
 *
 * 段が二つしか無いのは移行の取りこぼしではなく、旧ライブラリの `ToastType` が閉じた union で
 * `warning` を持ち得ないため。呼び出し側にも warning 段は一つも無い（第三段になる予定の
 * 409 二箇所は、今はまだ error として出ている）。**新しい側の三段は対称で、その姿はこの
 * ファイルではなく `shared/ui/toast.tsx` が `data-type` から引く。**
 */
export const legacyToastOptions: DefaultToastOptions = {
  duration: 4000,
  // 色は token class ではなくインライン style の CSS 変数で渡す。旧ライブラリが生成 class で
  // 背景を当てるため、class を重ねると勝敗がソース順に依存する。
  style: {
    background: 'var(--card)',
    color: 'var(--card-foreground)',
    borderRadius: '10px',
  },
  success: {
    duration: 3000,
    style: { background: 'var(--success)', color: 'var(--success-foreground)' },
    // 既定はハードコードの緑地に白チェックで 1.92——図形閾 3:1 に届かず、どちらの色も
    // 本リポジトリの測定外。primary（円の塗り）を透明にして形はそのままに、secondary（線）
    // だけ本文と同じ token へ移す。
    iconTheme: { primary: 'transparent', secondary: 'var(--success-foreground)' },
  },
  error: {
    duration: 5000,
    // color も明示する。型別 style は基底 style へ重ねられるだけなので、background だけ
    // 差し替えると文字色が基底のまま残り、赤地の上に測定外の前景色が乗る。
    style: { background: 'var(--destructive)', color: 'var(--destructive-foreground)' },
    // success と同じ是正。既定の白バツは暗モードで 2.89 しかなく、token へ移すと 6.88。
    iconTheme: { primary: 'transparent', secondary: 'var(--destructive-foreground)' },
  },
};

/**
 * 通知の描画口。語義層が持つ実体を渡して繋ぐ。旧ライブラリの Toaster が並んでいるのは、
 * まだ直接呼んでいる箇所が残っているため——それらが語義層へ移り切った時点で消える。
 */
export function ToastProvider() {
  return (
    <>
      <LegacyToaster position="top-center" toastOptions={legacyToastOptions} />
      <Toaster toastManager={toastManager} />
    </>
  );
}
