'use client';

import { useFormContext, useWatch } from 'react-hook-form';
import { MediumSelect } from '@/entities/medium';
import { ACQUISITION_SOURCE_LABELS, type OrderMedium } from '@/entities/order';
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui';

export interface AcquisitionValues {
  /** 空文字は未入力。 */
  acquisition_source: string;
  medium_id: string;
}

const UNSET = '__none__';
const SOURCE_ITEMS = Object.entries(ACQUISITION_SOURCE_LABELS).map(([value, label]) => ({
  value,
  label,
}));

/**
 * 知った経路と、経路が媒体のときの媒体。
 *
 * 更新の契約は経路を省略すると「変えない」で、記録済みの経路を未入力へ戻す値を持たない。そのため経路を持つ受注の編集では
 * 「未入力」を選択肢に出さない（allowUnset=false）— 選べても送れない値を見せない。
 */
export function OrderAcquisitionFields({
  allowUnset,
  currentMedium,
}: {
  allowUnset: boolean;
  currentMedium?: OrderMedium | null;
}) {
  const { control, setValue } = useFormContext<AcquisitionValues>();
  const source = useWatch({ control, name: 'acquisition_source' });
  const items = allowUnset ? [{ value: UNSET, label: '未入力' }, ...SOURCE_ITEMS] : SOURCE_ITEMS;
  return (
    <div className="grid grid-cols-2 gap-6">
      <FormField
        control={control}
        name="acquisition_source"
        render={({ field }) => (
          <FormItem>
            <FormLabel>知った経路</FormLabel>
            <Select
              items={items}
              value={field.value || (allowUnset ? UNSET : null)}
              onValueChange={v => {
                const next = v === UNSET || v == null ? '' : v;
                field.onChange(next);
                if (next !== 'MEDIA') setValue('medium_id', '');
              }}
            >
              <FormControl>
                <SelectTrigger ref={field.ref} className="w-full">
                  <SelectValue placeholder="経路を選択" />
                </SelectTrigger>
              </FormControl>
              <SelectContent>
                {items.map(o => (
                  <SelectItem key={o.value} value={o.value}>
                    {o.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </FormItem>
        )}
      />
      {source === 'MEDIA' && (
        <FormField
          control={control}
          name="medium_id"
          rules={{ required: '媒体を選択してください' }}
          render={({ field }) => (
            <FormItem className="min-w-0">
              <FormLabel>知った媒体</FormLabel>
              <MediumSelect
                value={field.value}
                onChange={field.onChange}
                current={currentMedium}
                triggerRef={field.ref}
              />
              <FormMessage />
            </FormItem>
          )}
        />
      )}
    </div>
  );
}
