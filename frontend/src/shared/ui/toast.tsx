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
        'pointer-events-none fixed inset-x-4 top-4 z-50 mx-auto flex w-auto max-w-sm flex-col items-center gap-2 outline-none',
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

function ToastTitle({ className, ...props }: ToastPrimitive.Title.Props) {
  return (
    <ToastPrimitive.Title
      data-slot="toast-title"
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
        // 配色の上に乗る対として測っていない。色を持たず currentColor と不透明度だけで
        // 出せば、どの段でも本文と同じ certified な対に留まる。
        'shrink-0 rounded-md p-1 opacity-70 outline-none transition-opacity hover:opacity-100 focus-visible:ring-[3px] focus-visible:ring-ring/50',
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
