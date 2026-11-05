'use client';

import Link from 'next/link';
import { useId } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { staffApi, useStoreContext, type StoreScopeType } from '@/entities/user';
import {
  RolePicker,
  StoreSetPicker,
  roleSetLabel,
  storeSetLabel,
} from '@/features/staff-management';
import { EMAIL_PATTERN, EMAIL_PATTERN_MESSAGE, useManagedList } from '@/shared/lib';
import {
  Badge,
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
} from '@/shared/ui';

export interface StaffGrants {
  role_ids: number[];
  storeSet: { storeScopeType: StoreScopeType; storeIds: number[] };
}

export interface StaffFormValues extends StaffGrants {
  email: string;
  password: string;
  display_name: string;
}

interface StaffFormProps {
  /** 編集対象の氏名と現在の授権。省略は新規作成で、本人の欄（メール・初期パスワード・氏名）を出す。 */
  editing?: { displayName: string; grants: StaffGrants };
  /** 書き込み中・競合中など、保存を受け付けない間は true。 */
  locked?: boolean;
  /** 保存処理。失敗の通知は呼び出し側が行い、送信中の状態はこの関数の完了まで保たれる。 */
  onSubmit: (values: StaffFormValues) => Promise<void>;
}

/** 従業員の作成・授権編集に共通のフォーム（本人の欄 + ロール + 担当店舗 + この設定の結果）。 */
export function StaffForm({ editing, locked = false, onSubmit }: StaffFormProps) {
  const form = useForm<StaffFormValues>({
    defaultValues: {
      email: '',
      password: '',
      display_name: '',
      role_ids: editing?.grants.role_ids ?? [],
      storeSet: editing?.grants.storeSet ?? { storeScopeType: 'SPECIFIC_STORES', storeIds: [] },
    },
  });
  const {
    control,
    handleSubmit,
    formState: { isSubmitting },
  } = form;
  const resultId = useId();
  const displayName = useWatch({ control, name: 'display_name' });
  const roleIds = useWatch({ control, name: 'role_ids' });
  const storeSet = useWatch({ control, name: 'storeSet' });

  const {
    items: roles,
    isLoading: rolesLoading,
    failed: rolesFailed,
    refetch: refetchRoles,
  } = useManagedList(staffApi.grantableRoles);
  // 担当店舗の選択肢は行使者自身の授権店舗。付与できる範囲と同じ集合なので、
  // 選べたものはサーバの店舗部分集合検査も通る。
  const { stores, loadFailed: storesFailed, reload: reloadStores } = useStoreContext();
  const storeCatalog = stores ?? [];

  const selectedRoles = roles.filter(role => role.id !== undefined && roleIds.includes(role.id));
  // 複数のロールが同じ権限を持ちうるので、コードで 1 件にまとめる（初出の順）
  const grantedPermissions = [
    ...new Map(
      selectedRoles.flatMap(role => role.permissions).map(entry => [entry.code, entry])
    ).values(),
  ];
  const selectedStores =
    storeSet.storeScopeType === 'ALL_STORES'
      ? []
      : storeCatalog.filter(
          store => store.id !== undefined && storeSet.storeIds.includes(store.id)
        );
  const subject = editing
    ? `${editing.displayName}さん`
    : displayName.trim()
      ? `${displayName.trim()}さん`
      : '新しい従業員';

  return (
    <Form {...form}>
      {/* noValidate: 原生制約が先に止めると我々の文言が描かれない。執行は各 rules が担う */}
      <form onSubmit={event => void handleSubmit(onSubmit)(event)} noValidate className="space-y-6">
        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]">
          <div className="min-w-0 space-y-6">
            {!editing && (
              <Card>
                <CardHeader>
                  <CardTitle role="heading" aria-level={2}>
                    従業員
                  </CardTitle>
                </CardHeader>
                <CardContent className="grid gap-6 md:grid-cols-2">
                  <FormField
                    control={control}
                    name="display_name"
                    rules={{ required: '氏名を入力してください' }}
                    render={({ field }) => (
                      <FormItem className="gap-2">
                        <FormLabel>氏名</FormLabel>
                        <FormControl>
                          <Input type="text" required maxLength={150} {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={control}
                    name="email"
                    rules={{
                      required: 'メールアドレスを入力してください',
                      pattern: { value: EMAIL_PATTERN, message: EMAIL_PATTERN_MESSAGE },
                    }}
                    render={({ field }) => (
                      <FormItem className="gap-2">
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
                      <FormItem className="gap-2">
                        <FormLabel>初期パスワード</FormLabel>
                        <FormControl>
                          <Input required type="password" autoComplete="new-password" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </CardContent>
              </Card>
            )}
            <Card>
              <CardHeader>
                <CardTitle role="heading" aria-level={2}>
                  権限
                </CardTitle>
                <CardDescription>
                  委譲できるのは自分が持つロールと、自分の担当店舗の範囲だけです。
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-6">
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
                          stores={storeCatalog}
                          isLoading={stores === null && !storesFailed}
                          failed={storesFailed}
                          onReload={reloadStores}
                          {...field.value}
                          onChange={field.onChange}
                          ref={field.ref}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </CardContent>
            </Card>
          </div>

          <Card role="region" aria-labelledby={resultId} className="h-fit xl:sticky xl:top-6">
            <CardHeader>
              <CardTitle id={resultId} role="heading" aria-level={2}>
                この設定の結果
              </CardTitle>
              <CardDescription>
                保存前に、付与される権限と対象店舗を確かめてください。
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-5 text-sm">
              <p className="text-foreground break-words">
                {subject}は{' '}
                {rolesFailed ? `選択中の ${roleIds.length} ロール` : roleSetLabel(selectedRoles)}{' '}
                として {storeSetLabel(storeSet.storeScopeType, storeSet.storeIds, storeCatalog)}{' '}
                のデータにアクセスできます
              </p>
              <div className="space-y-2">
                <h3 className="font-medium text-foreground">
                  付与される権限（{grantedPermissions.length} 件）
                </h3>
                {/* 目録が読めない・未着のときは「未選択」と区別する。保存は止めない */}
                {rolesFailed ? (
                  <p className="text-muted-foreground">
                    ロール一覧を取得できないため、付与される権限を表示できません。
                  </p>
                ) : rolesLoading ? (
                  <p className="text-muted-foreground">読み込み中...</p>
                ) : grantedPermissions.length === 0 ? (
                  <p className="text-muted-foreground">ロールが選ばれていません。</p>
                ) : (
                  <ul className="flex flex-wrap gap-1.5">
                    {grantedPermissions.map(entry => (
                      <li key={entry.code}>
                        <Badge variant="outline" className="whitespace-normal break-words">
                          {entry.label ?? entry.code}
                        </Badge>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
              <div className="space-y-2">
                <h3 className="font-medium text-foreground">
                  対象店舗（
                  {storeSet.storeScopeType === 'ALL_STORES'
                    ? '全店舗'
                    : `${selectedStores.length} 店舗`}
                  ）
                </h3>
                {storeSet.storeScopeType === 'ALL_STORES' ? (
                  <p className="text-muted-foreground">今後追加される店舗も含みます。</p>
                ) : selectedStores.length === 0 ? (
                  <p className="text-muted-foreground">店舗が選ばれていません。</p>
                ) : (
                  <ul className="max-h-64 space-y-1 overflow-y-auto">
                    {selectedStores.map(store => (
                      <li key={store.id} className="break-words text-foreground">
                        {store.name ?? store.id}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </CardContent>
          </Card>
        </div>

        <div className="flex justify-end gap-3 border-t pt-4">
          <Button variant="outline" render={<Link href="/admin/staff" />}>
            キャンセル
          </Button>
          <Button type="submit" disabled={isSubmitting || locked}>
            {editing
              ? isSubmitting
                ? '保存中...'
                : '保存する'
              : isSubmitting
                ? '追加中...'
                : '追加する'}
          </Button>
        </div>
      </form>
    </Form>
  );
}
