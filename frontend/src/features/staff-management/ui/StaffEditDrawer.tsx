'use client';

import { Dialog, DialogPanel, DialogTitle } from '@headlessui/react';
import { useEffect, useMemo, useState } from 'react';
import { toast } from 'react-hot-toast';
import {
  PlatformRole,
  PlatformStaffResponse,
  PlatformStore,
  PlatformStoreScopeType,
  platformAuthApi,
  platformStaffApi,
} from '@/entities/user';
import { getApiErrorMessage, useManagedList } from '@/shared/lib';
import { STAFF_ROLE_OPTIONS, staffRoleLabel } from '../lib/roles';
import { storeSetLabel } from '../lib/storeSetLabel';
import { StoreSetPicker } from './StoreSetPicker';

interface StaffEditDrawerProps {
  open: boolean;
  onClose: () => void;
  /** 編集対象。null なら何も表示しない。 */
  staff: PlatformStaffResponse | null;
  /** 更新成功後に呼ばれる（一覧の再取得用）。 */
  onUpdated: () => void;
}

const inputClass =
  'w-full rounded-md border-gray-300 text-sm shadow-sm focus:border-blue-500 focus:ring-blue-500';

/** スタッフの権限編集ドロワー（ロール・店舗集合のみ、氏名/メールは非表示。「この設定の結果」要約付き、#325 D6）。 */
export function StaffEditDrawer({ open, onClose, staff, onUpdated }: StaffEditDrawerProps) {
  const [role, setRole] = useState<PlatformRole>('STORE_STAFF');
  const [storeScopeType, setStoreScopeType] = useState<PlatformStoreScopeType>('ALL_STORES');
  const [storeIds, setStoreIds] = useState<number[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { items: stores } = useManagedList<PlatformStore>(
    () => platformAuthApi.stores(),
    '店舗一覧の取得に失敗しました'
  );

  useEffect(() => {
    if (!open || !staff) return;
    setRole(staff.role);
    setStoreScopeType(staff.store_scope_type);
    setStoreIds(staff.store_ids);
  }, [open, staff]);

  const summary = useMemo(() => {
    const scopeLabel = storeSetLabel(storeScopeType, storeIds, stores);
    return `${staff?.display_name ?? ''}さんは ${staffRoleLabel(role)} として ${scopeLabel} のデータにアクセスできます`;
  }, [role, storeScopeType, storeIds, stores, staff]);

  const submit = async () => {
    if (!staff) return;
    setIsSubmitting(true);
    try {
      await platformStaffApi.update(staff.id, {
        role,
        store_scope_type: storeScopeType,
        store_ids: storeIds,
      });
      toast.success('権限を更新しました');
      onUpdated();
      onClose();
    } catch (error) {
      toast.error(getApiErrorMessage(error, '権限の更新に失敗しました'));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog open={open && staff !== null} onClose={onClose} className="relative z-50">
      <div className="fixed inset-0 bg-gray-900/40" aria-hidden="true" />
      <div className="fixed inset-0 flex justify-end">
        <DialogPanel className="flex h-full w-full max-w-md flex-col overflow-y-auto border-l border-gray-200 bg-white shadow-lg">
          <DialogTitle className="border-b border-gray-200 px-6 py-4 text-lg font-semibold text-gray-900">
            {staff?.display_name} の権限を編集
          </DialogTitle>
          <div className="flex-1 space-y-4 px-6 py-5">
            <div>
              <label
                htmlFor="staff-edit-role"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                ロール
              </label>
              <select
                id="staff-edit-role"
                value={role}
                onChange={e => setRole(e.target.value as PlatformRole)}
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
            <div>
              <p className="mb-1 text-sm font-medium text-gray-700">この設定の結果</p>
              <p className="rounded-md bg-blue-50 p-3 text-sm text-blue-800">{summary}</p>
            </div>
          </div>
          <div className="flex justify-end gap-3 border-t border-gray-200 px-6 py-4">
            <button
              type="button"
              onClick={onClose}
              className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
            >
              キャンセル
            </button>
            <button
              type="button"
              onClick={submit}
              disabled={isSubmitting}
              className="rounded-md bg-blue-600 px-5 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
            >
              {isSubmitting ? '保存中...' : '保存する'}
            </button>
          </div>
        </DialogPanel>
      </div>
    </Dialog>
  );
}
