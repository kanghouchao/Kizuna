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

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  /** タイトルの下に出す補足。省略時はタイトルのみ。 */
  description?: string;
  /** 実行ボタンのラベル。 */
  confirmLabel?: string;
  /** 実行ボタン押下時。ダイアログは自動で閉じ、その後 onClose も呼ばれる。 */
  onConfirm: () => void;
  /** キャンセル・Esc・実行後の close で呼ばれる。ここで open を false に戻すこと。 */
  onClose: () => void;
}

/** 破壊的操作の確認ダイアログ。 */
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = '削除する',
  onConfirm,
  onClose,
}: ConfirmDialogProps) {
  return (
    <AlertDialog
      open={open}
      onOpenChange={next => {
        if (!next) onClose();
      }}
    >
      <AlertDialogContent {...(description ? {} : { 'aria-describedby': undefined })}>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          {description && <AlertDialogDescription>{description}</AlertDialogDescription>}
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>キャンセル</AlertDialogCancel>
          <AlertDialogAction
            className={buttonVariants({ variant: 'destructive' })}
            onClick={onConfirm}
          >
            {confirmLabel}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
