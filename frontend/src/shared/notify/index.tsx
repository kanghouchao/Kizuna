'use client';

import { TriangleAlertIcon } from 'lucide-react';
import { toast } from 'react-hot-toast';

/**
 * 通知の語義層。呼び出し側は事実の文だけを渡し、色・duration・アイコン・前置語は
 * 全てここが決める。三段の判定条文（失敗のあと画面はまだ本当のことを言っているか）は
 * DESIGN.md が正本。
 *
 * success / error はライブラリが型別キーを持つので姿は ToastProvider 側にあり、
 * ここは文言を素通しするだけ。warning はキーが無い（success / error / loading /
 * blank / custom）ため、四点ともここでしか渡せない。
 *
 * 例外を文言へ変える getApiErrorMessage は吞まない。toast 以外の消費者を既に持つ
 * 別の仕事であり、畳み込むと notify の署名が二種類になる。
 */
export const notify = {
  success: (message: string) => toast.success(message),

  error: (message: string) => toast.error(message),

  warning: (message: string) =>
    // blank へ姿を置かず素の toast() に持たせる。blank は型無しの toast() 全てと共有で、
    // そこへ置くと重大度が偶然に獲得され得る。
    toast(`警告：${message}`, {
      duration: 10000,
      // React 要素のアイコンはラッパ無しで toast の flex 行へ挿さり、幅を保つものが無い。
      // shrink-0 はスタイルではなくアイコンの一部——外すと 10.7×20 まで潰れる。
      icon: <TriangleAlertIcon size={20} className="shrink-0" />,
      // style は 基底 → 型別 → 呼び出し の三層で深合成されるため、この二つだけを渡せば
      // 基底の borderRadius は残る。
      style: { background: 'var(--warning)', color: 'var(--warning-foreground)' },
    }),
};
