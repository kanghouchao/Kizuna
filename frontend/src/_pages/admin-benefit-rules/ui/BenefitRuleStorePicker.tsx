'use client';

import { useId } from 'react';
import type { BenefitRuleStoreScopeType } from '@/entities/benefit-rule';
import { AuthorizedStore } from '@/entities/user';
import { StoreChecklist } from '@/features/staff-management';
import { Label, RadioGroup, RadioGroupItem } from '@/shared/ui';

interface BenefitRuleStorePickerProps {
  stores: AuthorizedStore[];
  isLoading: boolean;
  failed: boolean;
  onReload: () => void;
  storeScopeType: BenefitRuleStoreScopeType;
  storeIds: number[];
  onChange: (next: { storeScopeType: BenefitRuleStoreScopeType; storeIds: number[] }) => void;
}

/** 特典規則の適用店舗（発火を拾う範囲）を「全店舗」と「個別店舗」の択一で選ぶ。 */
export function BenefitRuleStorePicker({
  stores,
  isLoading,
  failed,
  onReload,
  storeScopeType,
  storeIds,
  onChange,
}: BenefitRuleStorePickerProps) {
  const labelId = useId();

  return (
    <div role="group" aria-labelledby={labelId} className="space-y-2">
      <span id={labelId} className="block text-sm font-medium text-foreground">
        適用店舗（発火を拾う範囲）
      </span>
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
          <RadioGroupItem value="SPECIFIC_STORES" />
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
