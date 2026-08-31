'use client';

import { useForm, useWatch } from 'react-hook-form';
import { notify } from '@/shared/notify';
import {
  PlatformStore,
  PlatformStoreScopeType,
  RoleSummaryResponse,
  serviceIdentityApi,
} from '@/entities/user';
import { getApiErrorMessage, useManagedList } from '@/shared/lib';
import {
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
} from '@/shared/ui';
import { RolePicker } from './RolePicker';
import { StoreSetPicker } from './StoreSetPicker';

interface ServiceIdentityCreateModalProps {
  /** 店舗目録（一覧ページが取得済みのものを共有する）。 */
  stores: PlatformStore[];
  storesLoading: boolean;
  /** 店舗目録の取得に失敗した状態。 */
  storesFailed: boolean;
  /** 店舗目録の取り直し（取得失敗からの手動回復導線）。 */
  onReloadStores: () => void;
  onClose: () => void;
  /** 作成成功後に呼ばれる（一覧の再取得用）。 */
  onCreated: () => void;
}

/** 値はそのまま作成リクエストになるため、欄名は wire のキーに合わせる。 */
interface ServiceIdentityCreateFormValues {
  display_name: string;
  role_ids: number[];
  store_scope_type: PlatformStoreScopeType;
  store_ids: number[];
}

/**
 * サービスIDの新規作成モーダル（用途名・ロール・対象店舗。資格情報の欄は無い）。
 * ロールの選択肢は自作ロールに限るサーバの読み口から引き、既定ロールはそもそも並ばない。
 * 既定は個別店舗 — 全店舗（全件閲覧）は明示的に選んだときだけ送る。
 */
export function ServiceIdentityCreateModal({
  stores,
  storesLoading,
  storesFailed,
  onReloadStores,
  onClose,
  onCreated,
}: ServiceIdentityCreateModalProps) {
  const form = useForm<ServiceIdentityCreateFormValues>({
    defaultValues: {
      display_name: '',
      role_ids: [],
      store_scope_type: 'SPECIFIC_STORES',
      store_ids: [],
    },
  });
  const {
    control,
    handleSubmit,
    setValue,
    formState: { isSubmitting },
  } = form;
  // 店舗集合は検証を持たないが、値は他の欄と同じ場所に置く（送信も初期化も一箇所で済む）
  const storeScopeType = useWatch({ control, name: 'store_scope_type' });
  const storeIds = useWatch({ control, name: 'store_ids' });
  const {
    items: roles,
    isLoading: rolesLoading,
    failed: rolesFailed,
    refetch: refetchRoles,
  } = useManagedList<RoleSummaryResponse>(() => serviceIdentityApi.grantableRoles());

  const submit = async (values: ServiceIdentityCreateFormValues) => {
    if (values.store_scope_type === 'SPECIFIC_STORES' && values.store_ids.length === 0) {
      notify.error('対象店舗を 1 つ以上選択してください');
      return;
    }
    try {
      await serviceIdentityApi.create(values);
      notify.success('サービスIDを追加しました');
      onCreated();
      onClose();
    } catch (error) {
      notify.error(getApiErrorMessage(error, 'サービスIDの追加に失敗しました'));
    }
  };

  return (
    <Dialog
      open
      onOpenChange={next => {
        // 送信中は閉じさせない。閉じると unmount で isSubmitting が消え、開き直した複製から
        // 二重送信できるうえ、古い継続の onClose が複製のモーダルまで閉じてしまう
        if (!next && !isSubmitting) onClose();
      }}
    >
      <DialogContent
        showCloseButton={false}
        aria-describedby={undefined}
        className="max-h-[calc(100vh-2rem)] gap-0 overflow-y-auto rounded-[10px] p-0 sm:max-w-md"
      >
        <DialogTitle className="border-b px-6 py-4 text-lg font-semibold text-foreground">
          サービスIDを追加
        </DialogTitle>
        <Form {...form}>
          {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
              我々の文言は永久に描かれない。執行は下の各 rules が担う */}
          <form onSubmit={handleSubmit(submit)} noValidate className="space-y-4 px-6 py-5">
            <FormField
              control={control}
              name="display_name"
              rules={{ required: '用途名を入力してください' }}
              render={({ field }) => (
                <FormItem className="gap-1">
                  <FormLabel>用途名</FormLabel>
                  <FormControl>
                    <Input
                      type="text"
                      maxLength={150}
                      placeholder="例: 夜間ポイント失効バッチ"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
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
            <div className="flex justify-end gap-3 border-t pt-4">
              <Button type="button" variant="outline" onClick={onClose} disabled={isSubmitting}>
                キャンセル
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? '追加中...' : '追加する'}
              </Button>
            </div>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
