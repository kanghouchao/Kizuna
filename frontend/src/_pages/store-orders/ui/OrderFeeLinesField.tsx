'use client';

import { Trash2 } from 'lucide-react';
import { useFieldArray, useFormContext } from 'react-hook-form';
import {
  ORDER_FEE_LINE_KIND_LABELS,
  ORDER_FEE_LINE_STORE_KINDS,
  OrderFeeLine,
  OrderFeeLineInput,
  OrderFeeLineKind,
  feeLinesTotal,
} from '@/entities/order';
import { integerRule } from '@/shared/lib';
import {
  Button,
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
} from '@/shared/ui';

/** 明細を持つフォームの値。呼出側の useForm はこの形を含む。 */
export interface OrderFeeLinesFormValues {
  fee_lines: OrderFeeLineInput[];
}

const AMOUNT_REQUIRED = '金額を入力してください';

const KIND_ITEMS = ORDER_FEE_LINE_STORE_KINDS.map(kind => ({
  value: kind,
  label: ORDER_FEE_LINE_KIND_LABELS[kind],
}));

interface OrderFeeLinesFieldProps {
  /**
   * 完了処理が書いた行（ポイント利用）。店舗は差し替えられないので、金額だけ読めるように並べる。
   * 送信の対象にも入らない。
   */
  systemLines?: OrderFeeLine[];
  /** 適用中のコース名。基本コース料金の行の名称はこの写しから採るため、空だとその種別を選べない。 */
  courseName?: string;
}

/**
 * 受注金額の内訳を行単位で編集する欄。
 *
 * 金額は種別ごとに符号が決まるので、入力は常に正値で受ける（割引に「-」を打たせない）。
 * 合計はここでは持たず、送られた行からサーバが導出する — 画面で合計を打てるようにすると、
 * 内訳と合計が食い違う受注を作れてしまう。
 *
 * ポイント利用の行は台帳の減算仕訳と対で書かれた記録なので、この欄からは触れない。
 */
export function OrderFeeLinesField({ systemLines = [], courseName }: OrderFeeLinesFieldProps) {
  const { control, watch } = useFormContext<OrderFeeLinesFormValues>();
  const { fields, append, remove } = useFieldArray<OrderFeeLinesFormValues, 'fee_lines'>({
    control,
    name: 'fee_lines',
  });
  const lines = watch('fee_lines') ?? [];
  const hasCourseName = (courseName ?? '').trim() !== '';

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <Label>会計内訳</Label>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => append({ kind: 'OPTION', name: '', amount: 0 })}
        >
          明細を追加
        </Button>
      </div>

      {fields.length === 0 && systemLines.length === 0 ? (
        <p className="rounded-lg border border-dashed p-4 text-sm text-muted-foreground">
          明細がありません。「明細を追加」で会計の内訳を入力してください。
        </p>
      ) : (
        <div className="space-y-3">
          {fields.map((field, index) => {
            const kind = lines[index]?.kind ?? 'OPTION';
            const nameFromCourse = kind === 'BASE_COURSE';
            return (
              <div key={field.id} className="flex items-start gap-3 rounded-lg border p-3">
                <div className="grid flex-1 grid-cols-1 gap-3 md:grid-cols-3">
                  <FormField
                    control={control}
                    name={`fee_lines.${index}.kind`}
                    render={({ field: kindField }) => (
                      <FormItem>
                        <FormLabel>種別</FormLabel>
                        <Select
                          items={KIND_ITEMS}
                          value={kindField.value}
                          onValueChange={value => kindField.onChange(value as OrderFeeLineKind)}
                        >
                          <FormControl>
                            <SelectTrigger aria-label={`明細${index + 1}の種別`}>
                              <SelectValue />
                            </SelectTrigger>
                          </FormControl>
                          <SelectContent>
                            {KIND_ITEMS.map(item => (
                              <SelectItem
                                key={item.value}
                                value={item.value}
                                // コース名が無いと行の名称の写し元が無いため、集約が撥ねる
                                disabled={item.value === 'BASE_COURSE' && !hasCourseName}
                              >
                                {item.label}
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
                    name={`fee_lines.${index}.name`}
                    rules={{
                      validate: value =>
                        nameFromCourse ||
                        (value ?? '').trim() !== '' ||
                        '明細の名称を入力してください',
                    }}
                    render={({ field: nameField }) => (
                      <FormItem>
                        <FormLabel>名称</FormLabel>
                        <FormControl>
                          <Input
                            {...nameField}
                            value={nameFromCourse ? (courseName ?? '') : (nameField.value ?? '')}
                            // 基本コース料金の名称はコース名の写し。行の側に別の名前を名乗らせない
                            disabled={nameFromCourse}
                            aria-label={`明細${index + 1}の名称`}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={control}
                    name={`fee_lines.${index}.amount`}
                    rules={{
                      required: AMOUNT_REQUIRED,
                      validate: {
                        notEmpty: value => !Number.isNaN(value) || AMOUNT_REQUIRED,
                        integer: integerRule('金額'),
                        // 手動調整だけが負値を取れる。他は符号を種別が表すので正値で受ける
                        sign: (value, values) =>
                          values.fee_lines[index]?.kind === 'MANUAL_ADJUST' ||
                          Number.isNaN(value) ||
                          value >= 0 ||
                          '金額は 0 以上で入力してください',
                      },
                    }}
                    render={({ field: amountField }) => (
                      <FormItem>
                        <FormLabel>金額</FormLabel>
                        <FormControl>
                          <Input
                            type="number"
                            {...amountField}
                            value={Number.isNaN(amountField.value) ? '' : amountField.value}
                            onChange={event => amountField.onChange(event.target.valueAsNumber)}
                            aria-label={`明細${index + 1}の金額`}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-sm"
                  className="mt-6"
                  onClick={() => remove(index)}
                  aria-label={`明細${index + 1}を削除`}
                >
                  <Trash2 className="h-4 w-4" />
                </Button>
              </div>
            );
          })}
          {systemLines.map((line, index) => (
            <div
              key={`system-${index}`}
              className="flex items-center justify-between rounded-lg border border-dashed p-3 text-sm text-muted-foreground"
            >
              <span>
                {ORDER_FEE_LINE_KIND_LABELS[line.kind]} / {line.name}
              </span>
              <span>-¥{line.amount.toLocaleString()}</span>
            </div>
          ))}
        </div>
      )}

      <p className="text-right text-sm font-medium">
        小計 ¥{feeLinesTotal(lines).toLocaleString()}
      </p>
    </div>
  );
}
