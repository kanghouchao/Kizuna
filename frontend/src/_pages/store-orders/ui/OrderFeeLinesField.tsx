'use client';

import { useState } from 'react';
import { useParams } from 'next/navigation';
import { Trash2Icon } from 'lucide-react';
import { useFieldArray, useFormContext } from 'react-hook-form';
import {
  ORDER_FEE_LINE_KIND_LABELS,
  OrderFeeLine,
  OrderFeeLineInput,
  feeLinesTotal,
  isDeduction,
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
} from '@/shared/ui';
import { OrderSurchargePicker } from './OrderSurchargePicker';

export interface OrderFeeLinesFormValues {
  fee_lines: OrderFeeLineInput[];
}

export function OrderFeeLinesField({
  systemLines = [],
  historicalOrderId,
}: {
  systemLines?: OrderFeeLine[];
  historicalOrderId?: string;
}) {
  const storeId = useParams()?.storeId as string;
  const { control, watch, setValue } = useFormContext<OrderFeeLinesFormValues>();
  const { fields, append, remove, update } = useFieldArray({ control, name: 'fee_lines' });
  const [selection, setSelection] = useState<{ storeId: string; index: number | null }>();
  const lines = watch('fee_lines') ?? [];
  const changed = (index: number) => setValue(`fee_lines.${index}.line_id`, undefined);
  const add = (kind: 'EXTENSION' | 'DISCOUNT' | 'CREDIT_SURCHARGE') =>
    append({
      kind,
      name: kind === 'EXTENSION' ? '延長' : '',
      amount: 0,
      ...(kind === 'EXTENSION' ? { duration_minutes: 30, remuneration: 0 } : {}),
    });
  return (
    <div className="space-y-3">
      <Label>会計内訳</Label>
      <div className="flex flex-wrap gap-2">
        <Button type="button" variant="outline" onClick={() => add('EXTENSION')}>
          延長を追加
        </Button>
        <Button
          type="button"
          variant="outline"
          onClick={() => setSelection({ storeId, index: null })}
        >
          加算を選択
        </Button>
        <Button type="button" variant="outline" onClick={() => add('DISCOUNT')}>
          割引を追加
        </Button>
        <Button type="button" variant="outline" onClick={() => add('CREDIT_SURCHARGE')}>
          クレジット加算を追加
        </Button>
      </div>
      {selection && selection.storeId === storeId && (
        <>
          <OrderSurchargePicker
            key={`${storeId}:${historicalOrderId ?? ''}`}
            historicalOrderId={historicalOrderId}
            onSelect={candidate => {
              const line: OrderFeeLineInput = {
                kind: 'SURCHARGE',
                name: candidate.name,
                amount: candidate.price,
                remuneration: candidate.remuneration,
                revision_number: candidate.revision_number,
                ...(historicalOrderId
                  ? { revision_id: candidate.revision_id }
                  : { service_id: candidate.service_id }),
              };
              if (selection.index === null) append(line);
              else update(selection.index, line);
              setSelection(undefined);
            }}
          />
          <Button type="button" variant="outline" onClick={() => setSelection(undefined)}>
            選択を閉じる
          </Button>
        </>
      )}
      {fields.map((row, index) => {
        const line = lines[index];
        const surcharge = line.kind === 'SURCHARGE';
        return (
          <div key={row.id} className="flex items-start gap-3 rounded-lg border p-3">
            <div className="flex-1 space-y-3">
              <p>
                {ORDER_FEE_LINE_KIND_LABELS[line.kind]}
                {line.line_id ? '（採用済み）' : ''}
              </p>
              {surcharge ? (
                <>
                  <p>
                    {line.name} / 料金 ¥{line.amount.toLocaleString()} / 固定報酬 ¥
                    {line.remuneration?.toLocaleString()} / 版{line.revision_number}
                  </p>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setSelection({ storeId, index })}
                  >
                    加算を選び直す
                  </Button>
                </>
              ) : (
                <div className="grid gap-3 md:grid-cols-2">
                  <FormField
                    control={control}
                    name={`fee_lines.${index}.name`}
                    rules={{
                      validate: value => !!value?.trim() || '明細の名称を入力してください',
                      maxLength: { value: 255, message: '名称は255文字以内で入力してください' },
                    }}
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>名称</FormLabel>
                        <FormControl>
                          <Input
                            {...field}
                            value={field.value ?? ''}
                            onChange={event => {
                              changed(index);
                              field.onChange(event);
                            }}
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
                      required: '金額を入力してください',
                      validate: {
                        integer: integerRule('金額'),
                        notEmpty: value => !Number.isNaN(value) || '金額を入力してください',
                        range: value =>
                          (Number.isFinite(value) &&
                            value >= (line.kind === 'DISCOUNT' ? 1 : 0) &&
                            value <= 2147483647) ||
                          (line.kind === 'DISCOUNT'
                            ? '割引は正の整数円で入力してください'
                            : '金額は0以上の整数円で入力してください'),
                      },
                    }}
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>金額</FormLabel>
                        <FormControl>
                          <Input
                            type="number"
                            min={line.kind === 'DISCOUNT' ? 1 : 0}
                            step={1}
                            {...field}
                            value={Number.isNaN(field.value) ? '' : field.value}
                            onChange={event => {
                              changed(index);
                              field.onChange(event.target.valueAsNumber);
                            }}
                            aria-label={`明細${index + 1}の金額`}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  {line.kind === 'EXTENSION' && (
                    <>
                      <FormField
                        control={control}
                        name={`fee_lines.${index}.duration_minutes`}
                        rules={{
                          required: '分数を入力してください',
                          validate: value =>
                            (Number.isInteger(value) && value! > 0 && value! <= 2147483647) ||
                            '分数は正の整数で入力してください',
                        }}
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>分数</FormLabel>
                            <FormControl>
                              <Input
                                type="number"
                                min={1}
                                step={1}
                                {...field}
                                value={Number.isNaN(field.value) ? '' : (field.value ?? '')}
                                onChange={event => {
                                  changed(index);
                                  field.onChange(event.target.valueAsNumber);
                                }}
                                aria-label={`明細${index + 1}の分数`}
                              />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                      <FormField
                        control={control}
                        name={`fee_lines.${index}.remuneration`}
                        rules={{
                          required: '固定報酬を入力してください',
                          validate: {
                            integer: value =>
                              (Number.isInteger(value) && value! >= 0) ||
                              '固定報酬は0以上の整数円で入力してください',
                            limit: value =>
                              value! <= lines[index].amount ||
                              '固定報酬は顧客費用以下で入力してください',
                          },
                        }}
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>固定報酬</FormLabel>
                            <FormControl>
                              <Input
                                type="number"
                                min={0}
                                step={1}
                                {...field}
                                value={Number.isNaN(field.value) ? '' : (field.value ?? '')}
                                onChange={event => {
                                  changed(index);
                                  field.onChange(event.target.valueAsNumber);
                                }}
                                aria-label={`明細${index + 1}の固定報酬`}
                              />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </>
                  )}
                </div>
              )}
            </div>
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              onClick={() => {
                setSelection(undefined);
                remove(index);
              }}
              aria-label={`明細${index + 1}を削除`}
            >
              <Trash2Icon className="size-4" />
            </Button>
          </div>
        );
      })}
      {systemLines.map((line, index) => (
        <p key={index} className="rounded-lg border p-3 text-sm">
          {ORDER_FEE_LINE_KIND_LABELS[line.kind]} / {line.name}: {isDeduction(line.kind) ? '-' : ''}
          ¥{line.amount.toLocaleString()} / 固定報酬 ¥{line.remuneration.toLocaleString()}
        </p>
      ))}
      <p className="text-right text-sm font-medium">
        小計 ¥{feeLinesTotal(lines).toLocaleString()}
      </p>
    </div>
  );
}
