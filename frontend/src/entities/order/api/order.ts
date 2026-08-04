import {
  CursorPageResult,
  CursorParams,
  PageResult,
  PaginationParams,
  apiClient,
  fromCursorPage,
  fromSpringPage,
} from '@/shared/api';
import {
  MemberOrder,
  MemberOrderCreateRequest,
  Order,
  OrderCreateRequest,
  OrderReceptionist,
  ReservationRequestUpdateRequest,
} from '../model/types';

export const orderApi = {
  list: async (
    params?: PaginationParams & { customer_id?: string }
  ): Promise<PageResult<Order>> => {
    const response = await apiClient.get('/store/orders', { params });
    return fromSpringPage(response.data);
  },
  create: async (data: OrderCreateRequest): Promise<Order> => {
    const response = await apiClient.post('/store/orders', data);
    return response.data;
  },
  listReceptionists: async (): Promise<OrderReceptionist[]> => {
    const response = await apiClient.get('/store/orders/receptionists');
    return response.data;
  },
  /**
   * 予約受付 inbox の未確定申請一覧（絞り込みと並びはサーバ側、取得件数は 1 回分に抑える）。
   *
   * 続きは応答の nextCursor をそのまま cursor に渡して取る。確定・謝絶で行が消えても位置がずれないため、
   * 読み込み済みの範囲を読み直さずに済む。
   */
  listReservationRequests: async (params?: CursorParams): Promise<CursorPageResult<Order>> => {
    const response = await apiClient.get('/store/orders/reservation-requests', { params });
    return fromCursorPage(response.data);
  },
  /** 未確定の予約申請を編集する。送った内容がそのまま新しい申請内容になる（省略＝未設定）。 */
  updateReservationRequest: async (
    id: string,
    data: ReservationRequestUpdateRequest
  ): Promise<Order> => {
    const response = await apiClient.put(`/store/orders/reservation-requests/${id}`, data);
    return response.data;
  },
  /** 予約申請を確定する（受注として受け付ける）。 */
  confirm: async (id: string): Promise<Order> => {
    const response = await apiClient.post(`/store/orders/${id}/confirmation`);
    return response.data;
  },
  /** 予約申請を謝絶する。 */
  decline: async (id: string): Promise<Order> => {
    const response = await apiClient.post(`/store/orders/${id}/decline`);
    return response.data;
  },
};

/** 会員本人の予約 API。店舗文脈を要さない（/platform/me 配下）。 */
export const memberOrderApi = {
  list: async (params?: CursorParams): Promise<CursorPageResult<MemberOrder>> => {
    const response = await apiClient.get('/platform/me/orders', { params });
    return fromCursorPage(response.data);
  },
  create: async (data: MemberOrderCreateRequest): Promise<MemberOrder> => {
    const response = await apiClient.post('/platform/me/orders', data);
    return response.data;
  },
  cancel: async (id: string): Promise<MemberOrder> => {
    const response = await apiClient.post(`/platform/me/orders/${id}/cancellation`);
    return response.data;
  },
};
