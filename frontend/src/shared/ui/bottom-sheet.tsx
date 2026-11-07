'use client';

import type { ReactNode } from 'react';
import { Drawer } from '@base-ui/react/drawer';

interface BottomSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
  description?: ReactNode;
  /** 操作帯。主操作を先頭に、全幅で縦に並ぶ。 */
  footer?: ReactNode;
  children?: ReactNode;
}

/**
 * 本人ポータル（キャストのみ）の下部シート。題名帯と操作帯を固定し、本文だけをスクロールさせる。
 * 下へのスワイプ・背景・Esc で閉じる。入力欄を持つ本文のため、ソフトキーボードが出ても焦点の欄を見せる。
 */
export function BottomSheet({
  open,
  onOpenChange,
  title,
  description,
  footer,
  children,
}: BottomSheetProps) {
  return (
    <Drawer.Root open={open} onOpenChange={next => onOpenChange(next)}>
      <Drawer.VirtualKeyboardProvider>
        <Drawer.Portal>
          <Drawer.Backdrop className="fixed inset-0 z-50 bg-black/50 opacity-[calc(1-var(--drawer-swipe-progress,0))] transition-opacity duration-300 data-ending-style:opacity-0 data-starting-style:opacity-0 data-swiping:duration-0" />
          <Drawer.Viewport className="fixed inset-0 z-50 flex items-end justify-center">
            <Drawer.Popup className="flex max-h-[calc(100dvh-2rem)] w-full max-w-md flex-col rounded-t-2xl bg-card text-card-foreground outline-none [transform:translateY(var(--drawer-swipe-movement-y,0px))] transition-transform duration-300 ease-out data-ending-style:[transform:translateY(100%)] data-starting-style:[transform:translateY(100%)] data-swiping:duration-0 data-swiping:select-none">
              <div className="shrink-0 border-b px-4 pt-2 pb-3">
                <div aria-hidden="true" className="mx-auto mb-3 h-1 w-10 rounded-full bg-muted" />
                <Drawer.Title className="text-lg font-semibold">{title}</Drawer.Title>
                {description && (
                  <Drawer.Description className="mt-1 text-sm text-muted-foreground">
                    {description}
                  </Drawer.Description>
                )}
              </div>
              {children != null && (
                <Drawer.Content className="min-h-0 space-y-4 overflow-y-auto overscroll-contain px-4 py-4">
                  {children}
                </Drawer.Content>
              )}
              {footer && (
                <div className="flex shrink-0 flex-col gap-2 border-t px-4 pt-3 pb-[calc(0.75rem_+_env(safe-area-inset-bottom))] [&>*]:min-h-11 [&>*]:w-full">
                  {footer}
                </div>
              )}
            </Drawer.Popup>
          </Drawer.Viewport>
        </Drawer.Portal>
      </Drawer.VirtualKeyboardProvider>
    </Drawer.Root>
  );
}

/** 下部シートを閉じる操作（「やめる」等）。見た目は render に Button を渡して決める。 */
export const BottomSheetClose = Drawer.Close;
