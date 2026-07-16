'use client';

import { Dialog, DialogPanel, DialogTitle } from '@headlessui/react';
import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'react-hot-toast';
import {
  CastFieldDefinitionResponse,
  CastFieldDefinitionUpdateRequest,
  castFieldDefinitionApi,
} from '@/entities/cast';
import { getApiErrorMessage } from '@/shared/lib';

interface CastFieldEditModalProps {
  open: boolean;
  onClose: () => void;
  /** 編集対象。null なら何も表示しない。 */
  definition: CastFieldDefinitionResponse | null;
  /** 更新成功後に呼ばれる(一覧の再取得用)。 */
  onUpdated: () => void;
}

interface CastFieldEditFormValues {
  label: string;
  display_order: number;
  is_public: boolean;
}

const inputClass =
  'w-full rounded-md border-gray-300 text-sm shadow-sm focus:border-blue-500 focus:ring-blue-500';

/** カスタムフィールド定義の編集モーダル(label・表示順・公開設定のみ、key は不変のため編集不可、#277)。 */
export function CastFieldEditModal({
  open,
  onClose,
  definition,
  onUpdated,
}: CastFieldEditModalProps) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { isSubmitting },
  } = useForm<CastFieldEditFormValues>();

  useEffect(() => {
    if (!open || !definition) return;
    reset({
      label: definition.label,
      display_order: definition.display_order,
      is_public: definition.is_public,
    });
  }, [open, definition, reset]);

  const submit = async (values: CastFieldEditFormValues) => {
    if (!definition) return;
    try {
      const request: CastFieldDefinitionUpdateRequest = {
        label: values.label,
        display_order: values.display_order,
        is_public: values.is_public,
      };
      await castFieldDefinitionApi.update(definition.id, request);
      toast.success('フィールドを更新しました');
      onUpdated();
      onClose();
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'フィールドの更新に失敗しました'));
    }
  };

  return (
    <Dialog open={open && definition !== null} onClose={onClose} className="relative z-50">
      <div className="fixed inset-0 bg-gray-900/40" aria-hidden="true" />
      <div className="fixed inset-0 flex items-center justify-center p-4">
        <DialogPanel className="w-full max-w-md rounded-[10px] border border-gray-200 bg-white shadow-lg">
          <DialogTitle className="border-b border-gray-200 px-6 py-4 text-lg font-semibold text-gray-900">
            フィールドを編集
          </DialogTitle>
          <form onSubmit={handleSubmit(submit)} className="space-y-4 px-6 py-5">
            <div>
              <span className="mb-1 block text-sm font-medium text-gray-700">key</span>
              <p className="text-sm text-gray-500">{definition?.key}</p>
            </div>
            <div>
              <label
                htmlFor="field-edit-label"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                label
              </label>
              <input
                id="field-edit-label"
                type="text"
                {...register('label', { required: true })}
                className={inputClass}
              />
            </div>
            <div>
              <label
                htmlFor="field-edit-display-order"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                表示順
              </label>
              <input
                id="field-edit-display-order"
                type="number"
                {...register('display_order', { valueAsNumber: true, required: true })}
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
                {isSubmitting ? '保存中...' : '保存する'}
              </button>
            </div>
          </form>
        </DialogPanel>
      </div>
    </Dialog>
  );
}
