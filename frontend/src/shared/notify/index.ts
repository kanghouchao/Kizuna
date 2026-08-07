'use client';

import { Toast } from '@base-ui/react/toast';

/**
 * 通知の実体。発行するここと描画する `ToastProvider` が同じものを指す必要があるため、
 * 生成はこの一箇所だけ。プリミティブ側に置くと、語義層を迂回して通知を出せる口が
 * もう一つできてしまう。
 */
export const toastManager = Toast.createToastManager();

/**
 * 通知の語義層。呼び出し側は事実の文だけを渡し、段の選択以外は何も書かない——duration は
 * ここが、色とアイコンは `shared/ui/toast.tsx` が段の型から引く。三段の判定条文
 * （失敗のあと画面はまだ本当のことを言っているか）は DESIGN.md が正本。
 *
 * 例外を文言へ変える getApiErrorMessage は吞まない。toast 以外の消費者を既に持つ
 * 別の仕事であり、畳み込むと notify の署名が二種類になる。
 */
export const notify = {
  success: (message: string) =>
    toastManager.add({ type: 'success', title: message, timeout: 3000 }),

  error: (message: string) => toastManager.add({ type: 'error', title: message, timeout: 5000 }),

  // 重大度は色と形が運ぶが、どちらも読み上げには届かない（記号は aria-hidden）。前置語を
  // 語義層が付けるので、新しい呼び出し側がこの段だけ重大度なしで読ませることはできない。
  warning: (message: string) =>
    toastManager.add({ type: 'warning', title: `警告：${message}`, timeout: 10000 }),
};
