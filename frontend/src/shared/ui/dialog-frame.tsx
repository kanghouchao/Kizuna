'use client';

import * as React from 'react';
import { createPortal } from 'react-dom';

import { cn } from '@/shared/lib/utils';
import {
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog';

// 幅は段の名前でしか選ばせない。primitive の sm:max-w-lg と修飾子を揃えて上書きするため、
// 段はすべて sm: 付きで字面に持つ（修飾子が違うと両立して狭い方が効く）。
const SIZE_CLASS = {
  sm: 'sm:max-w-md',
  md: 'sm:max-w-2xl',
  lg: 'sm:max-w-4xl',
} as const;

export type DialogFrameSize = keyof typeof SIZE_CLASS;

// 本文の部品が操作を置く先。初回描画では要素が未取得なので null。
const ActionsSlot = React.createContext<HTMLElement | null | undefined>(undefined);

interface DialogFrameProps {
  /** sm 28rem: 確認・1 列の小フォーム / md 42rem: キー値の詳細・2 列のフォーム / lg 56rem: 多列の表 */
  size: DialogFrameSize;
  title: React.ReactNode;
  description?: React.ReactNode;
  footer?: React.ReactNode;
  /** 渡すと本文と操作帯を一つの form で包み、操作帯の submit ボタンが本文の入力を送る。 */
  form?: Omit<React.ComponentProps<'form'>, 'className' | 'children'>;
  /** 閉じる手段を操作帯の明示のボタンだけに絞る段（再表示できない値を出している間等）で false。 */
  showCloseButton?: boolean;
  children?: React.ReactNode;
}

/**
 * ダイアログの外枠。題名帯と操作帯を固定し、本文だけをスクロールさせる。
 * 操作帯の主操作は右端、副操作はその左に置く（footer へ副・主の順に渡す）。
 * 本文の部品が持つ操作は DialogFrameActions で操作帯へ置く。footer の後ろ（主操作の位置）に並ぶ。
 */
export function DialogFrame({
  size,
  title,
  description,
  footer,
  form,
  showCloseButton = true,
  children,
}: DialogFrameProps) {
  const [slot, setSlot] = React.useState<HTMLElement | null>(null);
  const body = (
    <>
      {children != null && (
        <div className="min-h-0 space-y-4 overflow-y-auto px-6 py-4">{children}</div>
      )}
      <DialogFooter
        className={cn(
          'shrink-0 border-t px-6 py-4',
          !footer && 'has-[>[data-slot=dialog-frame-actions]:empty]:hidden'
        )}
      >
        {footer}
        <div ref={setSlot} data-slot="dialog-frame-actions" className="contents" />
      </DialogFooter>
    </>
  );
  return (
    <ActionsSlot.Provider value={slot}>
      <DialogContent
        showCloseButton={showCloseButton}
        className={cn(
          'flex max-h-[calc(100dvh-2rem)] flex-col gap-0 overflow-hidden p-0',
          SIZE_CLASS[size]
        )}
      >
        <DialogHeader className={cn('shrink-0 border-b px-6 py-4', showCloseButton && 'pr-12')}>
          <DialogTitle>{title}</DialogTitle>
          {description && <DialogDescription>{description}</DialogDescription>}
        </DialogHeader>
        {form ? (
          <form {...form} className="flex min-h-0 flex-col">
            {body}
          </form>
        ) : (
          body
        )}
      </DialogContent>
    </ActionsSlot.Provider>
  );
}

/**
 * 本文の部品から外枠の操作帯へ操作を置く。DOM では本文の form の外へ出るので、submit は
 * `form={id}` でその form に結び付ける。
 */
export function DialogFrameActions({ children }: { children: React.ReactNode }) {
  const slot = React.useContext(ActionsSlot);
  if (slot === undefined) {
    throw new Error('DialogFrameActions は DialogFrame の本文の中でだけ使える');
  }
  return slot ? createPortal(children, slot) : null;
}
