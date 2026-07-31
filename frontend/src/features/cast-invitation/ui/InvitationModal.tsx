'use client';

import { useRef } from 'react';
import { toast } from 'react-hot-toast';
import { Button, Dialog, DialogContent, DialogTitle, Input, Label } from '@/shared/ui';

interface InvitationModalProps {
  open: boolean;
  /** 招待受諾ページの完全な URL。 */
  link: string;
  /** 有効期限（ISO 文字列）。 */
  expiresAt: string | null;
  onClose: () => void;
}

/** 招待発行モーダル（リンク+コピー+有効期限+跨店注記のみ。LINE送信ボタンは付けない。裁定10）。 */
export function InvitationModal({ open, link, expiresAt, onClose }: InvitationModalProps) {
  const linkInputRef = useRef<HTMLInputElement>(null);

  const handleCopy = async () => {
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(link);
      } else {
        linkInputRef.current?.select();
        if (!document.execCommand('copy')) throw new Error('copy failed');
      }
      toast.success('リンクをコピーしました');
    } catch {
      toast.error('リンクをコピーできませんでした。招待リンクを選択して手動でコピーしてください。');
    }
  };

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
        className="gap-0 rounded-[10px] p-0 sm:max-w-md"
      >
        <DialogTitle className="border-b px-6 py-4 text-lg font-semibold text-foreground">
          招待リンクを発行しました
        </DialogTitle>
        <div className="space-y-4 px-6 py-5">
          <div className="grid gap-1">
            <Label htmlFor="cast-invitation-link">招待リンク</Label>
            <Input
              ref={linkInputRef}
              id="cast-invitation-link"
              type="text"
              readOnly
              value={link}
              className="bg-muted"
            />
          </div>
          {expiresAt && (
            <p className="text-sm text-muted-foreground">
              有効期限: {new Date(expiresAt).toLocaleString('ja-JP')}
            </p>
          )}
          <p className="text-xs text-muted-foreground">
            既に他店舗のキャストとして登録済みの場合、既存アカウントでログインして受諾すると本店舗の権限が追加されます。
          </p>
          <div className="flex justify-end gap-3 border-t pt-4">
            <Button type="button" variant="outline" onClick={onClose}>
              閉じる
            </Button>
            <Button type="button" onClick={handleCopy}>
              コピー
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
