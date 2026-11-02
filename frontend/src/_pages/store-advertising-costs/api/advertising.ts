import type { AxiosRequestConfig } from 'axios';
import { apiClient, type Page } from '@/shared/api';
export type Category = 'SALES' | 'RECRUITMENT';
export interface MediumRef {
  id: string;
  name: string;
}
interface CostFields {
  category: Category;
  agency_name: string | null;
  plan_name: string | null;
  inquiry_count: number | null;
  amount: number;
}
export interface CostValues extends CostFields {
  medium_id: string;
}
/** medium.name は一覧・詳細では現在名、変更履歴の前後ではその時点の名前。 */
export interface Cost extends CostFields {
  id: string;
  store_id: number;
  month: string;
  medium: MediumRef;
  version: number;
  created_at?: string;
  updated_at: string;
}
export interface MediaSummary {
  category: Category;
  medium: MediumRef | null;
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
  media_matching: 'MEDIUM_ID';
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
  medium: MediumRef | null;
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
  media_matching: 'MEDIUM_ID';
  order_basis: 'VALID_COMPLETED_ORIGINAL_BUSINESS_DATE_INCLUDING_ZERO';
  rounding: 'HALF_UP_2_DECIMAL_YEN';
  cost_entry_count: number;
  recorded_sales_amount: number | null;
  valid_completed_order_count: number;
  zero_amount_order_count: number;
  unnamed_media_order_count: number;
  /** リピート・紹介・その他の経路で知った有効完了受注の件数（媒体への割当てと単価算出の対象外）。 */
  other_route_order_count: number;
  rows: Page<OrderCostSummary>;
}
export interface RecruitmentRetention {
  months: 2 | 3 | 4;
  judgeable_count: number;
  retained_count: number;
}
export interface RecruitmentEffectSummary {
  medium: MediumRef | null;
  cost_entry_count: number;
  recorded_recruitment_amount: number | null;
  hire_count: number;
  cost_per_hire: string | null;
  calculation_status: 'CALCULATED' | 'NO_COST_RECORDS' | 'NO_HIRES';
  retention: RecruitmentRetention[];
}
export interface RecruitmentEffectReport {
  store_id: number;
  month: string;
  month_version: number;
  generated_at: string;
  retention_as_of: string;
  basis: 'recorded-recruitment-cost-per-hire-v1';
  media_matching: 'MEDIUM_ID';
  hire_basis: 'HIRED_APPLICANT_ENROLLMENT_STARTED_IN_MONTH';
  retention_basis: 'NOT_ENDED_AFTER_STARTED_ON_PLUS_MONTHS';
  rounding: 'HALF_UP_2_DECIMAL_YEN';
  cost_entry_count: number;
  recorded_recruitment_amount: number | null;
  hire_count: number;
  unnamed_media_hire_count: number;
  rows: Page<RecruitmentEffectSummary>;
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
const base = '/advertising-costs';
const months = '/advertising-cost-months';
export const advertisingApi = {
  orderCosts: async (month: string, page = 0) =>
    (
      await apiClient.get<OrderCostReport>('/advertising-order-costs', {
        params: { month, page, size: 20 },
      })
    ).data,
  recruitmentEffects: async (month: string, page = 0) =>
    (
      await apiClient.get<RecruitmentEffectReport>('/advertising-recruitment-effects', {
        params: { month, page, size: 20 },
      })
    ).data,
  media: async (month: string, page = 0) =>
    (
      await apiClient.get<MediaReport>('/advertising-media-summaries', {
        params: { month, page, size: 20 },
      })
    ).data,
  list: async (month: string, page = 0) =>
    (await apiClient.get<Page<Cost>>(base, { params: { month, page, size: 20 } })).data,
  get: async (id: string) => (await apiClient.get<Cost>(`${base}/${id}`)).data,
  month: async (month: string) => (await apiClient.get<Month>(`${months}/${month}`)).data,
  changes: async (month: string, page = 0) =>
    (
      await apiClient.get<Page<Change>>(`${months}/${month}/changes`, {
        params: { page, size: 20 },
      })
    ).data,
  change: async (month: string, id: string) =>
    (await apiClient.get<ChangeDetail>(`${months}/${month}/changes/${id}`)).data,
  async mutate(month: string, m: Mutation, requestId: string, config?: AxiosRequestConfig) {
    const body =
      m.kind === 'create'
        ? { ...m.values, month, request_id: requestId }
        : m.kind === 'replace'
          ? { ...m.values, version: m.version, reason: m.reason, request_id: requestId }
          : m.kind === 'delete'
            ? { version: m.version, reason: m.reason, request_id: requestId }
            : {
                source_version: m.source_version,
                target_version: m.target_version,
                reason: m.reason,
                request_id: requestId,
              };
    return apiClient.request({
      ...config,
      method: m.kind === 'replace' ? 'PUT' : m.kind === 'delete' ? 'DELETE' : 'POST',
      url: m.kind === 'copy' ? `${months}/${month}/copies` : 'id' in m ? `${base}/${m.id}` : base,
      data: body,
    });
  },
  async download(
    month: string,
    format: 'csv' | 'xlsx',
    signal: AbortSignal,
    kind: ExportKind = 'costs'
  ) {
    try {
      const response = await apiClient.get<Blob>(`/${advertisingExports[kind].resource}/exports`, {
        params: { month, format },
        responseType: 'blob',
        signal,
      });
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
/** 集計行の媒体は契約上 null を取りうる。空欄にせず未入力と示す。 */
export function mediumName(medium: MediumRef | null) {
  return medium?.name ?? '媒体未入力';
}
