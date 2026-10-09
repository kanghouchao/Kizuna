import { apiClient, type Page } from '@/shared/api';

export interface ReportCriteria {
  from: string;
  to: string;
  group_by: 'day' | 'month' | 'store';
  store_id?: number;
  include_remuneration?: boolean;
  include_advertising?: boolean;
}
export interface ReportRemuneration {
  known_guarantee_total: number;
  guarantee_total: number | null;
  bonus_total: number;
  total: number | null;
  pending_attendance_days: number;
  not_configured_days: number;
}
export interface ReportAdvertising {
  status:
    'RECORDED' | 'NO_RECORDS' | 'NOT_APPLICABLE_PARTIAL_MONTH' | 'NOT_APPLICABLE_DAY_GROUPING';
  entry_count: number | null;
  sales_amount: number | null;
  recruitment_amount: number | null;
  recorded_total_amount: number | null;
}
export interface ReportRow {
  store_id: number;
  store_name: string;
  period: string;
  order_count: number;
  invalidated_order_count: number;
  total_fee: number;
  total_remuneration: number;
  remuneration?: ReportRemuneration;
  advertising?: ReportAdvertising;
}
export interface OperationalReport extends ReportCriteria {
  generated_at: string;
  basis: string;
  stores: { store_id: number; store_name: string }[];
  total_order_count: number;
  invalidated_order_count: number;
  total_fee: number;
  total_remuneration: number;
  remuneration?: ReportRemuneration;
  advertising?: ReportAdvertising;
  rows: Page<ReportRow>;
}
export type ReportScope = 'store' | 'platform';
export async function fetchReport(scope: ReportScope, criteria: ReportCriteria, page: number) {
  return (
    await apiClient.get<OperationalReport>(`/${scope}/operational-reports`, {
      params: { ...criteria, page, size: 20 },
    })
  ).data;
}
export async function downloadReport(
  scope: ReportScope,
  criteria: ReportCriteria,
  format: 'csv' | 'xlsx',
  signal: AbortSignal
): Promise<Blob> {
  try {
    const response = await apiClient.get<Blob>(`/${scope}/operational-reports/exports`, {
      params: { ...criteria, format },
      responseType: 'blob',
      signal,
    });
    const type =
      format === 'csv'
        ? 'text/csv'
        : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
    if (!response.data.type.startsWith(type)) throw new Error();
    return response.data;
  } catch (failure) {
    if (signal.aborted) throw failure;
    const data = (failure as { response?: { data?: unknown } }).response?.data;
    if (data instanceof Blob && data.type.includes('application/json')) {
      let message: unknown;
      try {
        message = JSON.parse(await data.text()).error;
      } catch {
        message = null;
      }
      if (typeof message === 'string') throw new Error(message);
    }
    throw new Error('帳票を取得できませんでした。条件または通信状態を確認してください。');
  }
}
export function validReportPeriod(from: string, to: string): boolean {
  if (![from, to].every(value => /^(?!0000)[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(value))) return false;
  const start = new Date(from),
    end = new Date(to);
  if (!Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime())) return false;
  return (
    start.toISOString().slice(0, 10) === from &&
    end.toISOString().slice(0, 10) === to &&
    end.getTime() >= start.getTime() &&
    end.getTime() - start.getTime() < 366 * 86400000
  );
}
