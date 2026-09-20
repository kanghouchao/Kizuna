'use client';

import { useOrderConfirmation } from './useOrderConfirmation';

import { OrderFeeLinesField } from './OrderFeeLinesField';
import { OrderCourseField } from './OrderCourseField';
import { OrderSpecialServicesField } from './OrderSpecialServicesField';

import { OrderCustomerField } from './OrderCustomerField';
import { OrderContactFields } from './OrderContactFields';
import { CustomerSelection, ContactSnapshot } from '@/entities/order';
import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
import {
  Order,
  OrderApplicationRow,
  orderApplicationApi,
  OrderFeeLineInput,
  toFeeLineInputs,
} from '@/entities/order';
import { getApiErrorMessage, integerRule } from '@/shared/lib';
import { OrderReceptionistField } from './OrderReceptionistField';
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
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Textarea,
} from '@/shared/ui';

interface ConfirmFormValues {
  fee_lines: OrderFeeLineInput[];
  /** '' は受付担当なし（実行者本人が候補の条件を満たせばサーバが補う）。 */
  receptionist_id: string;
  business_date: string;
  arrival_scheduled_start_time: string;
  arrival_scheduled_end_time: string;
  pax: number;
  /** '' はコース未定。 */
  /** 適用するコース名の写し。確定は受注の出生なので、快照はここで写る。 */
  special_service_ids: string[];
  course_id: string;
  remarks: string;
  /** 指名するキャストの id。'' は指名なし。 */
  cast_id: string;
  /** 指名を外して確定するか。申請が指名を持つときだけ意味を持つ。 */
  clear_cast: boolean;
  customer_selection: CustomerSelection;
  contact_snapshot: ContactSnapshot;
}

interface OrderApplicationConfirmModalProps {
  open: boolean;
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
  open,
  application,
  onClose,
  onConfirmed,
}: OrderApplicationConfirmModalProps) {
  const confirmation = useOrderConfirmation(application?.id);
  const form = useForm<ConfirmFormValues>({
    defaultValues: {
      receptionist_id: '',
      business_date: '',
      arrival_scheduled_start_time: '',
      arrival_scheduled_end_time: '',
      pax: 1,
      special_service_ids: [],
      course_id: '',
      fee_lines: [],
      remarks: '',
      cast_id: '',
      clear_cast: false,
      customer_selection: { mode: 'NONE' },
      contact_snapshot: {},
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
      special_service_ids: [],
      course_id: '',
      fee_lines: [],
      remarks: application.remarks ?? '',
      cast_id: application.cast_id ?? '',
      clear_cast: false,
      customer_selection: { mode: 'NONE' },
      contact_snapshot: {
        ...application.contact_snapshot,
        name: application.contact_snapshot?.name ?? application.requester_declared_name ?? '',
      },
    });
  }, [application, reset]);

  const submit = async (values: ConfirmFormValues) => {
    if (!application) return;
    try {
      // 識別子の検証はアダプタが受け持つ（欠けていれば要求を組まずに名乗る失敗を投げる）
      const request = {
        receptionist_id: values.receptionist_id ? Number(values.receptionist_id) : undefined,
        business_date: values.business_date,
        arrival_scheduled_start_time: values.arrival_scheduled_start_time || undefined,
        arrival_scheduled_end_time: values.arrival_scheduled_end_time || undefined,
        cast_id: values.clear_cast || !values.cast_id ? undefined : values.cast_id,
        pax: Number(values.pax),
        special_service_ids: values.special_service_ids,
        course_id: values.course_id,
        fee_lines: toFeeLineInputs(values.fee_lines),
        remarks: values.remarks ? values.remarks : undefined,
        customer_selection: isGuest ? values.customer_selection : undefined,
        contact_snapshot: values.contact_snapshot,
      };
      const token = await confirmation.confirm(() =>
        orderApplicationApi.previewConfirmation(application.id, request)
      );
      if (!token) return;
      const created = await orderApplicationApi.confirm(application.id, {
        ...request,
        confirmation_token: token,
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
  return (
    <>
      {confirmation.dialog}
      <Dialog
        open={open}
        onOpenChange={next => {
          // 確定中に閉じると、結果が分からないまま古い一覧が残る
          if (!next && !isSubmitting) onClose();
        }}
      >
        <DialogContent
          showCloseButton={false}
          aria-describedby={undefined}
          className="max-h-[90dvh] gap-0 overflow-y-auto rounded-[10px] p-0 sm:max-w-md"
        >
          <DialogTitle className="border-b px-6 py-4">予約申請を確定</DialogTitle>
          <Form {...form}>
            {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
              我々の文言は永久に描かれない。人数の min={1} は下の min 規則が引き継ぐ */}
            <form onSubmit={handleSubmit(submit)} className="space-y-4 px-6 py-5" noValidate>
              {application !== null && (
                <OrderReceptionistField key={application.id} scene="confirm" />
              )}
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
              <OrderCourseField required />
              <OrderFeeLinesField />
              {clearCast ? (
                <p role="status">指名を外すため、特殊サービスは選択できません。</p>
              ) : (
                <OrderSpecialServicesField />
              )}
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
                            onCheckedChange={value => {
                              field.onChange(value === true);
                              if (value === true) {
                                setValue('special_service_ids', [], { shouldDirty: true });
                              }
                            }}
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
              {isGuest ? <OrderCustomerField /> : <p>顧客は会員本人の関連から決まります。</p>}
              <OrderContactFields />
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
    </>
  );
}
