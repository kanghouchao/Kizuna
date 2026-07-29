'use client';

import { useState } from 'react';
import { toast } from 'react-hot-toast';
import { getApiErrorMessage } from './apiError';

interface DeleteActionOptions<T> {
  /** 削除本体。確認済みの対象を受け取る */
  remove: (target: T) => Promise<void>;
  successMessage: string;
  /** サーバが理由を返さなかったときの文言 */
  errorMessage: string;
  /** 削除成功後の後始末（一覧の取り直し等） */
  onDeleted: () => void;
}

/**
 * 一覧行の削除ライフサイクル（確認対象の保持・実行・トースト）。
 * 失敗理由はサーバだけが持つ（授与中ロールの 409 等）ため、文言は常に応答から取り、
 * 応答が理由を持たないときだけ errorMessage へ落とす。
 * 確認ダイアログの文言は対象に依存するため、描画は呼び出し側の責務（target を読んで組み立てる）。
 */
export function useDeleteAction<T>({
  remove,
  successMessage,
  errorMessage,
  onDeleted,
}: DeleteActionOptions<T>) {
  const [target, setTarget] = useState<T | null>(null);

  const confirm = async () => {
    if (!target) return;
    try {
      await remove(target);
      toast.success(successMessage);
      onDeleted();
    } catch (error) {
      toast.error(getApiErrorMessage(error, errorMessage));
    }
  };

  return {
    /** 確認中の対象。null なら確認ダイアログは閉じている */
    target,
    /** 行の削除ボタンから確認を開く */
    ask: (row: T) => setTarget(row),
    confirm,
    cancel: () => setTarget(null),
  };
}
