'use client';

import { Dialog, DialogPanel, DialogTitle } from '@headlessui/react';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'react-hot-toast';
import { PlatformRole, PlatformStoreScopeType, platformStaffApi } from '@/entities/user';
import { getApiErrorMessage } from '@/shared/lib';
import { STAFF_ROLE_OPTIONS } from '../lib/roles';
import { StoreSetPicker } from './StoreSetPicker';

interface StaffCreateModalProps {
  open: boolean;
  onClose: () => void;
  /** 作成成功後に呼ばれる（一覧の再取得用）。 */
  onCreated: () => void;
}

interface StaffCreateFormValues {
  email: string;
  password: string;
  display_name: string;
  role: PlatformRole;
}

const inputClass =
  'w-full rounded-md border-gray-300 text-sm shadow-sm focus:border-blue-500 focus:ring-blue-500';

/** スタッフの新規作成モーダル（メール・初期パスワード・氏名・ロール・担当店舗、#325 D6）。 */
export function StaffCreateModal({ open, onClose, onCreated }: StaffCreateModalProps) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { isSubmitting },
  } = useForm<StaffCreateFormValues>();
  const [storeScopeType, setStoreScopeType] = useState<PlatformStoreScopeType>('ALL_STORES');
  const [storeIds, setStoreIds] = useState<number[]>([]);

  useEffect(() => {
    if (!open) return;
    reset({ email: '', password: '', display_name: '', role: 'STORE_STAFF' });
    setStoreScopeType('ALL_STORES');
    setStoreIds([]);
  }, [open, reset]);

  const submit = async (values: StaffCreateFormValues) => {
    try {
      await platformStaffApi.create({
        ...values,
        store_scope_type: storeScopeType,
        store_ids: storeIds,
      });
      toast.success('スタッフを追加しました');
      onCreated();
      onClose();
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'スタッフの追加に失敗しました'));
    }
  };

  return (
    <Dialog open={open} onClose={onClose} className="relative z-50">
      <div className="fixed inset-0 bg-gray-900/40" aria-hidden="true" />
      <div className="fixed inset-0 flex items-center justify-center p-4">
        <DialogPanel className="w-full max-w-md rounded-[10px] border border-gray-200 bg-white shadow-lg">
          <DialogTitle className="border-b border-gray-200 px-6 py-4 text-lg font-semibold text-gray-900">
            スタッフを追加
          </DialogTitle>
          <form onSubmit={handleSubmit(submit)} className="space-y-4 px-6 py-5">
            <div>
              <label htmlFor="staff-email" className="mb-1 block text-sm font-medium text-gray-700">
                メールアドレス
              </label>
              <input
                id="staff-email"
                type="email"
                {...register('email', { required: true })}
                className={inputClass}
              />
            </div>
            <div>
              <label
                htmlFor="staff-password"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                初期パスワード
              </label>
              <input
                id="staff-password"
                type="password"
                {...register('password', { required: true })}
                className={inputClass}
              />
            </div>
            <div>
              <label
                htmlFor="staff-display-name"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                氏名
              </label>
              <input
                id="staff-display-name"
                type="text"
                {...register('display_name', { required: true })}
                className={inputClass}
              />
            </div>
            <div>
              <label htmlFor="staff-role" className="mb-1 block text-sm font-medium text-gray-700">
                ロール
              </label>
              <select
                id="staff-role"
                {...register('role', { required: true })}
                className={inputClass}
              >
                {STAFF_ROLE_OPTIONS.map(option => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>
            <StoreSetPicker
              storeScopeType={storeScopeType}
              storeIds={storeIds}
              onChange={next => {
                setStoreScopeType(next.storeScopeType);
                setStoreIds(next.storeIds);
              }}
            />
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
