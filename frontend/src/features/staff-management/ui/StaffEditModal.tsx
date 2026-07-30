'use client';

import { useEffect, useMemo, useState } from 'react';
import { toast } from 'react-hot-toast';
import {
  PlatformStaffResponse,
  PlatformStore,
  PlatformStoreScopeType,
  RoleResponse,
  platformAuthApi,
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
  open: boolean;
  onClose: () => void;
  /** 編集対象。null なら何も表示しない。 */
  staff: PlatformStaffResponse | null;
  /** 更新成功後に呼ばれる（一覧の再取得用）。 */
  onUpdated: () => void;
}

/** スタッフの授権編集モーダル（ロール・店舗集合・停止/再開。「この設定の結果」要約付き）。 */
export function StaffEditModal({ open, onClose, staff, onUpdated }: StaffEditModalProps) {
  const [roleIds, setRoleIds] = useState<number[]>([]);
  const [storeScopeType, setStoreScopeType] = useState<PlatformStoreScopeType>('ALL_STORES');
  const [storeIds, setStoreIds] = useState<number[]>([]);
  const [enabled, setEnabled] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { items: stores } = useManagedList<PlatformStore>(
    () => platformAuthApi.stores(),
    '店舗一覧の取得に失敗しました'
  );
  const { items: roles, isLoading: rolesLoading } = useManagedList<RoleResponse>(
    () => platformRoleApi.list(),
    'ロール一覧の取得に失敗しました'
  );

  useEffect(() => {
    if (!open || !staff) return;
    setRoleIds((staff.roles ?? []).flatMap(role => (role.id === undefined ? [] : [role.id])));
    setStoreScopeType(staff.store_scope_type ?? 'ALL_STORES');
    setStoreIds(staff.store_ids ?? []);
    setEnabled(staff.enabled);
  }, [open, staff]);

  const summary = useMemo(() => {
    const scopeLabel = storeSetLabel(storeScopeType, storeIds, stores);
    const selectedRoles = roles.filter(role => role.id !== undefined && roleIds.includes(role.id));
    return `${staff?.display_name ?? ''}さんは ${roleSetLabel(selectedRoles)} として ${scopeLabel} のデータにアクセスできます`;
  }, [roles, roleIds, storeScopeType, storeIds, stores, staff]);

  const submit = async () => {
    if (!staff) return;
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
      open={open && staff !== null}
      onOpenChange={next => {
        if (!next) onClose();
      }}
    >
      <DialogContent
        showCloseButton={false}
        aria-describedby={undefined}
        className="max-h-[calc(100vh-2rem)] gap-0 overflow-y-auto rounded-[10px] p-0 sm:max-w-md"
      >
        <DialogTitle className="border-b px-6 py-4 text-lg font-semibold text-foreground">
          {staff?.display_name} の権限を編集
        </DialogTitle>
        <div className="space-y-4 px-6 py-5">
          <RolePicker
            roles={roles}
            isLoading={rolesLoading}
            roleIds={roleIds}
            onChange={setRoleIds}
          />
          <StoreSetPicker
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
            <Button type="button" variant="outline" onClick={onClose}>
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
