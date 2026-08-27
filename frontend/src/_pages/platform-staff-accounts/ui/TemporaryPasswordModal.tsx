'use client';

import { notify } from '@/shared/notify';
import { Button, Dialog, DialogContent, DialogTitle, Input, Label } from '@/shared/ui';

interface TemporaryPasswordModalProps {
  open: boolean;
  /** 発行された仮パスワードの生値。閉じると二度と取り出せない。 */
  temporaryPassword: string;
  /** 対象アカウントの表示名。 */
  displayName: string;
  /** 対象アカウントのメールアドレス。表示名は一意でないため、取り違え防止の同定は一意なこちらが担う。 */
  email: string;
  onClose: () => void;
}

/**
 * 仮パスワードの一度きりの表示。ESC と背景クリックでは閉じない — 生値はこの表示にしか無く、
 * 誤って閉じると再発行しかやり直す手段が無いため、閉じる意思は明示のボタンでしか受け取らない。
 */
export function TemporaryPasswordModal({
  open,
  temporaryPassword,
  displayName,
  email,
  onClose,
}: TemporaryPasswordModalProps) {
  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(temporaryPassword);
      notify.success('仮パスワードをコピーしました');
    } catch {
      notify.error('仮パスワードのコピーに失敗しました');
    }
  };

  return (
    <Dialog
      open={open}
      onOpenChange={next => {
        // 閉じる要求（next === false）は握り潰す。閉じるのは下の「閉じる」ボタンだけ。
        if (next) return;
      }}
    >
      <DialogContent
        showCloseButton={false}
        aria-describedby={undefined}
        className="gap-0 rounded-[10px] p-0 sm:max-w-md"
      >
        <DialogTitle className="border-b px-6 py-4 text-lg font-semibold text-foreground">
          仮パスワードを発行しました
        </DialogTitle>
        <div className="space-y-4 px-6 py-5">
          <div className="grid gap-1">
            <Label htmlFor="temporary-password">
              {displayName}（{email}）の仮パスワード
            </Label>
            <Input
              id="temporary-password"
              type="text"
              readOnly
              value={temporaryPassword}
              className="bg-muted font-mono"
            />
          </div>
          <p className="text-sm text-muted-foreground">
            この画面を閉じると二度と表示できません。本人へ安全な手段で伝え、初回ログイン後に変更してもらってください。
          </p>
          <p className="text-xs text-muted-foreground">
            対象の既存セッションは失効済みです。再設定前のログイン状態では操作を続けられません。
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
