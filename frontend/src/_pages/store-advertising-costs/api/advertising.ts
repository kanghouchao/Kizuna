import { apiClient, type Page, type CursorPage } from '@/shared/api';
import { getStoreIdFromPath, readTokenClaims } from '@/shared/lib';
export type Category = 'SALES' | 'RECRUITMENT';
export interface CostValues {
  category: Category;
  media_name: string;
  agency_name: string | null;
  plan_name: string | null;
  inquiry_count: number | null;
  amount: number;
}
export interface Cost extends CostValues {
  id: string;
  store_id: number;
  month: string;
  version: number;
  created_at?: string;
  updated_at: string;
}
export interface MediaSummary {
  category: Category;
  media_name: string;
  entry_count: number;
  recorded_amount: number;
  recorded_inquiry_entry_count: number;
  unrecorded_inquiry_entry_count: number;
  recorded_inquiry_count_sum: number | null;
  inquiry_status: 'UNRECORDED' | 'PARTIAL' | 'RECORDED';
}
export interface MediaReport {
  store_id: number;
  month: string;
  month_version: number;
  generated_at: string;
  basis: 'advertising-media-records-v1';
  media_matching: 'EXACT_STORED_NAME';
  inquiry_basis: 'MANUAL_RECORDED_SUM_NOT_DEDUPLICATED';
  entry_count: number;
  recorded_total_amount: number;
  category_totals: { category: Category; entry_count: number; recorded_amount: number }[];
  rows: Page<MediaSummary>;
}
export type ExportKind = 'costs' | 'media' | 'orders';
export const advertisingExports = {
  costs: { resource: 'advertising-costs', label: '広告費出力' },
  media: { resource: 'advertising-media-summaries', label: '媒体別集計出力' },
  orders: { resource: 'advertising-order-costs', label: '受注あたり記録広告費出力' },
} satisfies Record<ExportKind, { resource: string; label: string }>;
export interface OrderCostSummary {
  media_name: string;
  cost_entry_count: number;
  recorded_sales_amount: number | null;
  valid_completed_order_count: number;
  zero_amount_order_count: number;
  cost_per_order: string | null;
  calculation_status: 'CALCULATED' | 'NO_COST_RECORDS' | 'NO_VALID_ORDERS';
}
export interface OrderCostReport {
  store_id: number;
  month: string;
  month_version: number;
  generated_at: string;
  basis: 'recorded-sales-cost-per-valid-order-v1';
  media_matching: 'EXACT_STORED_NAME';
  order_basis: 'VALID_COMPLETED_ORIGINAL_BUSINESS_DATE_INCLUDING_ZERO';
  rounding: 'HALF_UP_2_DECIMAL_YEN';
  cost_entry_count: number;
  recorded_sales_amount: number | null;
  valid_completed_order_count: number;
  zero_amount_order_count: number;
  unnamed_media_order_count: number;
  rows: Page<OrderCostSummary>;
}
export interface Month {
  store_id: number;
  month: string;
  version: number;
  entry_count: number;
  sales_amount: number;
  recruitment_amount: number;
  recorded_total_amount: number;
  generated_at: string;
}
export interface Change {
  id: string;
  cost_id: string;
  action: 'CREATED' | 'UPDATED' | 'DELETED' | 'COPIED';
  actor_id: number;
  occurred_at: string;
  version_before: number | null;
  version_after: number | null;
}
export interface ChangeDetail extends Change {
  reason: string | null;
  source_cost_id: string | null;
  before: Cost | null;
  after: Cost | null;
}
export interface CopyResult {
  id: string;
  store_id: number;
  source_month: string;
  target_month: string;
  copied_count: number;
  source_version: number;
  target_version: number;
  created_at: string;
}
export type Mutation =
  | { kind: 'create'; values: CostValues }
  | { kind: 'replace'; id: string; version: number; values: CostValues; reason: string }
  | { kind: 'delete'; id: string; version: number; reason: string }
  | { kind: 'copy'; source_version: number; target_version: number; reason: string };
export interface Operation {
  subject: string;
  store: string;
  month: string;
  request_id: string;
  mutation: Mutation;
}
const base = '/store/advertising-costs';
const months = '/store/advertising-cost-months';
export const advertisingApi = {
  orderCosts: async (month: string, page = 0) =>
    (
      await apiClient.get<OrderCostReport>('/store/advertising-order-costs', {
        params: { month, page, size: 20 },
      })
    ).data,
  media: async (month: string, page = 0) =>
    (
      await apiClient.get<MediaReport>('/store/advertising-media-summaries', {
        params: { month, page, size: 20 },
      })
    ).data,
  list: async (month: string, page = 0) =>
    (await apiClient.get<Page<Cost>>(base, { params: { month, page, size: 20 } })).data,
  get: async (id: string) => (await apiClient.get<Cost>(`${base}/${id}`)).data,
  month: async (month: string) => (await apiClient.get<Month>(`${months}/${month}`)).data,
  changes: async (month: string, cursor?: string) =>
    (
      await apiClient.get<CursorPage<Change>>(`${months}/${month}/changes`, {
        params: { cursor, size: 20 },
      })
    ).data,
  change: async (month: string, id: string) =>
    (await apiClient.get<ChangeDetail>(`${months}/${month}/changes/${id}`)).data,
  async mutate(op: Operation) {
    const m = op.mutation;
    const body =
      m.kind === 'create'
        ? { ...m.values, month: op.month, request_id: op.request_id }
        : m.kind === 'replace'
          ? { ...m.values, version: m.version, reason: m.reason, request_id: op.request_id }
          : m.kind === 'delete'
            ? { version: m.version, reason: m.reason, request_id: op.request_id }
            : {
                source_version: m.source_version,
                target_version: m.target_version,
                reason: m.reason,
                request_id: op.request_id,
              };
    return apiClient.request({
      method: m.kind === 'replace' ? 'PUT' : m.kind === 'delete' ? 'DELETE' : 'POST',
      url:
        m.kind === 'copy' ? `${months}/${op.month}/copies` : 'id' in m ? `${base}/${m.id}` : base,
      data: body,
      transformRequest: [
        (data, headers) => {
          if (
            readTokenClaims()?.subject !== op.subject ||
            getStoreIdFromPath(window.location.pathname) !== op.store
          )
            throw new Error('元の店舗に戻って操作結果を確認してください。');
          headers['X-Role'] = 'store';
          headers['X-Store-ID'] = op.store;
          return JSON.stringify(data);
        },
      ],
    });
  },
  async download(
    month: string,
    format: 'csv' | 'xlsx',
    signal: AbortSignal,
    kind: ExportKind = 'costs'
  ) {
    try {
      const response = await apiClient.get<Blob>(
        '/store/' + advertisingExports[kind].resource + '/exports',
        {
          params: { month, format },
          responseType: 'blob',
          signal,
        }
      );
      const type =
        format === 'csv'
          ? 'text/csv'
          : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
      if (!response.data.type.startsWith(type)) throw new Error('出力形式を確認できませんでした。');
      return response.data;
    } catch (error) {
      if (signal.aborted) throw error;
      const data = (error as { response?: { data?: unknown } }).response?.data;
      if (data instanceof Blob && data.type.includes('application/json')) {
        let message: unknown;
        try {
          message = JSON.parse(await data.text()).error;
        } catch {
          message = null;
        }
        if (typeof message === 'string') throw new Error(message);
      }
      throw new Error('広告費を出力できませんでした。権限と通信状態を確認してください。');
    }
  },
};
export const categoryLabels: Record<Category, string> = {
  SALES: '営業広告',
  RECRUITMENT: '採用広告',
};
export function validMonth(month: string) {
  return /^(?!0000)[0-9]{4}-(0[1-9]|1[0-2])$/.test(month);
}
export function previousMonth(month: string) {
  if (!validMonth(month) || month === '0001-01') return null;
  const [year, part] = month.split('-').map(Number);
  return `${String(part === 1 ? year - 1 : year).padStart(4, '0')}-${String(part === 1 ? 12 : part - 1).padStart(2, '0')}`;
}
