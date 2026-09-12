'use client';

import { useMemo } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { notify } from '@/shared/notify';
import {
  PlatformStore,
  PlatformStoreScopeType,
  RoleSummaryResponse,
  ServiceIdentityResponse,
  serviceIdentityApi,
} from '@/entities/user';
import {
  getApiErrorMessage,
  isConflict,
  useManagedList,
  useResourceInitialization,
  KeyedResource,
} from '@/shared/lib';
import {
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  Form,
  FormField,
  RegionError,
} from '@/shared/ui';
import { roleSetLabel } from '../lib/roleSetLabel';
import { storeSetLabel } from '../lib/storeSetLabel';
import { RolePicker } from './RolePicker';
import { StoreSetPicker } from './StoreSetPicker';

interface ServiceIdentityEditModalProps {
  /** 編集対象。409 の再取得で差し替わると、フォームは最新値で再初期化される。 */
  resource: KeyedResource<ServiceIdentityResponse>;
  /** 店舗目録（一覧ページが取得済みのものを共有する）。 */
  stores: PlatformStore[];
  storesLoading: boolean;
  /** 店舗目録の取得に失敗した状態。 */
  storesFailed: boolean;
  /** 店舗目録の取り直し（取得失敗からの手動回復導線）。 */
  onReloadStores: () => void;
  onClose: () => void;
  /** 更新成功後に呼ばれる（一覧の再取得用）。 */
  onUpdated: () => void;
}

/** 値はそのまま更新リクエストになるため（version を除く）、欄名は wire のキーに合わせる。 */
interface ServiceIdentityEditFormValues {
  role_ids: number[];
  store_scope_type: PlatformStoreScopeType;
  store_ids: number[];
}

/** 対象サービスIDからフォームの初期値を作る。prop が差し替わるたびにこれで組み直す。 */
function toFormValues(identity: ServiceIdentityResponse): ServiceIdentityEditFormValues {
  return {
    role_ids: (identity.roles ?? []).flatMap(role => (role.id === undefined ? [] : [role.id])),
    // 欠落時は全店舗ではなく個別店舗（storeIds 空 = どの店舗にも及ばない）へ倒す。
    // 既定を全店舗にすると、保存操作がそのまま作用域の拡大になる。
    store_scope_type: identity.store_scope_type ?? 'SPECIFIC_STORES',
    store_ids: identity.store_ids ?? [],
  };
}

/**
 * サービスIDの授権編集モーダル（ロール・対象店舗。「この設定の結果」要約付き）。
 * 停止・再開は一覧の操作列が担うので、ここには状態を動かす欄が無い。
 */
export function ServiceIdentityEditModal({
  resource,
  stores,
  storesLoading,
  storesFailed,
  onReloadStores,
  onClose,
  onUpdated,
}: ServiceIdentityEditModalProps) {
  const identity = resource.data;
  const form = useForm<ServiceIdentityEditFormValues>({
    defaultValues: { role_ids: [], store_scope_type: 'SPECIFIC_STORES', store_ids: [] },
  });
  const {
    control,
    handleSubmit,
    reset,
    setValue,
    formState: { isSubmitting },
  } = form;
  const roleIds = useWatch({ control, name: 'role_ids' });
  const storeScopeType = useWatch({ control, name: 'store_scope_type' });
  const storeIds = useWatch({ control, name: 'store_ids' });
  const {
    items: roles,
    isLoading: rolesLoading,
    failed: rolesFailed,
    refetch: refetchRoles,
  } = useManagedList<RoleSummaryResponse>(() => serviceIdentityApi.grantableRoles());

  const initialized = useResourceInitialization(resource.success, value =>
    reset(toFormValues(value))
  );
  const ready = initialized && !resource.isLoading && identity !== null;

  const summary = useMemo(() => {
    const scopeLabel = storeSetLabel(storeScopeType, storeIds, stores);
    const selectedRoles = roles.filter(role => role.id !== undefined && roleIds.includes(role.id));
    return `${identity?.display_name ?? ''} は ${roleSetLabel(selectedRoles)} として ${scopeLabel} のデータにアクセスできます`;
  }, [roles, roleIds, storeScopeType, storeIds, stores, identity]);

  const submit = async (values: ServiceIdentityEditFormValues) => {
    if (!ready || identity === null) return;
    const operation = resource.capture();
    if (values.store_scope_type === 'SPECIFIC_STORES' && values.store_ids.length === 0) {
      notify.error('対象店舗を 1 つ以上選択してください');
      return;
    }
    try {
      await serviceIdentityApi.update(identity.id ?? 0, {
        ...values,
        // 楽観ロック用バージョン（応答の version をそのまま往復する）
        version: identity.version,
      });
      if (!operation.isCurrent()) return;
      notify.success('権限を更新しました');
      onUpdated();
      onClose();
    } catch (error) {
      if (!operation.isCurrent()) return;
      if (isConflict(error)) {
        const result = await resource.reload();
        if (result.status === 'success' && result.isCurrent()) {
          notify.warning('他の担当者が更新しました。入力を最新の内容に置き換えました');
          onUpdated();
        }
      } else {
        notify.error(getApiErrorMessage(error, '権限の更新に失敗しました'));
      }
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
          {identity?.display_name} の権限を編集
        </DialogTitle>
        {!ready ? (
          <div className="space-y-4 px-6 py-5">
            {resource.failure === 'notFound' ? (
              <p role="alert" className="text-sm text-destructive-strong">
                この対象は見つかりませんでした。
              </p>
            ) : resource.failure === 'error' ? (
              <RegionError
                message="詳細を取得できませんでした。"
                onRetry={() => void resource.reload()}
              />
            ) : (
              <p className="text-sm text-muted-foreground">読み込み中...</p>
            )}
            <Button type="button" variant="outline" onClick={onClose}>
              閉じる
            </Button>
          </div>
        ) : (
          <Form {...form}>
            {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
              我々の文言は永久に描かれない。執行は下の rules が担う */}
            <form onSubmit={handleSubmit(submit)} noValidate className="space-y-4 px-6 py-5">
              <FormField
                control={control}
                name="role_ids"
                rules={{
                  validate: value => value.length > 0 || 'ロールを 1 つ以上選択してください',
                }}
                render={({ field }) => (
                  <RolePicker
                    roles={roles}
                    isLoading={rolesLoading}
                    failed={rolesFailed}
                    onReload={() => void refetchRoles()}
                    roleIds={field.value}
                    onChange={field.onChange}
                    ref={field.ref}
                  />
                )}
              />
              <StoreSetPicker
                label="対象店舗"
                stores={stores}
                isLoading={storesLoading}
                failed={storesFailed}
                onReload={onReloadStores}
                storeScopeType={storeScopeType}
                storeIds={storeIds}
                onChange={next => {
                  setValue('store_scope_type', next.storeScopeType);
                  setValue('store_ids', next.storeIds);
                }}
              />
              <div>
                <p className="mb-1 text-sm font-medium text-foreground">この設定の結果</p>
                <p className="rounded-md bg-primary/10 p-3 text-sm text-primary-strong">
                  {summary}
                </p>
              </div>
              <div className="flex justify-end gap-3 border-t pt-4">
                <Button type="button" variant="outline" onClick={onClose} disabled={isSubmitting}>
                  キャンセル
                </Button>
                <Button type="submit" disabled={isSubmitting}>
                  {isSubmitting ? '保存中...' : '保存する'}
                </Button>
              </div>
            </form>
          </Form>
        )}
      </DialogContent>
    </Dialog>
  );
}
