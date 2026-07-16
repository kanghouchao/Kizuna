'use client';

import { Dialog, DialogPanel, DialogTitle } from '@headlessui/react';
import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'react-hot-toast';
import { CastFieldDefinitionCreateRequest, castFieldDefinitionApi } from '@/entities/cast';
import { getApiErrorMessage } from '@/shared/lib';

interface CastFieldCreateModalProps {
  open: boolean;
  onClose: () => void;
  /** 作成成功後に呼ばれる(一覧の再取得用)。 */
  onCreated: () => void;
}

interface CastFieldCreateFormValues {
  key: string;
  label: string;
  is_public: boolean;
}

const inputClass =
  'w-full rounded-md border-gray-300 text-sm shadow-sm focus:border-blue-500 focus:ring-blue-500';

/** カスタムフィールド定義の新規作成モーダル(key・label・公開設定、#277)。 */
export function CastFieldCreateModal({ open, onClose, onCreated }: CastFieldCreateModalProps) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { isSubmitting },
  } = useForm<CastFieldCreateFormValues>();

  useEffect(() => {
    if (!open) return;
    reset({ key: '', label: '', is_public: false });
  }, [open, reset]);

  const submit = async (values: CastFieldCreateFormValues) => {
    try {
      const request: CastFieldDefinitionCreateRequest = {
        key: values.key,
        label: values.label,
        is_public: values.is_public,
      };
      await castFieldDefinitionApi.create(request);
      toast.success('フィールドを追加しました');
      onCreated();
      onClose();
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'フィールドの追加に失敗しました'));
    }
  };

  return (
    <Dialog open={open} onClose={onClose} className="relative z-50">
      <div className="fixed inset-0 bg-gray-900/40" aria-hidden="true" />
      <div className="fixed inset-0 flex items-center justify-center p-4">
        <DialogPanel className="w-full max-w-md rounded-[10px] border border-gray-200 bg-white shadow-lg">
          <DialogTitle className="border-b border-gray-200 px-6 py-4 text-lg font-semibold text-gray-900">
            フィールドを追加
          </DialogTitle>
          <form onSubmit={handleSubmit(submit)} className="space-y-4 px-6 py-5">
            <div>
              <label htmlFor="field-key" className="mb-1 block text-sm font-medium text-gray-700">
                key
              </label>
              <input
                id="field-key"
                type="text"
                {...register('key', { required: true, pattern: /^[a-z][a-z0-9_]*$/ })}
                className={inputClass}
              />
              <p className="mt-1 text-xs text-gray-500">
                英小文字で始まり、英小文字・数字・アンダースコアのみ使用できます(作成後は変更できません)
              </p>
            </div>
            <div>
              <label htmlFor="field-label" className="mb-1 block text-sm font-medium text-gray-700">
                label
              </label>
              <input
                id="field-label"
                type="text"
                {...register('label', { required: true })}
                className={inputClass}
              />
            </div>
            <label className="flex items-center gap-2 text-sm text-gray-700">
              <input
                type="checkbox"
                {...register('is_public')}
                className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
              />
              公開する(公開詳細ページに表示)
            </label>
            <div className="flex justify-end gap-3 border-t border-gray-200 pt-4">
              <button
                type="button"
                onClick={onClose}
                className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
              >
                キャンセル
              </button>
              <button
                type="submit"
                disabled={isSubmitting}
                className="rounded-md bg-blue-600 px-5 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
              >
                {isSubmitting ? '追加中...' : '追加する'}
              </button>
            </div>
          </form>
        </DialogPanel>
      </div>
    </Dialog>
  );
}
