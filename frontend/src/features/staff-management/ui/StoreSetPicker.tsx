'use client';

import { useId, type Ref, type ComponentProps } from 'react';
import { AuthorizedStore, StoreScopeType } from '@/entities/user';
import { Label, RadioGroup, RadioGroupItem } from '@/shared/ui';
import { StoreChecklist } from './StoreChecklist';

interface StoreSetPickerProps extends Pick<
  ComponentProps<'div'>,
  'id' | 'aria-invalid' | 'aria-describedby'
> {
  ref?: Ref<HTMLElement>;
  label?: string;
  stores: AuthorizedStore[];
  isLoading: boolean;
  failed: boolean;
  onReload: () => void;
  storeScopeType: StoreScopeType;
  storeIds: number[];
  onChange: (next: { storeScopeType: StoreScopeType; storeIds: number[] }) => void;
}

/** 授権の店舗集合を「全店舗」と「個別店舗」の択一で編集する。 */
export function StoreSetPicker({
  label = '担当店舗',
  stores,
  isLoading,
  failed,
  onReload,
  storeScopeType,
  storeIds,
  onChange,
  ref,
  ...groupProps
}: StoreSetPickerProps) {
  const labelId = useId();

  return (
    <div role="group" aria-labelledby={labelId} {...groupProps} className="space-y-2">
      <span id={labelId} className="block text-sm font-medium text-foreground">
        {label}
      </span>
      {/* 見出しは外側の組が名乗る。内側は範囲の選び方だけを名乗り、同じ名前を二度読ませない */}
      <RadioGroup
        aria-label="範囲"
        value={storeScopeType}
        onValueChange={value =>
          onChange(
            value === 'ALL_STORES'
              ? { storeScopeType: 'ALL_STORES', storeIds: [] }
              : { storeScopeType: 'SPECIFIC_STORES', storeIds }
          )
        }
        className="gap-2"
      >
        <Label className="min-w-0 font-normal break-words">
          <RadioGroupItem value="ALL_STORES" />
          全店舗
        </Label>
        <Label className="min-w-0 font-normal break-words">
          <RadioGroupItem value="SPECIFIC_STORES" ref={ref} />
          個別店舗
        </Label>
      </RadioGroup>
      {storeScopeType === 'SPECIFIC_STORES' && (
        <StoreChecklist
          stores={stores}
          isLoading={isLoading}
          failed={failed}
          onReload={onReload}
          storeIds={storeIds}
          onChange={nextIds => onChange({ storeScopeType: 'SPECIFIC_STORES', storeIds: nextIds })}
        />
      )}
    </div>
  );
}
