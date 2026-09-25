'use client';

import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { customerApi, MergePreferences, MergePreview, MergeProfile } from '@/entities/customer';
import { getApiErrorMessage, useResource } from '@/shared/lib';
import { notify } from '@/shared/notify';
import {
  Button,
  Checkbox,
  ConfirmDialog,
  Dialog,
  DialogContent,
  DialogTitle,
  Input,
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

interface Props {
  open: boolean;
  survivingId: string;
  mergedId: string;
  onMerged: () => void;
  onClose: () => void;
  onBusyChange?: (busy: boolean) => void;
}

export function CustomerMergeConfirmDialog(props: Props) {
  const [busy, setBusy] = useState(false);
  return (
    <Dialog
      open={props.open}
      onOpenChange={open => {
        if (!open && !busy) props.onClose();
      }}
    >
      <DialogContent
        showCloseButton={!busy}
        aria-describedby={undefined}
        className="max-h-[calc(100vh-2rem)] overflow-y-auto sm:max-w-4xl"
      >
        <DialogTitle>顧客統合の資料と影響を確認</DialogTitle>
        {props.survivingId && props.mergedId && (
          <MergeLoader
            key={`${props.survivingId}:${props.mergedId}`}
            {...props}
            onBusyChange={setBusy}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function MergeLoader(props: Props) {
  const resource = useResource(
    props.open
      ? () => customerApi.mergePreview(props.survivingId, { merged_customer_id: props.mergedId })
      : null,
    [props.survivingId, props.mergedId, props.open]
  );
  if (resource.isLoading) return <p>読み込み中...</p>;
  if (!resource.data || resource.failure)
    return (
      <RegionError
        message="プレビューを取得できません。双方に会員関連がある場合は、先に理由付きで解除してください。"
        onRetry={() => void resource.reload()}
      />
    );
  return <MergeEditor {...props} initial={resource.data} />;
}

interface Values {
  profile: MergeProfile;
  preferences: MergePreferences;
  reason: string;
}
function MergeEditor({
  initial,
  survivingId,
  mergedId,
  onMerged,
  onBusyChange,
}: Props & { initial: MergePreview }) {
  const [data, setData] = useState(initial);
  const [preview, setPreview] = useState<MergePreview | null>(null);
  const [unresolved, setUnresolved] = useState(initial.preference_conflicts);
  const [acknowledged, setAcknowledged] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [busy, setLocalBusy] = useState(false);
  const setBusy = (value: boolean) => {
    setLocalBusy(value);
    onBusyChange?.(value);
  };
  const [failure, setFailure] = useState<string | null>(null);
  const {
    register,
    control,
    getValues,
    setValue,
    handleSubmit,
    formState: { errors },
  } = useForm<Values>({
    defaultValues: {
      profile: initial.profile,
      preferences: initial.preferred_contacts,
      reason: '',
    },
  });
  const invalidate = () => {
    setPreview(null);
    setAcknowledged(false);
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
      const invalid = (['PHONE', 'EMAIL', 'LINE'] as const).filter(type => {
        const key = type.toLowerCase() as keyof MergePreferences;
        const id = preferences[key];
        return id !== null && !contacts.some(c => c.id === id && c.type === type && !c.deleted);
      });
      setUnresolved(invalid);
    } catch (error) {
      setFailure(getApiErrorMessage(error, '最新資料の取得に失敗しました'));
    } finally {
      setBusy(false);
    }
  };
  const review = handleSubmit(async values => {
    if (unresolved.length) return;
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
      setFailure(getApiErrorMessage(error, 'プレビューの確認に失敗しました'));
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
        profile: getValues('profile'),
        preferred_contacts: getValues('preferences'),
        preview_token: preview.preview_token,
        warnings_acknowledged: acknowledged,
        operation_reason: values.reason.trim(),
      });
      notify.success('顧客を統合しました');
      onMerged();
    } catch (error) {
      invalidate();
      setFailure(getApiErrorMessage(error, '顧客の統合に失敗しました'));
    } finally {
      setBusy(false);
    }
  });
  return (
    <div className="min-w-0 space-y-6">
      <div className="grid gap-6 md:grid-cols-2">
        <MergeSnapshotView title="存続側の原資料" snapshot={data.surviving} />
        <MergeSnapshotView title="被統合側の原資料" snapshot={data.merged} />
      </div>
      <form onSubmit={review} className="space-y-6">
        <fieldset disabled={busy} className="min-w-0 space-y-6">
          <legend className="font-medium">統合後の資料</legend>
          {profileFields.map(([key, label, max]) => (
            <div key={key} className="space-y-2">
              <Label htmlFor={`merge-${key}`}>{label}</Label>
              <div className="flex flex-wrap gap-2">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    setValue(`profile.${key}`, data.surviving.profile[key]);
                    invalidate();
                  }}
                >
                  存続側の{label}を採用
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    setValue(`profile.${key}`, data.merged.profile[key]);
                    invalidate();
                  }}
                >
                  被統合側の{label}を採用
                </Button>
              </div>
              <Textarea
                id={`merge-${key}`}
                maxLength={max}
                {...register(`profile.${key}`, {
                  onChange: invalidate,
                  setValueAs: value => (value === '' ? null : value),
                })}
              />
            </div>
          ))}
          <Controller
            name="profile.has_pet"
            control={control}
            render={({ field }) => (
              <div className="space-y-2">
                <Label htmlFor="merge-pet">ペットの有無</Label>
                <Select
                  items={{ unknown: '未確認', true: 'あり', false: 'なし' }}
                  value={field.value === null ? 'unknown' : String(field.value)}
                  onValueChange={v => {
                    field.onChange(v === 'unknown' ? null : v === 'true');
                    invalidate();
                  }}
                >
                  <SelectTrigger id="merge-pet">
                    <SelectValue className="min-w-0 flex-1 text-left" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="unknown">未確認</SelectItem>
                    <SelectItem value="true">あり</SelectItem>
                    <SelectItem value="false">なし</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            )}
          />
          {(['PHONE', 'EMAIL', 'LINE'] as const).map(type => {
            const key = type.toLowerCase() as keyof MergePreferences;
            const items = {
              unresolved: '一件または指定なしを選択',
              none: '指定なし',
              ...Object.fromEntries(
                [...data.surviving.contacts, ...data.merged.contacts]
                  .filter(c => c.type === type && !c.deleted)
                  .map(c => [c.id, `${c.value}（ID: ${c.id} / 由来: ${c.origin_customer_id}）`])
              ),
            };
            return (
              <Controller
                key={type}
                name={`preferences.${key}`}
                control={control}
                render={({ field }) => (
                  <div className="space-y-2">
                    <Label htmlFor={`preferred-${key}`}>
                      {{ PHONE: '電話', EMAIL: 'メール', LINE: 'LINE' }[type]}の優先連絡先
                    </Label>
                    <Select
                      items={items}
                      value={unresolved.includes(type) ? 'unresolved' : (field.value ?? 'none')}
                      onValueChange={v => {
                        field.onChange(v === 'none' ? null : v);
                        setUnresolved(current => current.filter(t => t !== type));
                        invalidate();
                      }}
                    >
                      <SelectTrigger id={`preferred-${key}`} className="w-full min-w-0">
                        <SelectValue className="min-w-0 flex-1 text-left" />
                      </SelectTrigger>
                      <SelectContent>
                        {Object.entries(items).map(([value, label]) => (
                          <SelectItem key={value} value={value} disabled={value === 'unresolved'}>
                            {label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                )}
              />
            );
          })}
          <div className="space-y-2">
            <Label htmlFor="merge-reason">統合理由</Label>
            <Input
              id="merge-reason"
              maxLength={500}
              aria-invalid={!!errors.reason}
              aria-describedby={errors.reason ? 'merge-reason-error' : undefined}
              {...register('reason', {
                validate: value => value.trim().length > 0 || '統合理由を入力してください',
              })}
            />
            {errors.reason && (
              <p id="merge-reason-error" className="text-sm text-destructive-strong">
                {errors.reason.message}
              </p>
            )}
          </div>
          <Button type="submit" disabled={busy || unresolved.length > 0}>
            確定資料でプレビュー
          </Button>
        </fieldset>
      </form>
      {failure && <RegionError message={failure} onRetry={() => void refresh()} />}
      <div className="space-y-2 rounded-lg border p-4 text-sm" aria-live="polite">
        <p>最終的な関連会員: {data.final_member_code ?? '未関連'}</p>
        <p>
          現在のプラットフォーム全体残高:{' '}
          {data.point_balance === undefined ? '未関連' : `${data.point_balance} pt`}
        </p>
        <p>影響する未完了受注: {data.unfinished_order_count} 件</p>
        <p>
          移動する受注 {data.moved_order_count} 件・連絡先 {data.moved_contact_count} 件・関連{' '}
          {data.moved_link_count} 件
        </p>
        <p>
          未完了受注は完了時の有効な会員関連で帰属します。過去受注の自動帰属や残高変更は行いません。統合は取り消せません。
        </p>
      </div>
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
      <Button
        variant="destructive"
        disabled={!preview?.preview_token || !acknowledged || busy}
        onClick={handleSubmit(() => setConfirmOpen(true))}
      >
        統合する
      </Button>
      <ConfirmDialog
        open={confirmOpen}
        title="この内容で顧客を統合しますか？"
        description="資料と全連絡先・受注・会員関連を存続側へまとめます。取り消せません。"
        confirmLabel="統合を確定"
        onConfirm={() => void merge()}
        onClose={() => setConfirmOpen(false)}
      />
    </div>
  );
}
