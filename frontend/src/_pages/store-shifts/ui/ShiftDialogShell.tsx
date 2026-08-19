'use client';

import { ReactNode } from 'react';
import { Dialog, DialogContent, DialogTitle } from '@/shared/ui';

interface ShiftDialogShellProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
}

/**
 * 出勤管理の 3 つのモーダル（シフトの追加・編集、実績の記録・訂正、実績の取消）が共有する外枠。
 *
 * <p>背が伸びる余地のあるフォームを載せるので、はみ出しは器の内側で巻く。閉じる×を持たないのは、
 * 見出しを境界線で区切る形と併せて 3 つの面の姿を一つにするためで、退出はフォームの キャンセルが担う。
 */
export function ShiftDialogShell({ open, onClose, title, children }: ShiftDialogShellProps) {
  return (
    <Dialog
      open={open}
      onOpenChange={next => {
        if (!next) onClose();
      }}
    >
      <DialogContent
        showCloseButton={false}
        aria-describedby={undefined}
        className="max-h-[calc(100vh-2rem)] gap-0 overflow-y-auto rounded-[10px] p-0 sm:max-w-md"
      >
        <DialogTitle className="border-b px-6 py-4">{title}</DialogTitle>
        {children}
      </DialogContent>
    </Dialog>
  );
}
