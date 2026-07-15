'use client';

import { Dialog, DialogPanel, DialogTitle } from '@headlessui/react';
import { toast } from 'react-hot-toast';

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
  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(link);
      toast.success('リンクをコピーしました');
    } catch {
      toast.error('リンクのコピーに失敗しました');
    }
  };

  return (
    <Dialog open={open} onClose={onClose} className="relative z-50">
      <div className="fixed inset-0 bg-gray-900/40" aria-hidden="true" />
      <div className="fixed inset-0 flex items-center justify-center p-4">
        <DialogPanel className="w-full max-w-md rounded-[10px] border border-gray-200 bg-white shadow-lg">
          <DialogTitle className="border-b border-gray-200 px-6 py-4 text-lg font-semibold text-gray-900">
            招待リンクを発行しました
          </DialogTitle>
          <div className="space-y-4 px-6 py-5">
            <div>
              <label
                htmlFor="cast-invitation-link"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                招待リンク
              </label>
              <input
                id="cast-invitation-link"
                type="text"
                readOnly
                value={link}
                className="w-full rounded-md border-gray-300 bg-gray-50 text-sm shadow-sm focus:border-blue-500 focus:ring-blue-500"
              />
            </div>
            {expiresAt && (
              <p className="text-sm text-gray-600">
                有効期限: {new Date(expiresAt).toLocaleString('ja-JP')}
              </p>
            )}
            <p className="text-xs text-gray-500">
              既に他店舗のキャストとして登録済みの場合、既存アカウントでログインして受諾すると本店舗の権限が追加されます。
            </p>
            <div className="flex justify-end gap-3 border-t border-gray-200 pt-4">
              <button
                type="button"
                onClick={onClose}
                className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
              >
                閉じる
              </button>
              <button
                type="button"
                onClick={handleCopy}
                className="rounded-md bg-blue-600 px-5 py-2 text-sm font-medium text-white hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
              >
                コピー
              </button>
            </div>
          </div>
        </DialogPanel>
      </div>
    </Dialog>
  );
}
