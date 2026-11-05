'use client';

import { Button, Dialog, DialogFrame } from '@/shared/ui';
import { ReceiptTokenPanel } from './ReceiptTokenPanel';

interface ReceiptTokenDialogProps {
  /** 発行された伝票トークンの生値。null の間は出さない。 */
  token: string | null;
  /** 発行元の受注を名指す一行（営業日・顧客）。 */
  description: string;
  claimNote: string;
  /** 閉じるボタンでだけ呼ばれる。呼出側が一覧へ遷移する。 */
  onClose: () => void;
}

/**
 * 作業画面の上に出す伝票QRコードの閉じないダイアログ。生値はこの表示にしか現れないので、
 * Esc・背景押下の閉じる要求は無視し、× も出さず、操作帯のボタンでだけ閉じる。
 */
export function ReceiptTokenDialog({
  token,
  description,
  claimNote,
  onClose,
}: ReceiptTokenDialogProps) {
  return (
    <Dialog open={token !== null} onOpenChange={() => {}}>
      <DialogFrame
        size="sm"
        showCloseButton={false}
        title="伝票QRコード"
        description={description}
        footer={
          <Button type="button" onClick={onClose}>
            閉じて一覧へ
          </Button>
        }
      >
        {token !== null && <ReceiptTokenPanel token={token} claimNote={claimNote} />}
      </DialogFrame>
    </Dialog>
  );
}
