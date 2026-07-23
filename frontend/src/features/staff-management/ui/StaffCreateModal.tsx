'use client';

import { Dialog, DialogPanel, DialogTitle } from '@headlessui/react';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'react-hot-toast';
import {
  CapabilityBundleResponse,
  PlatformStoreScopeType,
  platformStaffApi,
} from '@/entities/user';
import { getApiErrorMessage, useManagedList } from '@/shared/lib';
import { BundlePicker } from './BundlePicker';
import { SettlementScopePicker } from './SettlementScopePicker';
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
}

const inputClass =
  'w-full rounded-md border-gray-300 text-sm shadow-sm focus:border-blue-500 focus:ring-blue-500';

/** スタッフの新規作成モーダル（メール・初期パスワード・氏名・権限束・担当店舗・任意の精算範囲）。 */
export function StaffCreateModal({ open, onClose, onCreated }: StaffCreateModalProps) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { isSubmitting },
  } = useForm<StaffCreateFormValues>();
  const [bundleIds, setBundleIds] = useState<number[]>([]);
  const [storeScopeType, setStoreScopeType] = useState<PlatformStoreScopeType>('ALL_STORES');
  const [storeIds, setStoreIds] = useState<number[]>([]);
  const [settlementScopeType, setSettlementScopeType] = useState<PlatformStoreScopeType | null>(
    null
  );
  const [settlementStoreIds, setSettlementStoreIds] = useState<number[]>([]);
  const { items: bundles, isLoading: bundlesLoading } = useManagedList<CapabilityBundleResponse>(
    () => platformStaffApi.bundles(),
    '権限束一覧の取得に失敗しました'
  );

  useEffect(() => {
    if (!open) return;
    reset({ email: '', password: '', display_name: '' });
    setBundleIds([]);
    setStoreScopeType('ALL_STORES');
    setStoreIds([]);
    setSettlementScopeType(null);
    setSettlementStoreIds([]);
  }, [open, reset]);

  const submit = async (values: StaffCreateFormValues) => {
    if (bundleIds.length === 0) {
      toast.error('権限束を 1 つ以上選択してください');
      return;
    }
    try {
      await platformStaffApi.create({
        ...values,
        bundle_ids: bundleIds,
        store_scope_type: storeScopeType,
        store_ids: storeIds,
        settlement_scope_type: settlementScopeType,
        settlement_store_ids: settlementStoreIds,
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
        <DialogPanel className="max-h-full w-full max-w-md overflow-y-auto rounded-[10px] border border-gray-200 bg-white shadow-lg">
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
            <BundlePicker
              bundles={bundles}
              isLoading={bundlesLoading}
              bundleIds={bundleIds}
              onChange={setBundleIds}
            />
            <StoreSetPicker
              storeScopeType={storeScopeType}
              storeIds={storeIds}
              onChange={next => {
                setStoreScopeType(next.storeScopeType);
                setStoreIds(next.storeIds);
              }}
            />
            <SettlementScopePicker
              scopeType={settlementScopeType}
              storeIds={settlementStoreIds}
              onChange={next => {
                setSettlementScopeType(next.scopeType);
                setSettlementStoreIds(next.storeIds);
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
