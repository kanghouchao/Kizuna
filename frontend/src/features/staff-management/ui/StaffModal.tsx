'use client';

import { useRef } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { notify } from '@/shared/notify';
import type {
  PlatformStore,
  PlatformStoreScopeType,
  RoleSummaryResponse,
  PlatformStaffCreateRequest,
  PlatformStaffUpdateRequest,
  ServiceIdentityCreateRequest,
  ServiceIdentityResponse,
  StoreStaffUpdateRequest,
} from '@/entities/user';
import {
  EMAIL_PATTERN,
  EMAIL_PATTERN_MESSAGE,
  getApiErrorMessage,
  isConflict,
  useManagedList,
  useResourceInitialization,
  type KeyedResource,
} from '@/shared/lib';
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
  Label,
  RegionError,
} from '@/shared/ui';
import { RolePicker } from './RolePicker';
import { StoreSetPicker } from './StoreSetPicker';
import { roleSetLabel } from '../lib/roleSetLabel';
import { storeSetLabel } from '../lib/storeSetLabel';

export interface StaffModalProps {
  stores: PlatformStore[];
  storesLoading: boolean;
  storesFailed: boolean;
  onReloadStores: () => void;
  onClose: () => void;
}
export interface StaffCreateModalProps extends StaffModalProps {
  onCreated: () => void;
}
export interface StaffEditModalProps<T> extends StaffModalProps {
  resource: KeyedResource<T>;
  onUpdated: () => void;
}

type CreateOptions = {
  mode: 'create';
  title: string;
  initialScope: PlatformStoreScopeType;
  displayName: { label: string; required: string; placeholder?: string };
  successMessage: string;
  failureMessage: string;
  onCreated: () => void;
} & (
  | { identity: 'human'; create: (values: PlatformStaffCreateRequest) => Promise<unknown> }
  | {
      identity: 'service';
      create: (
        values: ServiceIdentityCreateRequest & { email?: never; password?: never }
      ) => Promise<unknown>;
    }
);
type EditOptions<T> = {
  mode: 'edit';
  resource: KeyedResource<T>;
  honorific: string;
  onUpdated: () => void;
} & (
  | {
      editStatus: false;
      update: (
        id: number,
        values: PlatformStaffUpdateRequest & { enabled?: never }
      ) => Promise<unknown>;
    }
  | {
      editStatus: true;
      update: (
        id: number,
        values: StoreStaffUpdateRequest & { enabled: boolean }
      ) => Promise<unknown>;
    }
);
type Props<T> = StaffModalProps & {
  loadRoles: () => Promise<RoleSummaryResponse[]>;
  storeLabel?: string;
} & (CreateOptions | EditOptions<T>);

interface FormValues {
  email: string;
  password: string;
  display_name: string;
  role_ids: number[];
  storeSet: { storeScopeType: PlatformStoreScopeType; storeIds: number[] };
  enabled: boolean;
}

export function StaffModal<T extends ServiceIdentityResponse>(props: Props<T>) {
  const { stores, storesLoading, storesFailed, onReloadStores, onClose } = props;
  const resource = props.mode === 'edit' ? props.resource : null;
  const staff = resource?.data;
  const form = useForm<FormValues>({
    defaultValues: {
      email: '',
      password: '',
      display_name: '',
      role_ids: [],
      enabled: false,
      storeSet: {
        storeScopeType: props.mode === 'create' ? props.initialScope : 'SPECIFIC_STORES',
        storeIds: [],
      },
    },
  });
  const {
    control,
    handleSubmit,
    reset,
    setValue,
    formState: { isSubmitting },
  } = form;
  // 検証の非同期完了が重なっても、一度に一つの保存だけを進める。
  const pending = useRef(false);
  const roleIds = useWatch({ control, name: 'role_ids' });
  const storeSet = useWatch({ control, name: 'storeSet' });
  const enabled = useWatch({ control, name: 'enabled' });
  const {
    items: roles,
    isLoading: rolesLoading,
    failed: rolesFailed,
    refetch: refetchRoles,
  } = useManagedList(props.loadRoles);
  const initialized = useResourceInitialization(resource?.success ?? null, value =>
    reset({
      email: '',
      password: '',
      display_name: '',
      role_ids: (value.roles ?? []).flatMap(role => (role.id === undefined ? [] : [role.id])),
      // 範囲の欠落を全店舗にすると保存だけで授与範囲が拡大するため、個別店舗へ倒す。
      storeSet: {
        storeScopeType: value.store_scope_type ?? 'SPECIFIC_STORES',
        storeIds: value.store_ids ?? [],
      },
      enabled: value.enabled,
    })
  );
  const ready =
    props.mode === 'create' ||
    (initialized &&
      resource !== null &&
      !resource.isLoading &&
      resource.failure === null &&
      staff != null);
  const summary =
    props.mode === 'edit'
      ? `${staff?.display_name ?? ''}${props.honorific}は ${roleSetLabel(roles.filter(role => role.id !== undefined && roleIds.includes(role.id)))} として ${storeSetLabel(storeSet.storeScopeType, storeSet.storeIds, stores)} のデータにアクセスできます`
      : '';
  const submit = async (values: FormValues) => {
    if (!ready || pending.current) return;
    pending.current = true;
    const operation = resource?.capture();
    const grants = {
      role_ids: values.role_ids,
      store_scope_type: values.storeSet.storeScopeType,
      store_ids: values.storeSet.storeScopeType === 'ALL_STORES' ? [] : values.storeSet.storeIds,
    };
    try {
      if (props.mode === 'create') {
        const identity = { ...grants, display_name: values.display_name };
        if (props.identity === 'human')
          await props.create({ ...identity, email: values.email, password: values.password });
        else await props.create(identity);
        notify.success(props.successMessage);
        props.onCreated();
      } else {
        if (staff == null) return;
        const update = { ...grants, version: staff.version };
        if (props.editStatus)
          await props.update(staff.id ?? 0, { ...update, enabled: values.enabled });
        else await props.update(staff.id ?? 0, update);
        if (!operation?.isCurrent()) return;
        notify.success('権限を更新しました');
        props.onUpdated();
      }
      onClose();
    } catch (error) {
      if (props.mode === 'edit') {
        if (!operation?.isCurrent()) return;
        if (isConflict(error)) {
          props.onUpdated();
          const result = await props.resource.reload();
          if (result.status === 'success' && result.isCurrent())
            notify.warning('他の担当者が更新しました。入力を最新の内容に置き換えました');
          return;
        }
      }
      notify.error(
        getApiErrorMessage(
          error,
          props.mode === 'create' ? props.failureMessage : '権限の更新に失敗しました'
        )
      );
    } finally {
      pending.current = false;
    }
  };
  const close = () => {
    if (!pending.current && !isSubmitting) onClose();
  };
  return (
    <Dialog
      open
      onOpenChange={next => {
        if (!next) close();
      }}
    >
      <DialogContent
        showCloseButton={false}
        aria-describedby={undefined}
        className="max-h-[calc(100vh-2rem)] gap-0 overflow-y-auto rounded-[10px] p-0 sm:max-w-md"
      >
        <DialogTitle className="border-b px-6 py-4 text-lg font-semibold text-foreground break-words">
          {props.mode === 'create' ? props.title : `${staff?.display_name ?? ''} の権限を編集`}
        </DialogTitle>
        {!ready && resource ? (
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
            <Button type="button" variant="outline" onClick={close} disabled={isSubmitting}>
              閉じる
            </Button>
          </div>
        ) : (
          <Form {...form}>
            <form
              onSubmit={event => {
                void handleSubmit(submit)(event);
              }}
              noValidate
              className="space-y-4 px-6 py-5"
            >
              {props.mode === 'create' && (
                <>
                  {props.identity === 'human' && (
                    <>
                      <FormField
                        control={control}
                        name="email"
                        rules={{
                          required: 'メールアドレスを入力してください',
                          pattern: { value: EMAIL_PATTERN, message: EMAIL_PATTERN_MESSAGE },
                        }}
                        render={({ field }) => (
                          <FormItem className="gap-1">
                            <FormLabel>メールアドレス</FormLabel>
                            <FormControl>
                              <Input required type="email" maxLength={127} {...field} />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                      <FormField
                        control={control}
                        name="password"
                        rules={{ required: '初期パスワードを入力してください' }}
                        render={({ field }) => (
                          <FormItem className="gap-1">
                            <FormLabel>初期パスワード</FormLabel>
                            <FormControl>
                              <Input required type="password" {...field} />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </>
                  )}
                  <FormField
                    control={control}
                    name="display_name"
                    rules={{ required: props.displayName.required }}
                    render={({ field }) => (
                      <FormItem className="gap-1">
                        <FormLabel>{props.displayName.label}</FormLabel>
                        <FormControl>
                          <Input
                            type="text"
                            required
                            maxLength={150}
                            placeholder={props.displayName.placeholder}
                            {...field}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </>
              )}
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

              <FormField
                control={control}
                name="storeSet"
                rules={{
                  validate: value =>
                    value.storeScopeType === 'ALL_STORES' ||
                    value.storeIds.length > 0 ||
                    '対象店舗を 1 つ以上選択してください',
                }}
                render={({ field }) => (
                  <FormItem className="gap-1">
                    <FormControl>
                      <StoreSetPicker
                        label={props.storeLabel}
                        stores={stores}
                        isLoading={storesLoading}
                        failed={storesFailed}
                        onReload={onReloadStores}
                        {...field.value}
                        onChange={field.onChange}
                        ref={field.ref}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              {props.mode === 'edit' && props.editStatus && (
                <>
                  <div>
                    <span className="mb-1 block text-sm font-medium text-foreground">状態</span>
                    <div className="flex items-center gap-4">
                      <Label className="font-normal">
                        <input
                          className="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                          type="radio"
                          name="store-staff-enabled"
                          checked={enabled}
                          onChange={() => setValue('enabled', true)}
                        />
                        有効
                      </Label>
                      <Label className="font-normal">
                        <input
                          className="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                          type="radio"
                          name="store-staff-enabled"
                          checked={!enabled}
                          onChange={() => setValue('enabled', false)}
                        />
                        停止
                      </Label>
                    </div>
                    <p className="mt-1 text-xs text-muted-foreground">
                      停止してもアカウントは削除されず、過去の操作記録は保持されます。
                    </p>
                  </div>
                </>
              )}
              {props.mode === 'edit' && (
                <div>
                  <p className="mb-1 text-sm font-medium text-foreground">この設定の結果</p>
                  <p className="rounded-md bg-primary/10 p-3 text-sm text-primary-strong break-words">
                    {summary}
                  </p>
                </div>
              )}
              <div className="flex justify-end gap-3 border-t pt-4">
                <Button type="button" variant="outline" onClick={close} disabled={isSubmitting}>
                  キャンセル
                </Button>
                <Button type="submit" disabled={isSubmitting}>
                  {props.mode === 'create'
                    ? isSubmitting
                      ? '追加中...'
                      : '追加する'
                    : isSubmitting
                      ? '保存中...'
                      : '保存する'}
                </Button>
              </div>
            </form>
          </Form>
        )}
      </DialogContent>
    </Dialog>
  );
}
