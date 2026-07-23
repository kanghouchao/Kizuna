'use client';

import { Dialog, DialogPanel, DialogTitle } from '@headlessui/react';
import { useEffect, useMemo, useState } from 'react';
import { toast } from 'react-hot-toast';
import {
  CapabilityBundleResponse,
  GrantHistoryEntryResponse,
  PlatformStaffResponse,
  PlatformStore,
  PlatformStoreScopeType,
  platformAuthApi,
  platformStaffApi,
} from '@/entities/user';
import { getApiErrorMessage, useManagedList } from '@/shared/lib';
import { bundleSetLabel } from '../lib/bundleSetLabel';
import { storeSetLabel } from '../lib/storeSetLabel';
import { BundlePicker } from './BundlePicker';
import { SettlementScopePicker } from './SettlementScopePicker';
import { StoreSetPicker } from './StoreSetPicker';

interface StaffEditDrawerProps {
  open: boolean;
  onClose: () => void;
  /** 編集対象。null なら何も表示しない。 */
  staff: PlatformStaffResponse | null;
  /** 更新成功後に呼ばれる（一覧の再取得用）。 */
  onUpdated: () => void;
}

const GRANT_ACTION_LABELS: Record<string, string> = {
  GRANT: '付与',
  CHANGE: '変更',
  STOP: '停止',
  RESUME: '再開',
};

/** スタッフの授権編集ドロワー（権限束・店舗集合・精算範囲・停止/再開と付与履歴。「この設定の結果」要約付き）。 */
export function StaffEditDrawer({ open, onClose, staff, onUpdated }: StaffEditDrawerProps) {
  const [bundleIds, setBundleIds] = useState<number[]>([]);
  const [storeScopeType, setStoreScopeType] = useState<PlatformStoreScopeType>('ALL_STORES');
  const [storeIds, setStoreIds] = useState<number[]>([]);
  const [settlementScopeType, setSettlementScopeType] = useState<PlatformStoreScopeType | null>(
    null
  );
  const [settlementStoreIds, setSettlementStoreIds] = useState<number[]>([]);
  const [enabled, setEnabled] = useState(true);
  const [history, setHistory] = useState<GrantHistoryEntryResponse[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { items: stores } = useManagedList<PlatformStore>(
    () => platformAuthApi.stores(),
    '店舗一覧の取得に失敗しました'
  );
  const { items: bundles, isLoading: bundlesLoading } = useManagedList<CapabilityBundleResponse>(
    () => platformStaffApi.bundles(),
    '権限束一覧の取得に失敗しました'
  );

  useEffect(() => {
    if (!open || !staff) return;
    setBundleIds(staff.bundles.map(bundle => bundle.id));
    setStoreScopeType(staff.store_scope_type);
    setStoreIds(staff.store_ids);
    setSettlementScopeType(staff.settlement_scope_type);
    setSettlementStoreIds(staff.settlement_store_ids);
    setEnabled(staff.enabled);
    platformStaffApi
      .grantHistory(staff.id)
      .then(setHistory)
      .catch(() => setHistory([]));
  }, [open, staff]);

  const summary = useMemo(() => {
    const scopeLabel = storeSetLabel(storeScopeType, storeIds, stores);
    const selectedBundles = bundles.filter(bundle => bundleIds.includes(bundle.id));
    return `${staff?.display_name ?? ''}さんは ${bundleSetLabel(selectedBundles)} として ${scopeLabel} のデータにアクセスできます`;
  }, [bundles, bundleIds, storeScopeType, storeIds, stores, staff]);

  const submit = async () => {
    if (!staff) return;
    if (bundleIds.length === 0) {
      toast.error('権限束を 1 つ以上選択してください');
      return;
    }
    setIsSubmitting(true);
    try {
      await platformStaffApi.update(staff.id, {
        bundle_ids: bundleIds,
        store_scope_type: storeScopeType,
        store_ids: storeIds,
        settlement_scope_type: settlementScopeType,
        settlement_store_ids: settlementStoreIds,
        enabled,
        // 楽観ロック用バージョン（応答の version をそのまま往復する）
        version: staff.version,
      });
      toast.success('権限を更新しました');
      onUpdated();
      onClose();
    } catch (error) {
      const status =
        error && typeof error === 'object' && 'response' in error
          ? (error as { response?: { status?: number } }).response?.status
          : undefined;
      if (status === 409) {
        // 楽観ロック競合: 固定文言の toast を出し、一覧を再取得してドロワーの内容を
        // 最新値へ自動リフレッシュする（staff prop の更新で useEffect が再初期化。ローカル編集は破棄、ドロワーは開いたまま）。
        toast.error('他の管理者が更新しました。最新の内容を確認してください');
        onUpdated();
      } else {
        toast.error(getApiErrorMessage(error, '権限の更新に失敗しました'));
      }
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
            <div>
              <span className="mb-1 block text-sm font-medium text-gray-700">状態</span>
              <div className="flex items-center gap-4">
                <label className="flex items-center gap-2 text-sm text-gray-700">
                  <input
                    type="radio"
                    name="staff-enabled"
                    checked={enabled}
                    onChange={() => setEnabled(true)}
                    className="border-gray-300 text-blue-600 focus:ring-blue-500"
                  />
                  有効
                </label>
                <label className="flex items-center gap-2 text-sm text-gray-700">
                  <input
                    type="radio"
                    name="staff-enabled"
                    checked={!enabled}
                    onChange={() => setEnabled(false)}
                    className="border-gray-300 text-blue-600 focus:ring-blue-500"
                  />
                  停止
                </label>
              </div>
              <p className="mt-1 text-xs text-gray-500">
                停止してもアカウントは削除されず、過去の操作記録は保持されます。
              </p>
            </div>
            <div>
              <p className="mb-1 text-sm font-medium text-gray-700">この設定の結果</p>
              <p className="rounded-md bg-blue-50 p-3 text-sm text-blue-800">{summary}</p>
            </div>
            <div>
              <p className="mb-1 text-sm font-medium text-gray-700">付与履歴</p>
              {history.length === 0 ? (
                <p className="text-sm text-gray-500">履歴はありません</p>
              ) : (
                <ul className="space-y-1 rounded-md border border-gray-200 p-3">
                  {history.map(entry => (
                    <li key={entry.id} className="text-xs text-gray-600">
                      <span className="font-medium text-gray-900">
                        {GRANT_ACTION_LABELS[entry.action] ?? entry.action}
                      </span>
                      {' — '}
                      {new Date(entry.created_at).toLocaleString('ja-JP')} / {entry.actor_email}
                    </li>
                  ))}
                </ul>
              )}
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
