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
  MemberVisit,
  Order,
  OrderCastCandidate,
  OrderCompletionPreview,
  OrderCompletionRequest,
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
   * 指名候補の一覧（当店に在籍中のキャストを名前で絞り込む）。件数上限と並びはサーバ側が固定する。
   *
   * キャスト管理の一覧ではなくこの読み口を使うのは、受注権限だけで引けること・在籍停止が混ざらないことの
   * 両方がここでしか成り立たないため。
   */
  listCastCandidates: async (params?: { search?: string }): Promise<OrderCastCandidate[]> => {
    const response = await apiClient.get('/store/orders/cast-candidates', { params });
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
  /**
   * 受注を完了する（会計の確定）。ポイントの利用と自動付与が台帳へ入るのはこの経路だけ。
   *
   * 対象は確定済みの受注に限られ、それ以外の状態はサーバ側が撥ねる。
   */
  complete: async (id: string, data: OrderCompletionRequest): Promise<Order> => {
    const response = await apiClient.post(`/store/orders/${id}/completion`, data);
    return response.data;
  },
  /**
   * 完了処理の事前計算。付与見込みも利用単位も確定と同じ計算元から引くため、
   * 画面が独自に計算してはならない（設定変更のたびに見込みと結果が食い違う）。
   */
  completionPreview: async (id: string, totalFee: number): Promise<OrderCompletionPreview> => {
    const response = await apiClient.get(`/store/orders/${id}/completion-preview`, {
      params: { total_fee: totalFee },
    });
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

/** 会員本人の来店履歴 API。申請の追跡（memberOrderApi）とは別の読み口で、確定した来店だけを返す。 */
export const memberVisitApi = {
  list: async (params?: CursorParams): Promise<CursorPageResult<MemberVisit>> => {
    const response = await apiClient.get('/platform/me/visits', { params });
    return fromCursorPage(response.data);
  },
};
