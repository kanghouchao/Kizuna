'use client';

import { useEffect, useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import {
  BENEFIT_RULE_REPEAT_POLICY_OPTIONS,
  BENEFIT_RULE_TYPE_OPTIONS,
  BenefitRuleCreateRequest,
  BenefitRuleResponse,
  BenefitRuleRepeatPolicy,
  BenefitRuleStoreScopeType,
  BenefitRuleType,
  benefitRuleApi,
} from '@/entities/benefit-rule';
import { PlatformStore } from '@/entities/user';
import { StoreSetPicker } from '@/features/staff-management';
import { getApiErrorMessage, useResource } from '@/shared/lib';
import { notify } from '@/shared/notify';
import {
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  RegionError,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui';

interface BenefitRuleFormValues {
  name: string;
  type: BenefitRuleType;
  store_scope_type: BenefitRuleStoreScopeType;
  store_ids: number[];
  effective_from: string;
  effective_until: string;
  grant_validity_days: string;
  repeat_policy: BenefitRuleRepeatPolicy;
  points: string;
  referrer_points: string;
  referred_points: string;
}

const EMPTY_FORM: BenefitRuleFormValues = {
  name: '',
  type: 'VISIT',
  store_scope_type: 'ALL_STORES',
  store_ids: [],
  effective_from: '',
  effective_until: '',
  grant_validity_days: '',
  repeat_policy: 'EVERY_TIME',
  points: '',
  referrer_points: '',
  referred_points: '',
};

interface BenefitRuleFormModalProps {
  onClose: () => void;
  /** 編集対象の規則 id。null なら新規作成。 */
  editingId: number | null;
  /** 保存成功後に呼ばれる（一覧の再取得用）。 */
  onSaved: () => void;
  stores: PlatformStore[];
  storesLoading: boolean;
  storesFailed: boolean;
  onReloadStores: () => void;
}

/** 空欄は「値なし」。数値欄は空のまま送ると 0 と区別できないので null へ畳む。 */
function optionalNumber(raw: string): number | null {
  return raw.trim() === '' ? null : Number(raw);
}

function optionalText(raw: string): string | null {
  return raw.trim() === '' ? null : raw;
}

/**
 * 特典規則の新規作成・編集モーダル。開いたときだけ mount される前提で、編集フォームの中身
 * （店舗 ID の列挙と version）は mount 時に id で個別取得する。
 *
 * 種別は作成時にしか選べない。付与済みの仕訳が規則を指し返した後に種別を翻すと、記帳済みの
 * 付与の取消方法が遡って変わるため、更新の要求型に type は存在しない。
 */
export function BenefitRuleFormModal({
  onClose,
  editingId,
  onSaved,
  stores,
  storesLoading,
  storesFailed,
  onReloadStores,
}: BenefitRuleFormModalProps) {
  const {
    data: editingRule,
    isLoading: detailLoading,
    failure: detailFailure,
    reload: reloadEditingRule,
  } = useResource(editingId === null ? null : () => benefitRuleApi.get(editingId), [editingId]);

  const form = useForm<BenefitRuleFormValues>({ defaultValues: EMPTY_FORM });
  const {
    control,
    handleSubmit,
    reset,
    setValue,
    formState: { isSubmitting },
  } = form;

  const type = useWatch({ control, name: 'type' });
  const storeScopeType = useWatch({ control, name: 'store_scope_type' });
  const storeIds = useWatch({ control, name: 'store_ids' });

  // 届いた詳細をフォームへ移し終えたか。移した相手を持つのは、取り直しで届く別の詳細も
  // 同じ経路で載せ直すため
  const [seededRule, setSeededRule] = useState<BenefitRuleResponse | null>(null);

  useEffect(() => {
    if (editingRule !== null && editingRule !== seededRule) {
      reset({
        name: editingRule.name ?? '',
        type: editingRule.type ?? 'VISIT',
        store_scope_type: editingRule.store_scope_type ?? 'ALL_STORES',
        store_ids: editingRule.store_ids ?? [],
        effective_from: editingRule.effective_from ?? '',
        effective_until: editingRule.effective_until ?? '',
        grant_validity_days: editingRule.grant_validity_days?.toString() ?? '',
        repeat_policy: editingRule.repeat_policy ?? 'EVERY_TIME',
        points: editingRule.points?.toString() ?? '',
        referrer_points: editingRule.referrer_points?.toString() ?? '',
        referred_points: editingRule.referred_points?.toString() ?? '',
      });
      setSeededRule(editingRule);
    }
  }, [editingRule, seededRule, reset]);

  // 発火事象が店舗文脈を持たないログイン規則は全店舗しか取れない。サーバも拒むが、
  // 選べる形で見せてから撥ねるより、選択肢ごと畳む
  useEffect(() => {
    if (type === 'LOGIN' && storeScopeType !== 'ALL_STORES') {
      setValue('store_scope_type', 'ALL_STORES');
      setValue('store_ids', []);
    }
  }, [type, storeScopeType, setValue]);

  // 404 は何度押しても取れないので再試行を出さない（削除の口は無いが、消えた店舗を
  // 参照する古いリンク等で届きうる）。読めなかっただけの失敗とは提示の形を分ける。
  const ruleMissing = detailFailure === 'notFound';

  // 編集モードで詳細が未着のうちは保存させない。先に欄を出すと、その 1 回の描画が空のまま
  // 残り「店舗が 1 つも選ばれていない」に見える
  const editingLoading =
    editingId !== null && (detailLoading || editingRule === null || seededRule !== editingRule);

  const isReferral = type === 'REFERRAL';

  const submit = async (values: BenefitRuleFormValues) => {
    const payload: Omit<BenefitRuleCreateRequest, 'type'> = {
      name: values.name,
      store_scope_type: values.store_scope_type,
      store_ids: values.store_scope_type === 'SPECIFIC_STORES' ? values.store_ids : [],
      effective_from: optionalText(values.effective_from),
      effective_until: optionalText(values.effective_until),
      grant_validity_days: optionalNumber(values.grant_validity_days),
      repeat_policy: values.repeat_policy,
      points: isReferral ? null : optionalNumber(values.points),
      referrer_points: isReferral ? optionalNumber(values.referrer_points) : null,
      referred_points: isReferral ? optionalNumber(values.referred_points) : null,
    };
    try {
      if (editingId !== null) {
        if (editingRule?.version === undefined) return;
        await benefitRuleApi.update(editingId, { ...payload, version: editingRule.version });
        notify.success('特典規則を更新しました');
      } else {
        await benefitRuleApi.create({ ...payload, type: values.type });
        notify.success('特典規則を作成しました');
      }
      onSaved();
      onClose();
    } catch (error) {
      notify.error(getApiErrorMessage(error, '特典規則の保存に失敗しました'));
    }
  };

  return (
    <Dialog
      open
      onOpenChange={next => {
        // 送信中は閉じさせない。閉じると unmount で isSubmitting が消え、開き直した複製から
        // 二重送信できてしまう
        if (!next && !isSubmitting) onClose();
      }}
    >
      <DialogContent
        showCloseButton={false}
        aria-describedby={undefined}
        className="max-h-[calc(100vh-2rem)] gap-0 overflow-y-auto rounded-[10px] p-0 sm:max-w-lg"
      >
        <DialogTitle className="border-b px-6 py-4 text-lg font-semibold text-foreground">
          {editingId === null ? '特典規則を作成' : '特典規則を編集'}
        </DialogTitle>
        {detailFailure !== null ? (
          <div className="space-y-4 px-6 py-5">
            {ruleMissing ? (
              <p className="text-sm text-muted-foreground">この特典規則は見つかりませんでした。</p>
            ) : (
              <RegionError
                message="特典規則の取得に失敗しました"
                onRetry={() => void reloadEditingRule()}
              />
            )}
            <div className="flex justify-end border-t pt-4">
              <Button type="button" variant="outline" onClick={onClose}>
                閉じる
              </Button>
            </div>
          </div>
        ) : editingLoading ? (
          <p className="px-6 py-5 text-sm text-muted-foreground">読み込み中...</p>
        ) : (
          <Form {...form}>
            <form onSubmit={handleSubmit(submit)} className="space-y-4 px-6 py-5" noValidate>
              <FormField
                control={control}
                name="name"
                rules={{
                  required: '規則名を入力してください',
                  maxLength: { value: 100, message: '規則名は 100 文字以内で入力してください' },
                }}
                render={({ field }) => (
                  <FormItem className="gap-1">
                    <FormLabel>規則名</FormLabel>
                    <FormControl>
                      <Input type="text" required {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={control}
                name="type"
                render={({ field }) => (
                  <FormItem className="gap-1">
                    <FormLabel>種別</FormLabel>
                    <Select
                      items={BENEFIT_RULE_TYPE_OPTIONS}
                      value={field.value}
                      onValueChange={v => field.onChange(v as BenefitRuleType)}
                      disabled={editingId !== null}
                      required
                    >
                      <FormControl>
                        <SelectTrigger className="w-full" ref={field.ref}>
                          <SelectValue />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {BENEFIT_RULE_TYPE_OPTIONS.map(o => (
                          <SelectItem key={o.value} value={o.value}>
                            {o.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormDescription className="text-xs">
                      作成後は変更できません（記帳済みの付与の取消方法が種別から導かれるため）
                    </FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
              {type === 'LOGIN' ? (
                <div>
                  <span className="mb-1 block text-sm font-medium text-foreground">適用店舗</span>
                  <p className="text-sm text-muted-foreground">
                    全店舗（ログインは店舗での事象ではないため、店舗集合で絞れません）
                  </p>
                </div>
              ) : (
                <StoreSetPicker
                  label="適用店舗（発火を拾う範囲）"
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
              )}
              <div className="grid grid-cols-2 gap-3">
                <FormField
                  control={control}
                  name="effective_from"
                  render={({ field }) => (
                    <FormItem className="gap-1">
                      <FormLabel>適用開始日</FormLabel>
                      <FormControl>
                        <Input type="date" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={control}
                  name="effective_until"
                  render={({ field }) => (
                    <FormItem className="gap-1">
                      <FormLabel>適用終了日</FormLabel>
                      <FormControl>
                        <Input type="date" {...field} />
                      </FormControl>
                      <FormDescription className="text-xs">
                        空欄は常設（発火の窓を設けない）
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>
              <FormField
                control={control}
                name="grant_validity_days"
                rules={{ min: { value: 1, message: '有効期間は 1 日以上で入力してください' } }}
                render={({ field }) => (
                  <FormItem className="gap-1">
                    <FormLabel>付与ポイントの有効期間（日）</FormLabel>
                    <FormControl>
                      <Input type="number" min={1} {...field} />
                    </FormControl>
                    <FormDescription className="text-xs">空欄は無期限</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={control}
                name="repeat_policy"
                render={({ field }) => (
                  <FormItem className="gap-1">
                    <FormLabel>重複可否</FormLabel>
                    <Select
                      items={BENEFIT_RULE_REPEAT_POLICY_OPTIONS}
                      value={field.value}
                      onValueChange={v => field.onChange(v as BenefitRuleRepeatPolicy)}
                      required
                    >
                      <FormControl>
                        <SelectTrigger className="w-full" ref={field.ref}>
                          <SelectValue />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {BENEFIT_RULE_REPEAT_POLICY_OPTIONS.map(o => (
                          <SelectItem key={o.value} value={o.value}>
                            {o.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
              {isReferral ? (
                <div className="grid grid-cols-2 gap-3">
                  <FormField
                    control={control}
                    name="referrer_points"
                    rules={{
                      required: '紹介者点数を入力してください',
                      min: { value: 1, message: '紹介者点数は 1 以上で入力してください' },
                    }}
                    render={({ field }) => (
                      <FormItem className="gap-1">
                        <FormLabel>紹介者点数</FormLabel>
                        <FormControl>
                          <Input type="number" min={1} required {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={control}
                    name="referred_points"
                    rules={{
                      required: '被紹介者点数を入力してください',
                      min: { value: 1, message: '被紹介者点数は 1 以上で入力してください' },
                    }}
                    render={({ field }) => (
                      <FormItem className="gap-1">
                        <FormLabel>被紹介者点数</FormLabel>
                        <FormControl>
                          <Input type="number" min={1} required {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
              ) : (
                <FormField
                  control={control}
                  name="points"
                  rules={{
                    required: '付与ポイントを入力してください',
                    min: { value: 1, message: '付与ポイントは 1 以上で入力してください' },
                  }}
                  render={({ field }) => (
                    <FormItem className="gap-1">
                      <FormLabel>付与ポイント</FormLabel>
                      <FormControl>
                        <Input type="number" min={1} required {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              )}
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
