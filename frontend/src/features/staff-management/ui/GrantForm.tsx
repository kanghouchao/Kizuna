'use client';

import Link from 'next/link';
import { useId, type ReactNode } from 'react';
import { useForm, useWatch, type Control, type DefaultValues } from 'react-hook-form';
import {
  useStoreContext,
  type GrantableRoleResponse,
  type RoleSummaryResponse,
} from '@/entities/user';
import { useManagedList } from '@/shared/lib';
import {
  Badge,
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  Form,
  FormField,
} from '@/shared/ui';
import type { Grants } from '../lib/grants';
import { roleSetLabel } from '../lib/roleSetLabel';
import { EMPTY_STORE_SET_LABEL, storeSetLabel } from '../lib/storeSetLabel';
import { RolePicker } from './RolePicker';
import { StoreSetPicker } from './StoreSetPicker';

type GrantableRole = GrantableRoleResponse | RoleSummaryResponse;

export interface GrantFormValues extends Grants {
  display_name: string;
}

interface GrantFormProps<T extends GrantFormValues> {
  defaultValues: T;
  /** 編集対象の名前。省略は新規作成で、profile の欄を出し、入力中の名前を結果の主語にする。 */
  editingName?: string;
  /** 新規作成で出す本人の欄。欄は control で T の名前を引く。 */
  profile?: { title: string; fields: (control: Control<T>) => ReactNode };
  /** 主語の敬称（人は「さん」、サービスIDは無し）と、名前未入力の間の主語。 */
  honorific: string;
  unnamedSubject: string;
  /** 付与できるロールの目録。権限の列挙を持たない要約（サービスID）なら、結果はロールごとの件数で示す。 */
  loadRoles: () => Promise<GrantableRole[]>;
  rolesEmptyHint?: ReactNode;
  /** 権限の節の説明。付与できる範囲の規則は主体の種類ごとに異なる。 */
  grantDescription: string;
  storeLabel?: string;
  /** キャンセルの戻り先（一覧）。 */
  listHref: string;
  /** 書き込み中・競合中など、保存を受け付けない間は true。 */
  locked?: boolean;
  /** 保存処理。失敗の通知は呼び出し側が行い、送信中の状態はこの関数の完了まで保たれる。 */
  onSubmit: (values: T) => Promise<void>;
}

/** 授権の作成・編集に共通のフォーム（本人の欄 + ロール + 店舗集合 + この設定の結果）。 */
export function GrantForm<T extends GrantFormValues>({
  defaultValues,
  editingName,
  profile,
  honorific,
  unnamedSubject,
  loadRoles,
  rolesEmptyHint,
  grantDescription,
  storeLabel,
  listHref,
  locked = false,
  onSubmit,
}: GrantFormProps<T>) {
  const form = useForm<T>({ defaultValues: defaultValues as DefaultValues<T> });
  const {
    handleSubmit,
    formState: { isSubmitting },
  } = form;
  // 授権の欄は T の共通部分だけを引く。T を具体化した control は本人の欄へ渡す
  const control = form.control as unknown as Control<GrantFormValues>;
  const resultId = useId();
  const displayName = useWatch({ control, name: 'display_name' });
  const roleIds = useWatch({ control, name: 'role_ids' });
  const storeIds = useWatch({ control, name: 'store_ids' });

  const {
    items: roles,
    isLoading: rolesLoading,
    failed: rolesFailed,
    refetch: refetchRoles,
  } = useManagedList(loadRoles);
  // 店舗の選択肢は行使者自身の授権店舗。従業員への委譲で付与できる範囲と同じ集合なので、
  // 選べたものはサーバの店舗部分集合検査も通る。
  const { stores, loadFailed: storesFailed, reload: reloadStores } = useStoreContext();
  const storeCatalog = stores ?? [];

  const selectedRoles = roles.filter(role => role.id !== undefined && roleIds.includes(role.id));
  const listed = selectedRoles.filter(role => 'permissions' in role);
  const counted = selectedRoles.filter(role => 'permission_count' in role);
  // 複数のロールが同じ権限を持ちうるので、コードで 1 件にまとめる（初出の順）
  const grantedPermissions = [
    ...new Map(listed.flatMap(role => role.permissions).map(entry => [entry.code, entry])).values(),
  ];
  const selectedStores = storeCatalog.filter(
    store => store.id !== undefined && storeIds.includes(store.id)
  );
  // 目録が未着・取得失敗の間は名前を引けない。保持中の選択を「未選択」と述べず件数で示す
  const rolesUnavailable = rolesLoading || rolesFailed;
  const storesUnavailable = stores === null;
  const roleSummary =
    rolesUnavailable && roleIds.length > 0
      ? `選択中の ${roleIds.length} ロール`
      : roleSetLabel(selectedRoles);
  const storeSummary = storesUnavailable
    ? `選択中の ${storeIds.length} 店舗`
    : storeSetLabel(storeIds, storeCatalog);
  const name = editingName ?? displayName.trim();
  const subject = name ? `${name}${honorific}` : unnamedSubject;
  const editing = editingName !== undefined;

  return (
    <Form {...form}>
      {/* noValidate: 原生制約が先に止めると我々の文言が描かれない。執行は各 rules が担う */}
      <form onSubmit={event => void handleSubmit(onSubmit)(event)} noValidate className="space-y-6">
        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]">
          <div className="min-w-0 space-y-6">
            {!editing && profile && (
              <Card>
                <CardHeader>
                  <CardTitle role="heading" aria-level={2}>
                    {profile.title}
                  </CardTitle>
                </CardHeader>
                <CardContent className="grid gap-6 md:grid-cols-2">
                  {profile.fields(form.control)}
                </CardContent>
              </Card>
            )}
            <Card>
              <CardHeader>
                <CardTitle role="heading" aria-level={2}>
                  権限
                </CardTitle>
                <CardDescription>{grantDescription}</CardDescription>
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
                      emptyHint={rolesEmptyHint}
                      ref={field.ref}
                    />
                  )}
                />
                {/* 空の集合は「どの店舗も担当しない」で有効な授権のため、検証を置かない */}
                <FormField
                  control={control}
                  name="store_ids"
                  render={({ field }) => (
                    <StoreSetPicker
                      label={storeLabel}
                      stores={storeCatalog}
                      isLoading={stores === null && !storesFailed}
                      failed={storesFailed}
                      onReload={reloadStores}
                      storeIds={field.value}
                      onChange={field.onChange}
                    />
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
                {storeIds.length === 0
                  ? `${subject}は ${roleSummary} として、どの店舗のデータにもアクセスできません`
                  : `${subject}は ${roleSummary} として ${storeSummary} のデータにアクセスできます`}
              </p>
              <div className="space-y-2">
                <h3 className="font-medium text-foreground">
                  付与される権限
                  {!rolesUnavailable &&
                    counted.length === 0 &&
                    `（${grantedPermissions.length} 件）`}
                </h3>
                {/* 保存は止めない（取得失敗だけを理由に送信を止めない） */}
                {rolesFailed ? (
                  <p className="text-muted-foreground">
                    ロール一覧を取得できないため、付与される権限を表示できません。
                  </p>
                ) : rolesLoading ? (
                  <p className="text-muted-foreground">読み込み中...</p>
                ) : selectedRoles.length === 0 ? (
                  <p className="text-muted-foreground">ロールが選ばれていません。</p>
                ) : counted.length > 0 ? (
                  // 要約の目録は権限の件数しか持たず、ロール間の重なりを除けないため合計は出さない
                  <ul className="space-y-1">
                    {counted.map(role => (
                      <li key={role.id} className="break-words text-foreground">
                        {role.name}（{role.permission_count} 件）
                      </li>
                    ))}
                  </ul>
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
                  対象店舗（{storesUnavailable ? storeIds.length : selectedStores.length} 店舗）
                </h3>
                {storeIds.length === 0 ? (
                  <p className="text-muted-foreground">{EMPTY_STORE_SET_LABEL}</p>
                ) : storesUnavailable && storesFailed ? (
                  <p className="text-muted-foreground">
                    店舗一覧を取得できないため、選択中の店舗を表示できません。
                  </p>
                ) : storesUnavailable ? (
                  <p className="text-muted-foreground">読み込み中...</p>
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
          <Button variant="outline" render={<Link href={listHref} />}>
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
