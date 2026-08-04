'use client';

import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'react-hot-toast';
import { Order, OrderReceptionist, orderApi } from '@/entities/order';
import { getApiErrorMessage } from '@/shared/lib';
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
  Label,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Textarea,
} from '@/shared/ui';

// Radix Select は value="" を許容しないため、受付担当なしを表す番兵値。
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
    register,
    handleSubmit,
    reset,
    control,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = form;
  const [receptionistOptions, setReceptionistOptions] = useState<OrderReceptionist[]>([]);
  // 指名欄に見えている文字列。id では「打ちかけで未選択」と「空欄で指名なし」を区別できない
  const [castNameInput, setCastNameInput] = useState('');

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
    setCastNameInput(request.cast_name ?? request.cast_id ?? '');
  }, [request, reset]);

  useEffect(() => {
    if (!request) return;
    orderApi
      .listReceptionists()
      .then(setReceptionistOptions)
      .catch(() => toast.error('受付担当者の取得に失敗しました'));
  }, [request]);

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
      toast.success('予約申請を更新しました');
      onSaved(updated);
      onClose();
    } catch (error) {
      toast.error(getApiErrorMessage(error, '予約申請の更新に失敗しました'));
    }
  };

  const clearCast = watch('clear_cast');

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
          <form onSubmit={handleSubmit(submit)} className="space-y-4 px-6 py-5">
            <FormField
              control={control}
              name="receptionist_id"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>受付担当</FormLabel>
                  <Select
                    value={field.value ? field.value : SELECT_NONE}
                    onValueChange={v => field.onChange(v === SELECT_NONE ? '' : v)}
                  >
                    <FormControl>
                      <SelectTrigger className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      <SelectItem value={SELECT_NONE}>未設定</SelectItem>
                      {receptionistOptions.map(option => {
                        const id = option.id;
                        if (id === undefined) return null;
                        return (
                          <SelectItem key={id} value={String(id)}>
                            {option.display_name}
                          </SelectItem>
                        );
                      })}
                    </SelectContent>
                  </Select>
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
                onChange={(castId, name) => {
                  setCastNameInput(name);
                  setValue('cast_id', castId ?? '', { shouldValidate: true, shouldDirty: true });
                }}
                disabled={clearCast}
              />
              {/* 契約は部分更新ではないので、決着の付いていない指名を送ると黙って消える。止めるのは
                  「元は指名があった」（空欄も外すことになる）か「名前が入っている」（選び切って
                  いない）とき。指名なしの申請で空欄のままなのだけが、そのまま通す状態 */}
              <input
                type="hidden"
                {...register('cast_id', {
                  validate: value =>
                    clearCast ||
                    !!value ||
                    (!request?.cast_id && !castNameInput) ||
                    '指名を変えるときは候補から選んでください。外す場合は「指名を外す」にチェックしてください',
                })}
              />
              {!clearCast && errors.cast_id && (
                <p className="text-xs text-destructive-strong">{errors.cast_id.message}</p>
              )}
              {request?.cast_id && (
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
            <div className="grid gap-2">
              <Label htmlFor="remarks">備考</Label>
              {/* 上限はサーバ側の @Size(max = 500) に合わせる（会員の申請画面と同じ） */}
              <Textarea
                id="remarks"
                rows={3}
                {...register('remarks', {
                  maxLength: { value: 500, message: '備考は 500 文字以内で入力してください' },
                })}
              />
              {errors.remarks && (
                <p className="text-xs text-destructive-strong">{errors.remarks.message}</p>
              )}
            </div>
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
