'use client';

import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'react-hot-toast';
import {
  PlatformStoreScopeType,
  RoleSummaryResponse,
  platformRoleApi,
  platformStaffApi,
} from '@/entities/user';
import { getApiErrorMessage, useManagedList } from '@/shared/lib';
import { Button, Dialog, DialogContent, DialogTitle, Input, Label } from '@/shared/ui';
import { RolePicker } from './RolePicker';
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

/** スタッフの新規作成モーダル（メール・初期パスワード・氏名・ロール・担当店舗）。 */
export function StaffCreateModal({ open, onClose, onCreated }: StaffCreateModalProps) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { isSubmitting },
  } = useForm<StaffCreateFormValues>();
  const [roleIds, setRoleIds] = useState<number[]>([]);
  const [storeScopeType, setStoreScopeType] = useState<PlatformStoreScopeType>('ALL_STORES');
  const [storeIds, setStoreIds] = useState<number[]>([]);
  const { items: roles, isLoading: rolesLoading } = useManagedList<RoleSummaryResponse>(
    () => platformRoleApi.list(),
    'ロール一覧の取得に失敗しました'
  );

  useEffect(() => {
    if (!open) return;
    reset({ email: '', password: '', display_name: '' });
    setRoleIds([]);
    setStoreScopeType('ALL_STORES');
    setStoreIds([]);
  }, [open, reset]);

  const submit = async (values: StaffCreateFormValues) => {
    if (roleIds.length === 0) {
      toast.error('ロールを 1 つ以上選択してください');
      return;
    }
    try {
      await platformStaffApi.create({
        ...values,
        role_ids: roleIds,
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
    <Dialog
      open={open}
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
          スタッフを追加
        </DialogTitle>
        <form onSubmit={handleSubmit(submit)} className="space-y-4 px-6 py-5">
          <div className="grid gap-1">
            <Label htmlFor="staff-email">メールアドレス</Label>
            <Input
              id="staff-email"
              type="email"
              maxLength={127}
              {...register('email', { required: true })}
            />
          </div>
          <div className="grid gap-1">
            <Label htmlFor="staff-password">初期パスワード</Label>
            <Input
              id="staff-password"
              type="password"
              {...register('password', { required: true })}
            />
          </div>
          <div className="grid gap-1">
            <Label htmlFor="staff-display-name">氏名</Label>
            <Input
              id="staff-display-name"
              type="text"
              {...register('display_name', { required: true })}
            />
          </div>
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
          <div className="flex justify-end gap-3 border-t pt-4">
            <Button type="button" variant="outline" onClick={onClose}>
              キャンセル
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? '追加中...' : '追加する'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
