import { fetchBlob } from '@/shared/api';

export type MonthlyPdfCriteria =
  | { scope: 'store'; personId: number; month: string }
  | { scope: 'self'; storeId: number; month: string };

export function fetchMonthlyPdf(criteria: MonthlyPdfCriteria, signal: AbortSignal): Promise<Blob> {
  const path = {
    store: '/monthly-remunerations/pdf',
    self: '/me/monthly-remunerations/pdf',
  }[criteria.scope];
  return fetchBlob(path, {
    params: {
      month: criteria.month,
      ...('personId' in criteria ? { person_id: criteria.personId } : {}),
      ...('storeId' in criteria ? { store_id: criteria.storeId } : {}),
    },
    signal,
    types: ['application/pdf'],
    fallback: 'PDF を取得できませんでした。通信状態を確認して再試行してください。',
  });
}
