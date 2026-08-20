'use client';

import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
import { Order, OrderApplicationRow, orderApi, orderApplicationApi } from '@/entities/order';
import { customerApi } from '@/entities/customer';
import { getApiErrorMessage, integerRule, useResource } from '@/shared/lib';
import { CastSearchCombobox } from './CastSearchCombobox';
import {
  Button,
  Checkbox,
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
  RegionError,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Textarea,
} from '@/shared/ui';

// 受付担当なしを表す番兵値。フォームが持つ値は従来どおり空文字に戻す。
const SELECT_NONE = '__none__';

const CUSTOMER_MODE_OPTIONS = [
  { value: 'none', label: '顧客に着けない（連絡先は受注に残る）' },
  { value: 'existing', label: '既存の顧客を選ぶ' },
  { value: 'new', label: '新規に台帳へ登録する' },
];

/** 確定時の顧客の決め方。電話番号での自動照合は行わないため、選ぶのは常に店員である。 */
type CustomerMode = 'none' | 'existing' | 'new';

interface ConfirmFormValues {
  /** '' は受付担当なし（実行者本人が候補の条件を満たせばサーバが補う）。 */
  receptionist_id: string;
  business_date: string;
  arrival_scheduled_start_time: string;
  arrival_scheduled_end_time: string;
  pax: number;
  /** '' はコース未定。 */
  course_minutes: string;
  remarks: string;
  /** 指名するキャストの id。'' は指名なし。 */
  cast_id: string;
  /** 指名を外して確定するか。申請が指名を持つときだけ意味を持つ。 */
  clear_cast: boolean;
  /** 顧客の決め方（ゲスト申請でだけ意味を持つ）。 */
  customer_mode: CustomerMode;
  /** 既存顧客を選んだときの id。 */
  customer_id: string;
  new_customer_name: string;
  new_customer_phone: string;
  /** 既存顧客の絞り込み語。送信ペイロードには乗らない画面だけの値。 */
  customer_search: string;
}

interface OrderApplicationConfirmModalProps {
  /** 確定対象の申請。null なら閉じている。 */
  application: OrderApplicationRow | null;
  onClose: () => void;
  /** 確定の成功後に、生成された受注を伴って呼ばれる（受付箱からの行の除去と作業キューの取り直し用）。 */
  onConfirmed: (created: Order) => void;
}

/**
 * 予約申請の確定モーダル。確定は申請内容を予填した受注の作成操作で、店舗が補完・調整した内容が
 * そのまま受注になる（申請原文は不変のまま残り、対照できる）。
 *
 * 確定前に申請だけを書き換える口は無い — 原文を直せると「不変のまま対照できる」が成り立たない（ADR 0017）。
 * ここに無い項目（割引・媒体など）は、確定後の受注を汎用更新で整える。
 */
export function OrderApplicationConfirmModal({
  application,
  onClose,
  onConfirmed,
}: OrderApplicationConfirmModalProps) {
  const form = useForm<ConfirmFormValues>({
    defaultValues: {
      receptionist_id: '',
      business_date: '',
      arrival_scheduled_start_time: '',
      arrival_scheduled_end_time: '',
      pax: 1,
      course_minutes: '',
      remarks: '',
      cast_id: '',
      clear_cast: false,
      customer_mode: 'none',
      customer_id: '',
      new_customer_name: '',
      new_customer_phone: '',
      customer_search: '',
    },
  });
  const {
    handleSubmit,
    reset,
    control,
    watch,
    setValue,
    formState: { isSubmitting },
  } = form;
  // 閉じている間は取りに行かない（開いた時点で取り直す）
  const {
    data: receptionistOptions,
    isLoading: receptionistsLoading,
    failure: receptionistsFailure,
    reload: loadReceptionists,
  } = useResource(application === null ? null : () => orderApi.listReceptionists(), [application]);
  // 台帳の照会は押したときだけ走らせる（開いただけで顧客を読みに行かない）。確定した語を state に
  // 移してから取得の deps に載せることで、入力中の 1 文字ごとに問い合わせが飛ぶこともない。
  const [customerQuery, setCustomerQuery] = useState<string | null>(null);
  const {
    data: customerPage,
    isLoading: customersLoading,
    failure: customersFailure,
    reload: reloadCustomers,
  } = useResource(
    customerQuery === null
      ? null
      : () => customerApi.list({ search: customerQuery || undefined, size: 10 }),
    [customerQuery]
  );
  const customerMatches = customerPage?.rows ?? null;
  const receptionistItems = [
    { value: SELECT_NONE, label: '未設定（自分が受付なら自動で補われます）' },
    ...(receptionistOptions ?? [])
      .filter(o => o.id !== undefined)
      .map(o => ({ value: String(o.id), label: o.display_name ?? '' })),
  ];

  const castName = application?.cast_name ?? application?.cast_id ?? '';

  useEffect(() => {
    if (!application) return;
    // 申請内容を予填する。ここで直した値は受注にだけ現れ、申請原文は動かない
    reset({
      receptionist_id: '',
      business_date: application.business_date ?? '',
      arrival_scheduled_start_time: application.arrival_scheduled_start_time?.slice(0, 5) ?? '',
      arrival_scheduled_end_time: '',
      pax: application.pax ?? 1,
      course_minutes: '',
      remarks: application.remarks ?? '',
      cast_id: application.cast_id ?? '',
      clear_cast: false,
      // 既定は顧客未設定。既定で台帳行を起こすと、店員が判断しないまま重複した行が積み上がる
      customer_mode: 'none',
      customer_id: '',
      // 新規作成を選んだ時点で使えるよう、申請の連絡先をここで予填しておく
      new_customer_name: application.contact_name ?? '',
      new_customer_phone: application.contact_phone_number ?? '',
      customer_search: application.contact_name ?? '',
    });
    setCustomerQuery(null);
  }, [application, reset]);

  const submit = async (values: ConfirmFormValues) => {
    if (!application) return;
    try {
      // 識別子の検証はアダプタが受け持つ（欠けていれば要求を組まずに名乗る失敗を投げる）
      const created = await orderApplicationApi.confirm(application.id, {
        receptionist_id: values.receptionist_id ? Number(values.receptionist_id) : undefined,
        business_date: values.business_date,
        arrival_scheduled_start_time: values.arrival_scheduled_start_time || undefined,
        arrival_scheduled_end_time: values.arrival_scheduled_end_time || undefined,
        cast_id: values.clear_cast || !values.cast_id ? undefined : values.cast_id,
        pax: Number(values.pax),
        course_minutes: values.course_minutes ? Number(values.course_minutes) : undefined,
        remarks: values.remarks ? values.remarks : undefined,
        // 顧客の選択はゲスト申請だけが送る。会員申請へ送るとサーバが撥ねる（顧客は会員の紐づけが決める）
        customer_id:
          isGuest && values.customer_mode === 'existing' && values.customer_id
            ? values.customer_id
            : undefined,
        new_customer:
          isGuest && values.customer_mode === 'new'
            ? {
                name: values.new_customer_name,
                phone_number: values.new_customer_phone || undefined,
              }
            : undefined,
      });
      notify.success('予約を確定しました');
      onConfirmed(created);
      onClose();
    } catch (error) {
      // 指名の再検証や失効など、サーバは対処方法を含む文言を返す。汎用文言に潰さない
      notify.error(getApiErrorMessage(error, '予約の確定に失敗しました'));
    }
  };

  const clearCast = watch('clear_cast');
  const selectedCastId = watch('cast_id');
  // 会員コードのスナップショットの有無がそのまま入口の別。サーバ側の判定と同じ根拠を使う
  const isGuest = application !== null && !application.requester_member_code;
  const customerMode = watch('customer_mode');
  const chosenCustomerId = watch('customer_id');
  const customerSearch = watch('customer_search');

  const searchCustomers = () => {
    // 同じ語で押し直したときは deps が動かないので、取得そのものをやり直す
    if (customerQuery === customerSearch) {
      void reloadCustomers();
      return;
    }
    setCustomerQuery(customerSearch);
  };

  return (
    <Dialog
      open={application !== null}
      onOpenChange={next => {
        // 確定中に閉じると、結果が分からないまま古い一覧が残る
        if (!next && !isSubmitting) onClose();
      }}
    >
      <DialogContent
        showCloseButton={false}
        aria-describedby={undefined}
        className="gap-0 rounded-[10px] p-0 sm:max-w-md"
      >
        <DialogTitle className="border-b px-6 py-4">予約申請を確定</DialogTitle>
        <Form {...form}>
          {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
              我々の文言は永久に描かれない。人数の min={1} は下の min 規則が引き継ぐ */}
          <form onSubmit={handleSubmit(submit)} className="space-y-4 px-6 py-5" noValidate>
            <FormField
              control={control}
              name="receptionist_id"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>受付担当</FormLabel>
                  <Select
                    items={receptionistItems}
                    value={field.value ? field.value : SELECT_NONE}
                    onValueChange={v => field.onChange(v === SELECT_NONE ? '' : v)}
                  >
                    <FormControl>
                      <SelectTrigger className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {receptionistItems.map(o => (
                        <SelectItem key={o.value} value={o.value}>
                          {o.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  {/* 送信は塞がない。受付担当は任意項目で、サーバ側の再検証が最終権威 */}
                  {receptionistsLoading ? (
                    // 候補が「未設定」だけの状態は「受付が 1 人も居ない」と区別がつかない
                    <p className="text-sm text-muted-foreground">読み込み中...</p>
                  ) : (
                    receptionistsFailure !== null && (
                      <RegionError
                        message="受付担当者の取得に失敗しました"
                        onRetry={() => void loadReceptionists()}
                      />
                    )
                  )}
                </FormItem>
              )}
            />
            <FormField
              control={control}
              name="business_date"
              rules={{ required: '営業日を選択してください' }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>営業日</FormLabel>
                  <FormControl>
                    <Input type="date" required {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <div className="grid grid-cols-2 gap-3">
              <FormField
                control={control}
                name="arrival_scheduled_start_time"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>開始時刻</FormLabel>
                    <FormControl>
                      <Input type="time" {...field} />
                    </FormControl>
                  </FormItem>
                )}
              />
              <FormField
                control={control}
                name="arrival_scheduled_end_time"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>終了時刻</FormLabel>
                    <FormControl>
                      <Input type="time" {...field} />
                    </FormControl>
                  </FormItem>
                )}
              />
            </div>
            {/* 人数はサーバ側が @Min(1)。検証の結果を出さないと、空欄のまま押した確定が無反応に見える */}
            <FormField
              control={control}
              name="pax"
              rules={{
                required: '人数を入力してください',
                min: { value: 1, message: '人数は 1 以上です' },
                // noValidate は type="number" の暗黙の step=1 も止める。これが無いと 1.5 が
                // Integer の pax へ届く
                validate: integerRule('人数'),
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>人数</FormLabel>
                  <FormControl>
                    <Input type="number" min={1} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={control}
              name="course_minutes"
              // 空欄はコース未定として通る（規則が空を素通しする）
              rules={{ validate: integerRule('コース分数') }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>コース分数（任意）</FormLabel>
                  <FormControl>
                    <Input type="number" min={0} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <div className="grid gap-2">
              <CastSearchCombobox
                id="application-confirm-cast"
                label="指名"
                castName={castName}
                onChange={castId => setValue('cast_id', castId, { shouldDirty: true })}
                disabled={clearCast}
              />
              {/* 解除は明示操作。無効になった指名（在籍停止・シフト取消）を外して確定する導線 */}
              {selectedCastId && (
                <FormField
                  control={control}
                  name="clear_cast"
                  render={({ field }) => (
                    <FormItem className="flex flex-row items-center gap-2">
                      <FormControl>
                        <Checkbox
                          id="confirm_clear_cast"
                          checked={field.value}
                          onCheckedChange={value => field.onChange(value === true)}
                        />
                      </FormControl>
                      <FormLabel htmlFor="confirm_clear_cast" className="font-medium">
                        指名を外して確定する
                      </FormLabel>
                    </FormItem>
                  )}
                />
              )}
            </div>
            {isGuest && (
              <div className="grid gap-3 rounded-md border p-3">
                {/* 電話番号での自動照合は行わない。機械が 1 行を選ぶことが誤帰属・なりすましの入口になる
                    ため、常客と認めた店員だけが既存の台帳行へ着けられる */}
                <FormField
                  control={control}
                  name="customer_mode"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>顧客（ゲスト申請）</FormLabel>
                      <Select
                        items={CUSTOMER_MODE_OPTIONS}
                        value={field.value}
                        onValueChange={v => field.onChange(v as CustomerMode)}
                      >
                        <FormControl>
                          <SelectTrigger className="w-full">
                            <SelectValue />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {CUSTOMER_MODE_OPTIONS.map(o => (
                            <SelectItem key={o.value} value={o.value}>
                              {o.label}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </FormItem>
                  )}
                />
                {customerMode === 'existing' && (
                  <div className="grid gap-2">
                    <div className="flex items-end gap-2">
                      <FormField
                        control={control}
                        name="customer_search"
                        render={({ field }) => (
                          <FormItem className="grow">
                            <FormLabel>台帳を名前で探す</FormLabel>
                            <FormControl>
                              <Input {...field} />
                            </FormControl>
                          </FormItem>
                        )}
                      />
                      <Button type="button" variant="outline" onClick={searchCustomers}>
                        検索
                      </Button>
                    </div>
                    {customersLoading ? (
                      <p className="text-muted-foreground text-sm">読み込み中...</p>
                    ) : customersFailure !== null ? (
                      // 顧客管理の権限が無い実行者ではサーバが拒否する。確定そのものは塞がない
                      <p className="text-muted-foreground text-sm">
                        台帳を検索できませんでした（顧客管理の権限が要ります）。新規作成か未設定で確定できます
                      </p>
                    ) : (
                      customerMatches !== null &&
                      customerMatches.length === 0 && (
                        <p className="text-muted-foreground text-sm">一致する顧客がいません</p>
                      )
                    )}
                    <ul className="grid gap-1">
                      {(customerMatches ?? []).map(candidate => (
                        <li key={candidate.id}>
                          <button
                            type="button"
                            onClick={() => setValue('customer_id', candidate.id ?? '')}
                            className={
                              chosenCustomerId === candidate.id
                                ? 'w-full rounded-md border border-primary px-3 py-2 text-left text-sm'
                                : 'w-full rounded-md border px-3 py-2 text-left text-sm'
                            }
                          >
                            {candidate.name}
                            {candidate.phone_number ? '（' + candidate.phone_number + '）' : ''}
                          </button>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
                {customerMode === 'new' && (
                  <div className="grid gap-3">
                    <FormField
                      control={control}
                      name="new_customer_name"
                      rules={{ required: 'お客様名を入力してください' }}
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>お客様名</FormLabel>
                          <FormControl>
                            <Input {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={control}
                      name="new_customer_phone"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>電話番号</FormLabel>
                          <FormControl>
                            <Input {...field} />
                          </FormControl>
                        </FormItem>
                      )}
                    />
                  </div>
                )}
              </div>
            )}
            <FormField
              control={control}
              name="remarks"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>備考</FormLabel>
                  <FormControl>
                    <Textarea rows={3} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <div className="flex justify-end gap-3 border-t pt-4">
              <Button type="button" variant="outline" onClick={onClose} disabled={isSubmitting}>
                キャンセル
              </Button>
              {/* 検証では塞がない — 灰色のボタンは何が足りないかを言わない。押せば欄の傍が言う */}
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? '確定中...' : '確定する'}
              </Button>
            </div>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
