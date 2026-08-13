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
  MemberReceiptClaim,
  MemberVisit,
  Order,
  OrderAttribution,
  OrderAttributionCorrection,
  OrderAttributionCorrectionRequest,
  OrderAttributionInvalidationRequest,
  OrderCastCandidate,
  OrderCompletionPreview,
  OrderCompletionRequest,
  OrderCreateRequest,
  OrderReceiptTokenIssue,
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
  /** 受注 1 件の帰属の現況。無効化と再発行のどちらを提示するかはこの読み口で決まる。 */
  attribution: async (id: string): Promise<OrderAttribution> => {
    const response = await apiClient.get(`/store/orders/${id}/attribution`);
    return response.data;
  },
  /**
   * 帰属記録を理由付きで無効化する（誤帰属の訂正の一段目）。行は削除されず、理由・実行者・時刻が記録に残る。
   *
   * ポイント台帳へは波及しない。誤って付与されたポイントは二段目の訂正で差し引く（ADR 0012）。
   */
  invalidateAttribution: async (
    id: string,
    data: OrderAttributionInvalidationRequest
  ): Promise<OrderAttribution> => {
    const response = await apiClient.post(`/store/orders/${id}/attribution/invalidation`, data);
    return response.data;
  },
  /**
   * 無効化された受注へ伝票トークンを再発行する。申領期限は再発行から 90 日で数え直される。
   *
   * 生値はこの応答にしか現れない（保存されるのはダイジェストだけ）。
   */
  reissueReceiptToken: async (id: string): Promise<OrderReceiptTokenIssue> => {
    const response = await apiClient.post(`/store/orders/${id}/receipt-token`);
    return response.data;
  },
  /** 誤帰属の訂正の進み具合。差し引く既定値（付与の全額）と引き残しはここから取る。 */
  attributionCorrection: async (
    id: string,
    attributionId: number
  ): Promise<OrderAttributionCorrection> => {
    const response = await apiClient.get(`/store/orders/${id}/attribution/correction`, {
      params: { attribution_id: attributionId },
    });
    return response.data;
  },
  /**
   * 誤帰属で付いたポイントを、名指した帰属記録が持つ会員から差し引く（訂正の二段目。ADR 0012）。
   *
   * 宛先は帰属記録が持つ会員であって、顧客に現在紐づく会員ではない — 申領で成立した帰属は紐づけを
   * 作らず、完了時の帰属でも紐づけはあとから解除・張り替えされうる。
   */
  correctAttributionPoints: async (
    id: string,
    data: OrderAttributionCorrectionRequest
  ): Promise<OrderAttributionCorrection> => {
    const response = await apiClient.post(`/store/orders/${id}/attribution/correction`, data);
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

/** 会員本人の伝票トークン申領 API。トークンは本体で送る（パスや問い合わせ文字列はアクセスログに残る）。 */
export const memberReceiptApi = {
  claim: async (token: string): Promise<MemberReceiptClaim> => {
    const response = await apiClient.post('/platform/me/receipts/claim', { token });
    return response.data;
  },
};
