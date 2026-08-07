'use client';

import { CircleCheckIcon, CircleXIcon, TriangleAlertIcon, XIcon } from 'lucide-react';
import { Toast as ToastPrimitive } from '@base-ui/react/toast';

import { cn } from '@/shared/lib/utils';

function ToastViewport({ className, ...props }: ToastPrimitive.Viewport.Props) {
  return (
    <ToastPrimitive.Viewport
      data-slot="toast-viewport"
      className={cn(
        // 通知は積み重ねず素直な縦並びで出す。ライブラリの見本はカードを重ねる演出のため
        // Root を絶対配置して `--toast-index` から変形を組むが、こちらの規範は一覧表示。
        // 通常フローに置けば `--toast-offset-y` 等を使わずに済み、段数の計算が要らない。
        // items-center で幅を内容に合わせる。伸ばすと短い一文でも常に最大幅の帯になり、
        // 通知の重さが文の長さではなく枠の大きさで伝わってしまう。
        //
        // z は overlay 一式（dialog / popover / select / menu＝どれも z-50）より上へ置く。
        // 同値だと後から portal した方＝モーダルが勝ち、`error` の段が前提にしている
        // 「モーダルが開いたまま失敗を伝える」場面で、遮罩が文言を暗くし × への操作も
        // 奪う。前台の年齢確認（z-[9999]）より下なのは意図——あれは何にも覆われてはならない。
        'pointer-events-none fixed inset-x-4 top-4 z-[60] mx-auto flex w-auto max-w-sm flex-col items-center gap-2 outline-none',
        className
      )}
      {...props}
    />
  );
}

function Toast({ className, ...props }: ToastPrimitive.Root.Props) {
  return (
    <ToastPrimitive.Root
      data-slot="toast"
      // 上端に出すので、払って消す向きも上。既定は下と右で、上寄せの通知だと画面中央へ
      // 引き下ろす動きになってしまう。
      swipeDirection="up"
      className={cn(
        'pointer-events-auto max-w-full rounded-[10px] p-4 shadow-lg outline-none select-none',
        'focus-visible:ring-[3px] focus-visible:ring-ring/50',
        // 段ごとの配色。いずれも既に測ってある組み合わせで、新しい対を作らない。
        // 型を持たない通知は基底の対（card）に落ちる。
        'bg-card text-card-foreground',
        'data-[type=success]:bg-success data-[type=success]:text-success-foreground',
        'data-[type=error]:bg-destructive data-[type=error]:text-destructive-foreground',
        'data-[type=warning]:bg-warning data-[type=warning]:text-warning-foreground',
        // 上限を超えた通知は DOM に残る。通常フローなので透明にするだけでは場所を取る。
        'data-limited:hidden',
        'transition-opacity duration-200 data-ending-style:opacity-0 data-starting-style:opacity-0',
        className
      )}
      {...props}
    />
  );
}

function ToastContent({ className, ...props }: ToastPrimitive.Content.Props) {
  return (
    <ToastPrimitive.Content
      data-slot="toast-content"
      className={cn('flex items-center gap-3', className)}
      {...props}
    />
  );
}

/**
 * 通知の文言。`Toast.Root` はこれを `aria-labelledby` で指すので、支援技術へ届く名前は
 * ここに入れるしかない——`Description` へ回すと dialog が名前を持たないまま出る。
 *
 * ただし既定の描画要素は `<h2>` で、そのままだと通知の文言がページの見出し階層へ紛れ込む
 * （見出し送りで「保存に失敗しました」が節見出しとして拾われる）。通知はページの節ではない
 * ので `<span>` へ倒す。id は render 先へ載るため、名前の紐付けは保たれる。
 */
function ToastTitle({ className, ...props }: ToastPrimitive.Title.Props) {
  return (
    <ToastPrimitive.Title
      data-slot="toast-title"
      render={<span />}
      className={cn('min-w-0 flex-1 text-sm font-medium', className)}
      {...props}
    />
  );
}

function ToastClose({ className, ...props }: ToastPrimitive.Close.Props) {
  return (
    <ToastPrimitive.Close
      data-slot="toast-close"
      aria-label="通知を閉じる"
      className={cn(
        // 共有の Button を被せない。ghost の hover は `bg-accent` を塗るが、これは段の
        // 配色の上に乗る対として測っていない。素の currentColor なら、どの段でも本文と
        // 同じ certified な対に留まる。
        //
        // 不透明度も持たせない。下げた分だけ地の色と合成され、certified な対から外れる
        // ——実測で明モードの error が 4.57 → 2.64 と図形閾 3:1 を割る。休止状態が見え
        // にくいのを hover で補う形は、そもそも見つける前の状態を直していない。
        //
        // 焦点環も `--ring` ではなく currentColor。段の地は admin の面ではないので
        // `--ring` は不透明でも 1.22〜2.80 しか出ず（`/50` だと 1.04〜1.67）、F6 で
        // 辿り着いた焦点がどこにあるか見えない。currentColor なら本文と同じ対に乗る。
        'shrink-0 rounded-md p-1 outline-none focus-visible:ring-[3px] focus-visible:ring-current',
        className
      )}
      {...props}
    >
      <XIcon className="size-4" aria-hidden="true" />
    </ToastPrimitive.Close>
  );
}

/**
 * 段の記号。色は `currentColor`＝その段の前景 token なので、配色マトリクスの同じ行に乗る。
 * 20px で揃えるのは、三段とも本文の始まる位置を同じにするため。重大度は色と形が運ぶ。
 */
function ToastIcon({ type }: { type: string | undefined }) {
  const Icon =
    type === 'success'
      ? CircleCheckIcon
      : type === 'error'
        ? CircleXIcon
        : type === 'warning'
          ? TriangleAlertIcon
          : null;

  if (!Icon) {
    return null;
  }

  // shrink-0 はスタイルではなく記号の一部。flex 行の中で幅を保つものが他に無く、
  // 外すと本文に押されて縦に潰れる。
  return <Icon data-slot="toast-icon" className="size-5 shrink-0" aria-hidden="true" />;
}

function ToastList() {
  const { toasts } = ToastPrimitive.useToastManager();

  return toasts.map(item => (
    <Toast key={item.id} toast={item}>
      <ToastContent>
        <ToastIcon type={item.type} />
        <ToastTitle />
        <ToastClose />
      </ToastContent>
    </Toast>
  ));
}

/**
 * 通知の描画口。`toastManager` は語義層が持つ実体を受け取る——生成をここに置くと
 * プリミティブ側が発行口にもなり、語義層を迂回して通知を出せてしまう。
 */
function Toaster({ children, ...props }: ToastPrimitive.Provider.Props) {
  return (
    <ToastPrimitive.Provider {...props}>
      {children}
      <ToastPrimitive.Portal data-slot="toast-portal">
        <ToastViewport>
          <ToastList />
        </ToastViewport>
      </ToastPrimitive.Portal>
    </ToastPrimitive.Provider>
  );
}

export { Toast, ToastClose, ToastContent, ToastIcon, ToastTitle, ToastViewport, Toaster };
