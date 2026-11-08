'use client';

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from './alert-dialog';
import { buttonVariants } from './button';
import { cn } from '@/shared/lib/utils';

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  /** タイトルの下に出す補足。省略時はタイトルのみ。 */
  description?: string;
  /** 実行ボタンのラベル。 */
  confirmLabel?: string;
  /** 取り消しボタンのラベル。 */
  cancelLabel?: string;
  /**
   * large は会員の段（本文 18px・押せる領域 48px）。ダイアログは外枠の外へ描かれ、段の大きさを継がない。
   */
  size?: 'default' | 'large';
  /** 実行ボタン押下時。ダイアログは自動で閉じ、その後 onClose も呼ばれる。 */
  onConfirm: () => void;
  /** キャンセル・Esc・実行後の close で呼ばれる。ここで open を false に戻すこと。 */
  onClose: () => void;
}

const LARGE_BUTTON = 'min-h-12 text-lg';

/** 破壊的操作の確認ダイアログ。 */
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = '削除する',
  cancelLabel = 'キャンセル',
  size = 'default',
  onConfirm,
  onClose,
}: ConfirmDialogProps) {
  const large = size === 'large';
  return (
    <AlertDialog
      open={open}
      onOpenChange={next => {
        if (!next) onClose();
      }}
    >
      <AlertDialogContent {...(description ? {} : { 'aria-describedby': undefined })}>
        <AlertDialogHeader>
          <AlertDialogTitle className={cn(large && 'text-xl')}>{title}</AlertDialogTitle>
          {description && (
            <AlertDialogDescription className={cn(large && 'text-lg')}>
              {description}
            </AlertDialogDescription>
          )}
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel className={cn(large && LARGE_BUTTON)}>{cancelLabel}</AlertDialogCancel>
          <AlertDialogAction
            className={cn(buttonVariants({ variant: 'destructive' }), large && LARGE_BUTTON)}
            onClick={onConfirm}
          >
            {confirmLabel}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
