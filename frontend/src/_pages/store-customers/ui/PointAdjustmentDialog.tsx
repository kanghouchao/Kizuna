'use client';

import { useEffect, useRef } from 'react';
import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
import { CustomerPointBalanceResponse, customerApi } from '@/entities/customer';
import { getApiErrorMessage, integerRule } from '@/shared/lib';
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
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Textarea,
} from '@/shared/ui';

/** 空欄と 0 は別物なので、未入力の文言は 1 箇所に持って両方の規則から指す。 */
const DELTA_REQUIRED = '増減ポイントを入力してください';

/** 事由の上限。台帳側の @Size(max = 500) と同値。 */
const REASON_MAX_LENGTH = 500;

/**
 * 事由の分類。業務で起きる調整はこの三つに収まる（#806 の業務確認）。
 *
 * 列は増やさず、選んだ分類を接頭辞として自由記述と連結して送る — 後から分類で拾えて、かつ
 * 「なぜその額なのか」は自由記述にしか書けない。
 */
const REASON_CATEGORIES = ['補填', '訂正', '個別施策'] as const;

type ReasonCategory = (typeof REASON_CATEGORIES)[number];

const CATEGORY_SEPARATOR = ': ';

/** 自由記述に許す長さ。どの分類を選んでも連結後が列に収まるよう、最長の接頭辞で引く。 */
const DETAIL_MAX_LENGTH =
  REASON_MAX_LENGTH - Math.max(...REASON_CATEGORIES.map(c => c.length)) - CATEGORY_SEPARATOR.length;

interface PointAdjustmentFormValues {
  /** 空欄は NaN。「未入力」と 0 の指定を取り違えないため、valueAsNumber の写像をそのまま持つ。 */
  delta: number;
  reason_category: ReasonCategory | '';
  reason: string;
  /** 'yyyy-MM-dd'。空欄は無期限。 */
  expires_on: string;
}

interface PointAdjustmentDialogProps {
  customerId: string;
  open: boolean;
  onClose: () => void;
  /** 調整の成功後に、応答が持つ調整後の残高を渡す。 */
  onAdjusted: (balance: CustomerPointBalanceResponse) => void;
}

/**
 * 会員ポイントの手動調整ダイアログ。
 *
 * 準金銭的な残高を人手で動かす操作なので、事由は省略できない（台帳の仕訳が誰の何のための
 * 操作か辿れなくなる）。0 の拒否も残高不足の判定も最終的な権威は台帳側にあり、手元の規則は
 * その場で直せる入力を送信の前に返すためのもの。
 */
export function PointAdjustmentDialog({
  customerId,
  open,
  onClose,
  onAdjusted,
}: PointAdjustmentDialogProps) {
  const form = useForm<PointAdjustmentFormValues>({
    defaultValues: { delta: NaN, reason_category: '', reason: '', expires_on: '' },
  });
  const {
    control,
    handleSubmit,
    reset,
    watch,
    formState: { isSubmitting },
  } = form;
  // 有効期限は加算のときだけ意味を持つ。減算に添えるとサーバが 400 を返す
  const isGrant = watch('delta') > 0;

  // 開くたびに 1 回の人間の操作としてキーを発行する。失敗後の再送信・入力変更では変えない —
  // サーバはこのキーで「commit 済みの初回への再送」を見分けて二重記帳を遮断する（ADR 0007）。
  const idempotencyKeyRef = useRef('');

  useEffect(() => {
    if (!open) return;
    idempotencyKeyRef.current = crypto.randomUUID();
    reset({ delta: NaN, reason_category: '', reason: '', expires_on: '' });
  }, [open, reset]);

  const submit = async (values: PointAdjustmentFormValues) => {
    try {
      const balance = await customerApi.adjustPoints(customerId, {
        delta: values.delta,
        reason: values.reason_category + CATEGORY_SEPARATOR + values.reason,
        // 欄が消えても react-hook-form は値を保つ。減算へ切り替えた後の持ち越しを送らないよう、
        // 送信可否は入力の有無ではなく今の増減で決める（undefined は JSON 化の段でキーごと消える）。
        expires_on: isGrant && values.expires_on ? values.expires_on : undefined,
        idempotency_key: idempotencyKeyRef.current,
      });
      notify.success('ポイントを調整しました');
      onAdjusted(balance);
      onClose();
    } catch (error) {
      // 残高不足・未紐づけは、サーバが対処できる文言を返す。汎用文言に潰さない。
      notify.error(getApiErrorMessage(error, 'ポイントの調整に失敗しました'));
    }
  };

  return (
    <Dialog
      open={open}
      onOpenChange={next => {
        // 送信中に閉じると、台帳へ記帳されたか分からないまま古い残高が残る
        if (!next && !isSubmitting) onClose();
      }}
    >
      <DialogContent
        showCloseButton={false}
        aria-describedby={undefined}
        className="gap-0 rounded-[10px] p-0 sm:max-w-md"
      >
        <DialogTitle className="border-b px-6 py-4">ポイント調整</DialogTitle>
        <Form {...form}>
          {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
              我々の文言は永久に描かれない。required は下の規則が引き継ぐ */}
          <form onSubmit={handleSubmit(submit)} className="space-y-4 px-6 py-5" noValidate>
            <FormField
              control={control}
              name="delta"
              rules={{
                required: DELTA_REQUIRED,
                validate: {
                  // 空欄は NaN であって null でも空文字でもないため required は素通りする
                  notEmpty: value => !Number.isNaN(value) || DELTA_REQUIRED,
                  // noValidate は type="number" の暗黙の step=1 も止める。これが無いと
                  // 1.5 が Integer の delta へ届く
                  integer: integerRule('増減ポイント'),
                  // 0 は台帳が撥ねる。往復させずに欄の傍で返す
                  nonZero: value =>
                    Number.isNaN(value) || value !== 0 || '0 以外の増減を入力してください',
                },
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>増減ポイント</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      required
                      {...field}
                      // register の valueAsNumber と同じ写像。Number() は空欄を 0 にしてしまい、
                      // 「未入力」を表す NaN が失われる。
                      value={Number.isNaN(field.value) ? '' : field.value}
                      onChange={event => field.onChange(event.target.valueAsNumber)}
                    />
                  </FormControl>
                  <FormDescription>減算はマイナスで入力します（例: -100）。</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            {/* 分類と自由記述は併存する。分類だけでは「なぜその額か」が残らず、自由記述だけでは
                後から分類で拾えない */}
            <FormField
              control={control}
              name="reason_category"
              rules={{ required: '事由の分類を選択してください' }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>事由の分類</FormLabel>
                  <Select
                    items={REASON_CATEGORIES.map(c => ({ value: c, label: c }))}
                    value={field.value}
                    onValueChange={value => field.onChange(value)}
                    required
                  >
                    <FormControl>
                      <SelectTrigger className="w-full" ref={field.ref}>
                        <SelectValue placeholder="分類を選択" />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {REASON_CATEGORIES.map(category => (
                        <SelectItem key={category} value={category}>
                          {category}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={control}
              name="reason"
              rules={{
                required: '事由を入力してください',
                maxLength: {
                  value: DETAIL_MAX_LENGTH,
                  message: `事由は${DETAIL_MAX_LENGTH}文字以内で入力してください`,
                },
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>事由</FormLabel>
                  <FormControl>
                    <Textarea rows={3} required maxLength={DETAIL_MAX_LENGTH} {...field} />
                  </FormControl>
                  <FormDescription>分類とあわせて台帳の仕訳に残ります。</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            {/* 減算では欄そのものを出さない — 指定できない値の入り口を残さない */}
            {isGrant && (
              <FormField
                control={control}
                name="expires_on"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>有効期限</FormLabel>
                    <FormControl>
                      <Input type="date" {...field} />
                    </FormControl>
                    <FormDescription>空欄なら無期限で付与します。</FormDescription>
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
                {isSubmitting ? '調整中...' : '調整する'}
              </Button>
            </div>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
