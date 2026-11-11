'use client';

import Link from 'next/link';
import { ReactNode, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useForm, useWatch } from 'react-hook-form';
import { CheckIcon, InfoIcon } from 'lucide-react';
import { customerApi, MergePreferences, MergePreview, MergeProfile } from '@/entities/customer';
import { MePending, hasPermission, useCurrentStoreId, useMe } from '@/entities/user';
import { getApiErrorMessage, isNotFound, storePath, useResource } from '@/shared/lib';
import { notify } from '@/shared/notify';
import {
  Badge,
  Button,
  Checkbox,
  ConfirmDialog,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  KeyValueList,
  KeyValueRow,
  Label,
  RegionError,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Textarea,
} from '@/shared/ui';
import { MergeSnapshotView, profileFields } from './MergeSnapshotView';

const listHref = (storeId: string) => storePath(storeId, '/customers');
const fallback = (storeId: string) => ({ href: listHref(storeId), label: '顧客一覧へ' });
const CONTACT_TYPES = ['PHONE', 'EMAIL', 'LINE'] as const;
const CONTACT_LABELS = { PHONE: '電話', EMAIL: 'メール', LINE: 'LINE' };
const PREFERENCE_KEYS = { PHONE: 'phone', EMAIL: 'email', LINE: 'line' } as const;
/** 統合理由の上限。統合要求の operation_reason の @Size(max = 500) と同値。 */
const REASON_MAX_LENGTH = 500;
const SECTION = 'space-y-4 rounded-xl border bg-card p-6 text-card-foreground shadow-sm';

/** 顧客の統合画面。どちらが残るかは URL の surviving / merged が名指す。 */
export default function CustomerMergePage() {
  const storeId = useCurrentStoreId();
  const params = useSearchParams();
  const survivingId = params.get('surviving') ?? '';
  const mergedId = params.get('merged') ?? '';
  const { me } = useMe();
  let body: ReactNode;
  if (me === null) body = <MePending />;
  else if (!hasPermission(me, 'CUSTOMER_MANAGE') || !hasPermission(me, 'CUSTOMER_MERGE'))
    body = <p role="alert">顧客の管理と統合の権限が必要です。</p>;
  else if (!survivingId || !mergedId || survivingId === mergedId)
    body = (
      <RegionError
        message="統合する 2 件の顧客を一覧から選び直してください。"
        fallback={fallback(storeId)}
      />
    );
  else
    body = (
      <MergeLoader
        key={`${survivingId}:${mergedId}`}
        survivingId={survivingId}
        mergedId={mergedId}
      />
    );
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-foreground">顧客の統合</h1>
      {body}
    </div>
  );
}

interface Target {
  survivingId: string;
  mergedId: string;
}

function MergeLoader({ survivingId, mergedId }: Target) {
  const resource = useResource(
    () => customerApi.mergePreview(survivingId, { merged_customer_id: mergedId }),
    [survivingId, mergedId]
  );
  if (resource.isLoading) return <p className="text-sm text-muted-foreground">読み込み中...</p>;
  if (resource.failure === 'notFound') return <MissingCustomer />;
  if (resource.failure !== null)
    return (
      <RegionError
        message="プレビューを取得できません。双方に会員関連がある場合は、先に理由付きで解除してください。"
        onRetry={() => void resource.reload()}
      />
    );
  return (
    resource.data && (
      <MergeEditor survivingId={survivingId} mergedId={mergedId} initial={resource.data} />
    )
  );
}

function MissingCustomer() {
  const storeId = useCurrentStoreId();
  return (
    <RegionError
      message="顧客が見つかりません。すでに統合されている可能性があります。"
      fallback={fallback(storeId)}
    />
  );
}

interface Values {
  profile: MergeProfile;
  preferences: MergePreferences;
  reason: string;
}

function MergeEditor({ initial, survivingId, mergedId }: Target & { initial: MergePreview }) {
  const storeId = useCurrentStoreId();
  const router = useRouter();
  const [data, setData] = useState<MergePreview | null>(initial);
  const [gone, setGone] = useState(false);
  const [preview, setPreview] = useState<MergePreview | null>(null);
  const initialPreferences = { ...initial.preferred_contacts };
  for (const type of initial.preference_conflicts) {
    initialPreferences[PREFERENCE_KEYS[type]] = 'unresolved';
  }
  const [acknowledged, setAcknowledged] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const form = useForm<Values>({
    defaultValues: {
      profile: initial.profile,
      preferences: initialPreferences,
      reason: '',
    },
  });
  const { register, control, getValues, setValue, handleSubmit } = form;
  const adopted = useWatch({ control, name: 'profile' });
  // 資料を変えたら、影響の確認からやり直させる
  const invalidate = () => {
    setPreview(null);
    setAcknowledged(false);
  };
  const fail = (error: unknown, message: string) => {
    setData(null);
    if (isNotFound(error)) setGone(true);
    else setFailure(getApiErrorMessage(error, message));
  };
  const refresh = async () => {
    setBusy(true);
    invalidate();
    setFailure(null);
    try {
      const latest = await customerApi.mergePreview(survivingId, { merged_customer_id: mergedId });
      setData(latest);
      const preferences = getValues('preferences');
      const contacts = [...latest.surviving.contacts, ...latest.merged.contacts];
      const invalid = CONTACT_TYPES.filter(type => {
        const id = preferences[PREFERENCE_KEYS[type]];
        return id !== null && !contacts.some(c => c.id === id && c.type === type && !c.deleted);
      });
      for (const type of invalid) {
        setValue(`preferences.${PREFERENCE_KEYS[type]}`, 'unresolved', { shouldValidate: true });
      }
    } catch (error) {
      fail(error, '最新資料の取得に失敗しました');
    } finally {
      setBusy(false);
    }
  };
  const review = handleSubmit(async values => {
    setBusy(true);
    invalidate();
    setFailure(null);
    try {
      const result = await customerApi.mergePreview(survivingId, {
        merged_customer_id: mergedId,
        profile: values.profile,
        preferred_contacts: values.preferences,
      });
      setData(result);
      setPreview(result);
    } catch (error) {
      fail(error, 'プレビューの確認に失敗しました');
    } finally {
      setBusy(false);
    }
  });
  const merge = handleSubmit(async values => {
    if (!preview?.preview_token || !acknowledged || busy) return;
    setBusy(true);
    try {
      await customerApi.merge(survivingId, {
        merged_customer_id: mergedId,
        profile: values.profile,
        preferred_contacts: values.preferences,
        preview_token: preview.preview_token,
        warnings_acknowledged: acknowledged,
        operation_reason: values.reason.trim(),
      });
      notify.success('顧客を統合しました');
      // 統合した組へ戻るボタンで戻らせない。被統合側はもう存在しない
      router.replace(listHref(storeId));
    } catch (error) {
      invalidate();
      fail(error, '顧客の統合に失敗しました');
      setBusy(false);
    }
  });
  if (gone) return <MissingCustomer />;
  const sides =
    data &&
    ([
      ['存続側', data.surviving],
      ['被統合側', data.merged],
    ] as const);
  return (
    <>
      <div className="flex items-start gap-3 rounded-lg border p-4 text-sm text-foreground">
        <InfoIcon className="mt-0.5 size-4 shrink-0" />
        <p>
          この画面で選んだ内容は、最下部の「統合する」を確定するまで保存されません。途中で画面を離れても、何も確定しません。
        </p>
      </div>

      {data && (
        <section className="space-y-3">
          <h2 className="text-lg font-semibold text-foreground">統合する 2 件</h2>
          <div className="grid gap-6 md:grid-cols-2">
            <MergeSnapshotView title="存続側の原資料" snapshot={data.surviving} />
            <MergeSnapshotView title="被統合側の原資料" snapshot={data.merged} />
          </div>
        </section>
      )}

      <Form {...form}>
        <form onSubmit={review} className="space-y-6">
          <fieldset disabled={busy} className="min-w-0 space-y-6">
            <section className={SECTION}>
              <div>
                <h2 className="font-semibold text-foreground">統合後の資料</h2>
                <p className="mt-1 text-sm text-muted-foreground">
                  項目ごとに、どちらの値を採るかを選ぶか、直接書き換えてください。
                </p>
              </div>
              <div className="divide-y">
                {profileFields.map(([key, label, max]) => (
                  <div key={key} className="space-y-3 py-5 first:pt-0">
                    <h3 className="text-sm font-medium text-foreground">{label}</h3>
                    {sides && (
                      <div className="grid gap-3 md:grid-cols-2">
                        {sides.map(([side, snapshot]) => {
                          const value = snapshot.profile[key];
                          return (
                            <div
                              key={side}
                              className="flex min-w-0 flex-col gap-2 rounded-md border p-3"
                            >
                              <div className="flex items-center justify-between gap-2">
                                <span className="text-xs text-muted-foreground">{side}</span>
                                {adopted[key] === value ? (
                                  <Badge
                                    variant="outline"
                                    className="border-transparent bg-primary/10 text-primary-strong"
                                  >
                                    <CheckIcon />
                                    採用中
                                  </Badge>
                                ) : (
                                  <Button
                                    type="button"
                                    size="xs"
                                    variant="outline"
                                    aria-label={`${side}の${label}を採用`}
                                    onClick={() => {
                                      setValue(`profile.${key}`, value);
                                      invalidate();
                                    }}
                                  >
                                    採用
                                  </Button>
                                )}
                              </div>
                              <p
                                className={
                                  value === null
                                    ? 'text-sm text-muted-foreground'
                                    : 'text-sm break-words whitespace-pre-wrap text-foreground'
                                }
                              >
                                {value ?? '未設定'}
                              </p>
                            </div>
                          );
                        })}
                      </div>
                    )}
                    <div className="space-y-2">
                      <Label htmlFor={`merge-${key}`}>統合後の{label}</Label>
                      <Textarea
                        id={`merge-${key}`}
                        maxLength={max}
                        {...register(`profile.${key}`, {
                          onChange: invalidate,
                          setValueAs: value => (value === '' ? null : value),
                        })}
                      />
                    </div>
                  </div>
                ))}
                <div className="py-5 last:pb-0">
                  <FormField
                    name="profile.has_pet"
                    control={control}
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>ペットの有無</FormLabel>
                        <Select
                          items={{ unknown: '未確認', true: 'あり', false: 'なし' }}
                          value={field.value === null ? 'unknown' : String(field.value)}
                          onValueChange={v => {
                            field.onChange(v === 'unknown' ? null : v === 'true');
                            invalidate();
                          }}
                        >
                          <FormControl>
                            <SelectTrigger ref={field.ref} onBlur={field.onBlur}>
                              <SelectValue className="min-w-0 flex-1 text-left" />
                            </SelectTrigger>
                          </FormControl>
                          <SelectContent>
                            <SelectItem value="unknown">未確認</SelectItem>
                            <SelectItem value="true">あり</SelectItem>
                            <SelectItem value="false">なし</SelectItem>
                          </SelectContent>
                        </Select>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
              </div>
            </section>

            <section className={SECTION}>
              <div>
                <h2 className="font-semibold text-foreground">優先連絡先と理由</h2>
                <p className="mt-1 text-sm text-muted-foreground">
                  連絡先は両方とも存続側へ移ります。種類ごとに優先を 1 件選びます。
                </p>
              </div>
              {data &&
                CONTACT_TYPES.map(type => {
                  const key = PREFERENCE_KEYS[type];
                  const items = {
                    unresolved: '一件または指定なしを選択',
                    none: '指定なし',
                    ...Object.fromEntries(
                      [...data.surviving.contacts, ...data.merged.contacts]
                        .filter(c => c.type === type && !c.deleted)
                        .map(c => [
                          c.id,
                          `${c.value}（ID: ${c.id} / 由来: ${c.origin_customer_id}）`,
                        ])
                    ),
                  };
                  return (
                    <FormField
                      key={type}
                      name={`preferences.${key}`}
                      control={control}
                      rules={{
                        validate: value =>
                          value !== 'unresolved' ||
                          '優先連絡先を一件、または指定なしを選択してください',
                      }}
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>{CONTACT_LABELS[type]}の優先連絡先</FormLabel>
                          <Select
                            items={items}
                            value={field.value ?? 'none'}
                            onValueChange={v => {
                              field.onChange(v === 'none' ? null : v);
                              invalidate();
                            }}
                          >
                            <FormControl>
                              <SelectTrigger
                                ref={field.ref}
                                onBlur={field.onBlur}
                                className="w-full min-w-0"
                              >
                                <SelectValue className="min-w-0 flex-1 text-left" />
                              </SelectTrigger>
                            </FormControl>
                            <SelectContent>
                              {Object.entries(items).map(([value, label]) => (
                                <SelectItem
                                  key={value}
                                  value={value}
                                  disabled={value === 'unresolved'}
                                >
                                  {label}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  );
                })}
              <FormField
                name="reason"
                control={control}
                rules={{
                  validate: value => value.trim().length > 0 || '統合理由を入力してください',
                }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>統合理由</FormLabel>
                    <FormControl>
                      <Input maxLength={REASON_MAX_LENGTH} {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <div className="flex justify-end">
                <Button type="submit" variant="outline" disabled={busy}>
                  確定資料でプレビュー
                </Button>
              </div>
            </section>
          </fieldset>
        </form>
      </Form>

      {failure && <RegionError message={failure} onRetry={() => void refresh()} />}

      <section className={SECTION} aria-live="polite">
        <div>
          <h2 className="font-semibold text-foreground">統合の影響</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            統合は取り消せません。確定する前に読んでください。
          </p>
        </div>
        {!preview && (
          // 表示中の数字は直前の取得時点のもの。確定資料で取り直すまで統合させない
          <p className="text-sm text-foreground">
            「確定資料でプレビュー」で、この内容での影響を確かめてください。
          </p>
        )}
        {data && (
          <>
            <KeyValueList>
              <KeyValueRow label="移る受注">{data.moved_order_count} 件</KeyValueRow>
              <KeyValueRow label="影響する未完了受注（両顧客）">
                {data.unfinished_order_count} 件
              </KeyValueRow>
              <KeyValueRow label="移る連絡先">{data.moved_contact_count} 件</KeyValueRow>
              <KeyValueRow label="移る会員関連">{data.moved_link_count} 件</KeyValueRow>
              <KeyValueRow label="最終的な関連会員">
                {data.final_member_code ?? '未関連'}
              </KeyValueRow>
              <KeyValueRow label="ポイント残高（全体）" money>
                {data.point_balance === undefined ? '未関連' : `${data.point_balance} pt`}
              </KeyValueRow>
            </KeyValueList>
            <p className="text-sm text-foreground">
              未完了受注は完了時の有効な会員関連で帰属します。過去受注の自動帰属や残高変更は行いません。
            </p>
          </>
        )}
        <div className="flex items-start gap-2">
          <Checkbox
            id="merge-warnings"
            checked={acknowledged}
            disabled={!preview?.preview_token || busy}
            onCheckedChange={value => setAcknowledged(value === true)}
          />
          <Label htmlFor="merge-warnings">
            双方の注意事項・確定資料・優先指定・会員への影響を確認しました
          </Label>
        </div>
      </section>

      <div className="flex justify-end gap-3 border-t pt-4">
        {busy ? (
          // 統合の送信中に離れると成立したかを画面で受け取れない。プレビューの取得中も同じ busy で塞ぐ
          <Button type="button" variant="outline" disabled>
            統合せずに戻る
          </Button>
        ) : (
          <Button variant="outline" render={<Link href={listHref(storeId)} />}>
            統合せずに戻る
          </Button>
        )}
        <Button
          variant="destructive"
          disabled={!preview?.preview_token || !acknowledged || busy}
          onClick={handleSubmit(() => setConfirmOpen(true))}
        >
          統合する
        </Button>
      </div>
      <ConfirmDialog
        open={confirmOpen}
        title="この内容で顧客を統合しますか？"
        description="資料と全連絡先・受注・会員関連を存続側へまとめます。取り消せません。"
        confirmLabel="統合を確定"
        onConfirm={() => void merge()}
        onClose={() => setConfirmOpen(false)}
      />
    </>
  );
}
