'use client';

import { useEffect, useMemo, useState } from 'react';
import { toast } from 'react-hot-toast';
import {
  PlatformStaffResponse,
  PlatformStore,
  PlatformStoreScopeType,
  RoleSummaryResponse,
  platformRoleApi,
  platformStaffApi,
} from '@/entities/user';
import { getApiErrorMessage, isConflict, useManagedList } from '@/shared/lib';
import { Button, Dialog, DialogContent, DialogTitle, Label } from '@/shared/ui';
import { roleSetLabel } from '../lib/roleSetLabel';
import { storeSetLabel } from '../lib/storeSetLabel';
import { RolePicker } from './RolePicker';
import { StoreSetPicker } from './StoreSetPicker';

interface StaffEditModalProps {
  /** 編集対象。409 の再取得で差し替わると、フォームは最新値で再初期化される。 */
  staff: PlatformStaffResponse;
  /** 店舗目録（一覧ページが取得済みのものを共有する）。 */
  stores: PlatformStore[];
  storesLoading: boolean;
  /** 店舗目録の取り直し（取得失敗からの手動回復導線）。 */
  onReloadStores: () => void;
  onClose: () => void;
  /** 更新成功後に呼ばれる（一覧の再取得用）。 */
  onUpdated: () => void;
}

/**
 * スタッフの授権編集モーダル（ロール・店舗集合・停止/再開。「この設定の結果」要約付き）。
 * 開いたときだけ mount される前提。ロール目録の取得は mount 時 = 開いた時点に遅延される。
 */
export function StaffEditModal({
  staff,
  stores,
  storesLoading,
  onReloadStores,
  onClose,
  onUpdated,
}: StaffEditModalProps) {
  const [roleIds, setRoleIds] = useState<number[]>([]);
  const [storeScopeType, setStoreScopeType] = useState<PlatformStoreScopeType>('ALL_STORES');
  const [storeIds, setStoreIds] = useState<number[]>([]);
  const [enabled, setEnabled] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { items: roles, isLoading: rolesLoading } = useManagedList<RoleSummaryResponse>(
    () => platformRoleApi.list(),
    'ロール一覧の取得に失敗しました'
  );

  useEffect(() => {
    setRoleIds((staff.roles ?? []).flatMap(role => (role.id === undefined ? [] : [role.id])));
    // 欠落時は全店舗ではなく個別店舗（storeIds 空 = どの店舗にも及ばない）へ倒す。
    // 既定を全店舗にすると、保存操作がそのまま作用域の拡大になる。
    setStoreScopeType(staff.store_scope_type ?? 'SPECIFIC_STORES');
    setStoreIds(staff.store_ids ?? []);
    setEnabled(staff.enabled);
  }, [staff]);

  const summary = useMemo(() => {
    const scopeLabel = storeSetLabel(storeScopeType, storeIds, stores);
    const selectedRoles = roles.filter(role => role.id !== undefined && roleIds.includes(role.id));
    return `${staff.display_name ?? ''}さんは ${roleSetLabel(selectedRoles)} として ${scopeLabel} のデータにアクセスできます`;
  }, [roles, roleIds, storeScopeType, storeIds, stores, staff]);

  const submit = async () => {
    if (roleIds.length === 0) {
      toast.error('ロールを 1 つ以上選択してください');
      return;
    }
    setIsSubmitting(true);
    try {
      await platformStaffApi.update(staff.id ?? 0, {
        role_ids: roleIds,
        store_scope_type: storeScopeType,
        store_ids: storeIds,
        enabled,
        // 楽観ロック用バージョン（応答の version をそのまま往復する）
        version: staff.version,
      });
      toast.success('権限を更新しました');
      onUpdated();
      onClose();
    } catch (error) {
      if (isConflict(error)) {
        // 楽観ロック競合: 固定文言の toast を出し、一覧を再取得してモーダルの内容を
        // 最新値へ自動リフレッシュする（staff prop の更新で useEffect が再初期化。ローカル編集は破棄、モーダルは開いたまま）。
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
    <Dialog
      open
      onOpenChange={next => {
        // 送信中は閉じさせない。閉じると unmount で isSubmitting が消え、開き直した複製から
        // 二重送信できるうえ、古い継続の onClose/onUpdated が複製のモーダルへ波及する
        if (!next && !isSubmitting) onClose();
      }}
    >
      <DialogContent
        showCloseButton={false}
        aria-describedby={undefined}
        className="max-h-[calc(100vh-2rem)] gap-0 overflow-y-auto rounded-[10px] p-0 sm:max-w-md"
      >
        <DialogTitle className="border-b px-6 py-4 text-lg font-semibold text-foreground">
          {staff.display_name} の権限を編集
        </DialogTitle>
        <div className="space-y-4 px-6 py-5">
          <RolePicker
            roles={roles}
            isLoading={rolesLoading}
            roleIds={roleIds}
            onChange={setRoleIds}
          />
          <StoreSetPicker
            stores={stores}
            isLoading={storesLoading}
            onReload={onReloadStores}
            storeScopeType={storeScopeType}
            storeIds={storeIds}
            onChange={next => {
              setStoreScopeType(next.storeScopeType);
              setStoreIds(next.storeIds);
            }}
          />
          <div>
            <span className="mb-1 block text-sm font-medium text-foreground">状態</span>
            <div className="flex items-center gap-4">
              <Label className="font-normal">
                <input
                  type="radio"
                  name="staff-enabled"
                  checked={enabled}
                  onChange={() => setEnabled(true)}
                />
                有効
              </Label>
              <Label className="font-normal">
                <input
                  type="radio"
                  name="staff-enabled"
                  checked={!enabled}
                  onChange={() => setEnabled(false)}
                />
                停止
              </Label>
            </div>
            <p className="mt-1 text-xs text-muted-foreground">
              停止してもアカウントは削除されず、過去の操作記録は保持されます。
            </p>
          </div>
          <div>
            <p className="mb-1 text-sm font-medium text-foreground">この設定の結果</p>
            <p className="rounded-md bg-primary/10 p-3 text-sm text-primary-strong">{summary}</p>
          </div>
          <div className="flex justify-end gap-3 border-t pt-4">
            <Button type="button" variant="outline" onClick={onClose} disabled={isSubmitting}>
              キャンセル
            </Button>
            <Button type="button" onClick={submit} disabled={isSubmitting}>
              {isSubmitting ? '保存中...' : '保存する'}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
