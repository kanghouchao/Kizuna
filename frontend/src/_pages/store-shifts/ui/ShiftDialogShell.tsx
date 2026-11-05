'use client';

import type { ComponentProps, ReactNode } from 'react';
import { Dialog, DialogFrame } from '@/shared/ui';

interface ShiftDialogShellProps {
  open: boolean;
  onClose: () => void;
  title: string;
  form: ComponentProps<typeof DialogFrame>['form'];
  footer: ReactNode;
  children: ReactNode;
}

/** 出勤管理の 3 つのフォームモーダル（シフトの追加・編集、実績の記録・訂正、実績の取消）が共有する外枠。 */
export function ShiftDialogShell({
  open,
  onClose,
  title,
  form,
  footer,
  children,
}: ShiftDialogShellProps) {
  return (
    <Dialog
      open={open}
      onOpenChange={next => {
        if (!next) onClose();
      }}
    >
      <DialogFrame size="sm" title={title} form={form} footer={footer}>
        {children}
      </DialogFrame>
    </Dialog>
  );
}
