import { apiClient } from '@/shared/api';

export type MonthlyPdfCriteria =
  | { scope: 'store'; personId: number; month: string }
  | { scope: 'self'; storeId: number; month: string }
  | { scope: 'platform'; storeId: number; personId: number; month: string };

export async function fetchMonthlyPdf(
  criteria: MonthlyPdfCriteria,
  signal: AbortSignal
): Promise<Blob> {
  const path = {
    store: '/store/monthly-remunerations/pdf',
    self: '/platform/me/monthly-remunerations/pdf',
    platform: '/platform/monthly-remunerations/pdf',
  }[criteria.scope];
  try {
    const response = await apiClient.get<Blob>(path, {
      params: {
        month: criteria.month,
        ...('personId' in criteria ? { person_id: criteria.personId } : {}),
        ...('storeId' in criteria ? { store_id: criteria.storeId } : {}),
      },
      responseType: 'blob',
      headers: { Accept: 'application/pdf, application/json' },
      signal,
    });
    if (!response.data.type.startsWith('application/pdf'))
      throw new Error('PDF を取得できませんでした。再試行してください。');
    return response.data;
  } catch (error) {
    if (signal.aborted) throw error;
    const data = (error as { response?: { data?: unknown } }).response?.data;
    if (data instanceof Blob && data.type.includes('application/json')) {
      const body = await data.text();
      let message: unknown;
      try {
        message = JSON.parse(body).error;
      } catch {
        message = null;
      }
      if (typeof message === 'string') throw new Error(message);
    }
    throw new Error('PDF を取得できませんでした。通信状態を確認して再試行してください。');
  }
}
