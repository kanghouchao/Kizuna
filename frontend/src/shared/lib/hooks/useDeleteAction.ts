'use client';

import { useState } from 'react';
import { useVersionedWrite } from './useVersionedWrite';

interface DeleteActionOptions<T> {
  /** 削除本体。確認済みの対象を受け取る */
  remove: (target: T) => Promise<void>;
  successMessage: string;
  /** サーバが理由を返さなかったときの文言 */
  errorMessage: string;
  /** 一覧の取り直し。成功・404・409 の後に走る */
  reload: () => void | Promise<void>;
}

/**
 * 一覧行の削除ライフサイクル（確認対象の保持・実行・トースト）。
 * 失敗理由はサーバだけが持つ（授与中ロールの 409 等）ため、文言は常に応答から取る。
 * 一覧の行には失われる入力が無いので、409 は案内を出さずにその場で取り直す。
 * 確認ダイアログの文言は対象に依存するため、描画は呼び出し側の責務（target を読んで組み立てる）。
 */
export function useDeleteAction<T>({
  remove,
  successMessage,
  errorMessage,
  reload,
}: DeleteActionOptions<T>) {
  const [open, setOpen] = useState(false);
  const [target, setTarget] = useState<T | null>(null);
  const writer = useVersionedWrite(reload);

  const confirm = async () => {
    if (!open || !target) return;
    const outcome = await writer.write(() => remove(target), {
      success: successMessage,
      failure: errorMessage,
      onSuccess: () => void reload(),
    });
    if (outcome === 'conflict') await writer.reload();
  };

  return {
    open,
    /** 退出中も確認文を保持するため、対象は次の選択時に置き換える。 */
    target,
    /** 行の削除ボタンから確認を開く */
    ask: (row: T) => {
      setTarget(row);
      setOpen(true);
    },
    confirm,
    cancel: () => setOpen(false),
  };
}
