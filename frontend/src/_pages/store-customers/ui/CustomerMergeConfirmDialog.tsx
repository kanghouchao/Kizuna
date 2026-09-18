'use client';

import { Button, Dialog, DialogContent, DialogTitle } from '@/shared/ui';

interface CustomerMergeConfirmDialogProps {
  open: boolean;
  /** 台帳に残る行の氏名。 */
  survivingName: string;
  /** 墓標になる行の氏名。 */
  mergedName: string;
  /** 被統合行から存続行へ移る受注の件数。 */
  movedOrderCount: number;
  isSubmitting: boolean;
  onConfirm: () => void;
  onClose: () => void;
}

/**
 * 統合の実行前に挟む確認。
 *
 * 統合に取消（undo）は無い（ADR 0010）。誤って畳んだ行を戻す手立ては統合履歴を根拠にした人手の
 * 修復しかないので、取り消せないことを実行の手前で告げる。
 *
 * 閉じる手段は明示のボタンだけに絞る — ESC や背景押下で閉じられると、取り返しのつかない操作の
 * 確認が「うっかり触れた」で消える。伝票QRの表示（{@code ReceiptTokenPanel} の呼出側）と同じ規律。
 */
export function CustomerMergeConfirmDialog({
  open,
  survivingName,
  mergedName,
  movedOrderCount,
  isSubmitting,
  onConfirm,
  onClose,
}: CustomerMergeConfirmDialogProps) {
  return (
    <Dialog
      open={open}
      // onOpenChange を握り潰すことで ESC と背景押下を無効にする。ConfirmDialog（AlertDialog）は
      // ESC で閉じるため、この確認には使えない。
      onOpenChange={() => {}}
    >
      <DialogContent
        showCloseButton={false}
        aria-describedby={undefined}
        className="gap-0 rounded-[10px] p-0 sm:max-w-md"
      >
        <DialogTitle className="border-b px-6 py-4">顧客を統合しますか？</DialogTitle>
        <div className="space-y-4 px-6 py-5">
          <div className="space-y-2 text-sm">
            <p className="text-foreground">
              <span className="font-medium">{mergedName}</span> を{' '}
              <span className="font-medium">{survivingName}</span> にまとめます。
            </p>
            <p className="text-muted-foreground">
              受注 {movedOrderCount} 件と会員紐づけの履歴が {survivingName} へ移り、{mergedName}{' '}
              は一覧から消えます。
            </p>
          </div>
          {/* 取り消せないことと、転記の期限が「今」であることを操作の前に知らせる。統合後は被統合行の
              値を読む経路が無い — 一覧からも候補からも外れ、旧 ID の詳細は統合先の行を返す */}
          <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive-strong">
            統合は取り消せません。値は自動で合わさらず、{mergedName}{' '}
            にしかない氏名・住所などは統合後どこからも読めなくなります。
            {survivingName} へ残したい値がある場合は、キャンセルして先に転記してください。
          </p>
          <div className="flex justify-end gap-3 border-t pt-4">
            <Button type="button" variant="outline" onClick={onClose} disabled={isSubmitting}>
              キャンセル
            </Button>
            <Button type="button" variant="destructive" onClick={onConfirm} disabled={isSubmitting}>
              {isSubmitting ? '統合中...' : '統合する'}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
