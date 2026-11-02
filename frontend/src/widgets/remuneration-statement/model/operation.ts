import type { AxiosRequestConfig } from 'axios';
import { remunerationApi } from '../api';
import type { Bonus, Guarantee } from './types';
import { memoryOperationStore, useOperation } from '@/shared/lib';

export type EditTarget =
  | { kind: 'guarantee'; item?: Guarantee; version: number; cancel?: boolean }
  | { kind: 'bonus'; item?: Bonus; cancel?: boolean };
export interface EditValues {
  date: string;
  amount: number | undefined;
  stopped: boolean;
  reason: string;
  correction_reason: string;
}
export interface Operation {
  personId: number;
  personName: string;
  target: EditTarget;
  values: EditValues;
}

/** 保証・ボーナスの書き込みは本人・店舗ごとに一つ。金額・理由はブラウザの永続領域に置かない。 */
export function useRemunerationOperation() {
  return useOperation<Operation, void>('remuneration', { store: memoryOperationStore, send });
}

async function send(
  { personId, target, values }: Operation,
  requestId: string,
  config: AxiosRequestConfig
) {
  if (target.cancel && target.item) {
    const body = {
      reason: values.reason.trim(),
      request_id: requestId,
      expected_version: target.kind === 'guarantee' ? target.version : target.item.version,
    };
    if (target.kind === 'guarantee')
      await remunerationApi.cancelGuarantee(target.item.id, body, config);
    else await remunerationApi.cancelBonus(target.item.id, body, config);
  } else if (target.kind === 'guarantee') {
    const body = {
      effective_from: values.date,
      state: values.stopped ? ('STOPPED' as const) : ('ACTIVE' as const),
      daily_amount: values.stopped ? null : (values.amount ?? 0),
      reason: values.reason.trim(),
      request_id: requestId,
      expected_version: target.version,
    };
    if (target.item)
      await remunerationApi.correctGuarantee(
        target.item.id,
        { ...body, correction_reason: values.correction_reason.trim() },
        config
      );
    else await remunerationApi.createGuarantee(personId, body, config);
  } else {
    const body = {
      award_date: values.date,
      amount: values.amount ?? 0,
      reason: values.reason.trim(),
      request_id: requestId,
    };
    if (target.item)
      await remunerationApi.correctBonus(
        target.item.id,
        {
          ...body,
          expected_version: target.item.version,
          correction_reason: values.correction_reason.trim(),
        },
        config
      );
    else await remunerationApi.createBonus(personId, body, config);
  }
}
