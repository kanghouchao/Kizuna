'use client';

import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
import { Order, orderApi } from '@/entities/order';
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

interface ReservationRequestEditFormValues {
  /** '' は受付担当なし。 */
  receptionist_id: string;
  pax: number;
  remarks: string;
  /** 指名するキャストの id。'' は指名なし。 */
  cast_id: string;
  /** 指名を外して保存するか。申請が指名を持つときだけ意味を持つ。 */
  clear_cast: boolean;
}

interface ReservationRequestEditModalProps {
  /** 編集対象の申請。null なら閉じている。 */
  request: Order | null;
  onClose: () => void;
  /** 保存の成功後に、更新後の申請を伴って呼ばれる（一覧の当該行の差し替え用）。 */
  onSaved: (updated: Order) => void;
}

/**
 * 未確定の予約申請の編集モーダル。
 *
 * 会員は指名なしでも申請できるため、キャストを埋めずに人数・備考・受付担当を直せることと、
 * 無効になった指名を確定前に外す・別のキャストへ立て直すことがこの画面の目的。編集の収口は
 * 受注の汎用更新とは別で、送った内容がそのまま新しい申請内容になる（画面が持たない項目は
 * 契約にも無いので書き換わらない）。
 */
export function ReservationRequestEditModal({
  request,
  onClose,
  onSaved,
}: ReservationRequestEditModalProps) {
  const form = useForm<ReservationRequestEditFormValues>({
    defaultValues: { receptionist_id: '', pax: 1, remarks: '', cast_id: '', clear_cast: false },
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
  } = useResource(request === null ? null : () => orderApi.listReceptionists(), [request]);
  const receptionistItems = [
    { value: SELECT_NONE, label: '未設定' },
    ...(receptionistOptions ?? [])
      .filter(o => o.id !== undefined)
      .map(o => ({ value: String(o.id), label: o.display_name ?? '' })),
  ];

  const castName = request?.cast_name ?? request?.cast_id ?? '';

  useEffect(() => {
    if (!request) return;
    reset({
      receptionist_id: request.receptionist_id != null ? String(request.receptionist_id) : '',
      pax: request.pax ?? 1,
      remarks: request.remarks ?? '',
      cast_id: request.cast_id ?? '',
      clear_cast: false,
    });
  }, [request, reset]);

  const submit = async (values: ReservationRequestEditFormValues) => {
    if (!request?.id) return;
    try {
      const updated = await orderApi.updateReservationRequest(request.id, {
        receptionist_id: values.receptionist_id ? Number(values.receptionist_id) : undefined,
        // 指名は「そのまま送り返す」ことで維持される。外すときだけ送らない
        cast_id: values.clear_cast || !values.cast_id ? undefined : values.cast_id,
        pax: Number(values.pax),
        remarks: values.remarks ? values.remarks : undefined,
      });
      notify.success('予約申請を更新しました');
      onSaved(updated);
      onClose();
    } catch (error) {
      notify.error(getApiErrorMessage(error, '予約申請の更新に失敗しました'));
    }
  };

  const clearCast = watch('clear_cast');
  const selectedCastId = watch('cast_id');

  return (
    <Dialog
      open={request !== null}
      onOpenChange={next => {
        // 保存中に閉じると、結果が分からないまま古い一覧が残る
        if (!next && !isSubmitting) onClose();
      }}
    >
      <DialogContent
        showCloseButton={false}
        aria-describedby={undefined}
        className="gap-0 rounded-[10px] p-0 sm:max-w-md"
      >
        <DialogTitle className="border-b px-6 py-4">予約申請を編集</DialogTitle>
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
            {/* 人数はサーバ側が @NotNull @Min(1)。検証の結果を出さないと、空欄のまま押した保存が
                無反応に見える */}
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
            <div className="grid gap-2">
              <CastSearchCombobox
                id="reservation-request-cast"
                label="指名"
                castName={castName}
                onChange={castId => setValue('cast_id', castId, { shouldDirty: true })}
                disabled={clearCast}
              />
              {/* 解除は明示操作。今この場で立てた指名も取り消せるよう、元から指名があったかでは
                  なく「今この申請に指名が載っているか」で出す */}
              {selectedCastId && (
                <FormField
                  control={control}
                  name="clear_cast"
                  render={({ field }) => (
                    <FormItem className="flex flex-row items-center gap-2">
                      <FormControl>
                        <Checkbox
                          id="clear_cast"
                          checked={field.value}
                          onCheckedChange={value => field.onChange(value === true)}
                        />
                      </FormControl>
                      <FormLabel htmlFor="clear_cast" className="font-medium">
                        指名を外す
                      </FormLabel>
                    </FormItem>
                  )}
                />
              )}
            </div>
            {/* 上限はサーバ側の @Size(max = 500) に合わせる（会員の申請画面と同じ） */}
            <FormField
              control={control}
              name="remarks"
              rules={{
                maxLength: { value: 500, message: '備考は 500 文字以内で入力してください' },
              }}
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
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? '保存中...' : '保存する'}
              </Button>
            </div>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
