import type {
  CourseCandidate,
  OrderCorrectionHistoryEntry,
  PlatformOrder,
  SurchargeCandidate,
  OrderPreview,
  SpecialServiceCandidate,
  SpecialServiceRevision,
  OrderSpecialServiceEvent,
} from '../model/types';
import {
  CursorPageResult,
  CursorParams,
  PageResult,
  PaginationParams,
  apiClient,
  fromCursorPage,
  fromSpringPage,
} from '@/shared/api';
import { requireId } from '@/shared/lib';
import {
  GuestOrderApplicationCreateRequest,
  GuestOrderApplicationResponse,
  MemberOrderApplication,
  MemberOrderApplicationCreateRequest,
  MemberReceiptClaim,
  MemberVisit,
  Order,
  OrderApplicationConfirmationRequest,
  OrderApplicationDeclineRequest,
  OrderApplicationRow,
  OrderApplicationStatus,
  OrderAttribution,
  OrderAttributionCorrection,
  OrderAttributionCorrectionRequest,
  OrderAttributionInvalidationRequest,
  OrderArchiveRow,
  OrderCancellationRequest,
  OrderCastCandidate,
  OrderCompletionRequest,
  OrderCompletionResult,
  OrderCorrectionRequest,
  OrderCorrectionResult,
  OrderCreateRequest,
  OrderPointRollbackPreview,
  OrderPointRollbackRequest,
  OrderPointRollbackResult,
  OrderQueryParams,
  OrderReceiptTokenIssue,
  OrderReceptionist,
  OrderSummaryRow,
  OrderUpdateRequest,
  OrderWorkQueueRow,
} from '../model/types';

/**
 * 群読み口の問い合わせ文字列を組む。
 *
 * 状態は繰り返しではなくカンマ区切りの 1 個で送り、空欄の検索語は項目ごと落とす — サーバ側は空欄も
 * 「送っていない」と同じに扱うが、送らない方が「絞り込んでいない」ことが要求そのものから読める。
 */
function toQuery<T extends OrderQueryParams>(params: T): Record<string, unknown> {
  const { statuses, customer_name: customerName, ...rest } = params;
  return {
    ...Object.fromEntries(
      Object.entries(rest).filter(([, value]) => value !== undefined && value !== '')
    ),
    statuses: statuses.join(','),
    ...(customerName ? { customer_name: customerName } : {}),
  };
}

export const orderApi = {
  platformList: async (page: number) => {
    const response = await apiClient.get('/platform/orders', {
      params: { page, size: 20, sort: 'createdAt,desc' },
    });
    return fromSpringPage<PlatformOrder>(response.data);
  },
  correctionHistory: async (scope: 'store' | 'platform', id: string, cursor?: string) => {
    const response = await apiClient.get(`/${scope}/orders/${requireId(id, '受注')}/corrections`, {
      params: { cursor, size: 20 },
    });
    return fromCursorPage<OrderCorrectionHistoryEntry>(response.data);
  },
  surchargeCandidates: async (search: string, page: number) => {
    const response = await apiClient.get('/store/orders/surcharge-candidates', {
      params: { search, page, size: 20 },
    });
    return fromSpringPage<SurchargeCandidate>(response.data);
  },
  surchargeRevisions: async (
    id: string,
    search: string,
    cursor?: string
  ): Promise<{ content: SurchargeCandidate[]; next_cursor?: string }> =>
    (
      await apiClient.get(`/store/orders/${requireId(id, '受注')}/surcharge-revisions`, {
        params: { search, cursor, size: 20 },
      })
    ).data,

  specialServiceCandidates: async (castId: string, search: string, page: number) => {
    const response = await apiClient.get('/store/orders/special-service-candidates', {
      params: { cast_id: castId, search, page, size: 20 },
    });
    return fromSpringPage<SpecialServiceCandidate>(response.data);
  },
  specialServiceRevisions: async (id: string, search: string, cursor?: string) => {
    const response = await apiClient.get(
      `/store/orders/${requireId(id, '受注')}/special-service-revisions`,
      { params: { search, cursor, size: 20 } }
    );
    return fromCursorPage<SpecialServiceRevision>(response.data);
  },
  specialServiceEvents: async (id: string, cursor?: string) => {
    const response = await apiClient.get(
      `/store/orders/${requireId(id, '受注')}/special-service-events`,
      { params: { cursor, size: 20 } }
    );
    return fromCursorPage<OrderSpecialServiceEvent>(response.data);
  },
  start: async (id: string, expectedVersion: number, reason: string): Promise<Order> => {
    const response = await apiClient.post(`/store/orders/${requireId(id, '受注')}/start`, {
      expected_version: expectedVersion,
      reason,
    });
    return response.data;
  },
  courseCandidates: async (search: string, page: number) => {
    const response = await apiClient.get('/store/orders/course-candidates', {
      params: { search, page, size: 20 },
    });
    return fromSpringPage<CourseCandidate>(response.data);
  },
  courseRevisions: async (
    id: string,
    search: string,
    cursor?: string
  ): Promise<{ content: CourseCandidate[]; next_cursor?: string }> => {
    return (
      await apiClient.get(`/store/orders/${requireId(id, '受注')}/course-revisions`, {
        params: { search, cursor, size: 20 },
      })
    ).data;
  },
  previewCreate: async (data: OrderCreateRequest): Promise<OrderPreview> =>
    (await apiClient.post('/store/orders/preview', data)).data,
  previewUpdate: async (id: string | undefined, data: OrderUpdateRequest): Promise<OrderPreview> =>
    (await apiClient.post(`/store/orders/${requireId(id, '受注')}/preview`, data)).data,
  previewCorrection: async (
    id: string | undefined,
    data: OrderCorrectionRequest
  ): Promise<OrderPreview> =>
    (await apiClient.post(`/store/orders/${requireId(id, '受注')}/correction-preview`, data)).data,

  list: async (
    params?: PaginationParams & { customer_id?: string }
  ): Promise<PageResult<OrderSummaryRow>> => {
    const response = await apiClient.get('/store/orders', { params });
    return fromSpringPage(response.data);
  },
  /** 受注 1 件。編集ページは開くたびにここから読み直す（一覧の行を種にすると陳腐化した値で上書きする）。 */
  get: async (id: string | undefined): Promise<Order> => {
    const response = await apiClient.get(`/store/orders/${requireId(id, '受注')}`);
    return response.data;
  },
  create: async (data: OrderCreateRequest): Promise<Order> => {
    const response = await apiClient.post('/store/orders', data);
    return response.data;
  },
  /**
   * 受注の内容を部分更新する。状態は動かせない（完了・取消・確定・謝絶は専用の操作が独占する）。
   *
   * 送るのは直した項目だけでよい（省略＝変更しない）。文字列の項目は空文字を送れば空にできるが、
   * 日付・時刻・数値にその形は無い。
   *
   * 既に設定済みの指名・受付担当だけは例外で、直していなくても毎回運ぶこと — 省略すると「外す」と
   * 区別できないため 400 になる。
   */
  update: async (id: string | undefined, data: OrderUpdateRequest): Promise<OrderWorkQueueRow> => {
    const response = await apiClient.put(`/store/orders/${requireId(id, '受注')}`, data);
    return response.data;
  },
  /**
   * 未完了（CONFIRMED / IN_SERVICE）の受注を理由付きで取消す。理由・実行者・時刻が記録に残り、以後この受注は凍結される。
   *
   * 二度目は逐次・並行とも状態違反（400）で撥ねられる。応答は 204（本体なし）で、
   * 呼出側は行を消すか一覧を取り直す。
   */
  cancel: async (id: string | undefined, data: OrderCancellationRequest): Promise<void> => {
    await apiClient.post(`/store/orders/${requireId(id, '受注')}/cancellation`, data);
  },
  /**
   * 完了受注の明細・実績時刻・コース・特殊サービスを理由付きで訂正する。
   * 項目ごとの省略規則は OrderCorrectionRequest に従い、成功時は訂正前後の採用条件・金額・分数・報酬を返す。
   * ポイント台帳と完了時の付与額は変更しない。
   */
  correct: async (
    id: string | undefined,
    data: OrderCorrectionRequest
  ): Promise<OrderCorrectionResult> => {
    const response = await apiClient.post(
      `/store/orders/${requireId(id, '受注')}/corrections`,
      data
    );
    return response.data;
  },
  /**
   * 作業キュー（対応が要る受注）。状態の群を指定して、検索と並び替えを当てたうえでカーソルで辿る。
   *
   * 続きは応答の nextCursor をそのまま cursor に渡して取る。確定・取消で行が消えても位置がずれない。
   */
  listWorkQueue: async (
    params: OrderQueryParams & CursorParams
  ): Promise<CursorPageResult<OrderWorkQueueRow>> => {
    const response = await apiClient.get('/store/orders/work-queue', {
      params: toQuery(params),
    });
    return fromCursorPage(response.data);
  },
  /**
   * アーカイブ（完了・取消）。作業キューと同じ検索・並び替えを、オフセットのページャで辿る。
   *
   * 位置をページ番号で指せるのは、終端状態の受注が処理で消えず増えるだけだから。
   */
  listArchive: async (
    params: OrderQueryParams & PaginationParams
  ): Promise<PageResult<OrderArchiveRow>> => {
    const response = await apiClient.get('/store/orders/archive', {
      params: toQuery(params),
    });
    return fromSpringPage(response.data);
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
   * 受注を完了する（会計の確定）。ポイントの利用と自動付与が台帳へ入るのはこの経路だけ。
   *
   * 対象は未完了（CONFIRMED / IN_SERVICE）の受注に限られ、それ以外の状態はサーバ側が撥ねる。
   */
  complete: async (
    id: string | undefined,
    data: OrderCompletionRequest
  ): Promise<OrderCompletionResult> => {
    const response = await apiClient.post(
      `/store/orders/${requireId(id, '受注')}/completion`,
      data
    );
    return response.data;
  },
  /**
   * 完了処理の事前計算。付与見込みも利用単位も確定と同じ計算元から引くため、
   * 画面が独自に計算してはならない（設定変更のたびに見込みと結果が食い違う）。
   */
  completionPreview: async (
    id: string | undefined,
    data: OrderCompletionRequest
  ): Promise<OrderPreview> => {
    const response = await apiClient.post(
      `/store/orders/${requireId(id, '受注')}/completion-preview`,
      data
    );
    return response.data;
  },
  /** 受注 1 件の帰属の現況。無効化と再発行のどちらを提示するかはこの読み口で決まる。 */
  attribution: async (id: string | undefined): Promise<OrderAttribution> => {
    const response = await apiClient.get(`/store/orders/${requireId(id, '受注')}/attribution`);
    return response.data;
  },
  /**
   * 帰属記録を理由付きで無効化する（誤帰属の訂正の一段目）。行は削除されず、理由・実行者・時刻が記録に残る。
   *
   * ポイント台帳へは波及しない。誤って付与されたポイントは二段目の訂正で差し引く（ADR 0012）。
   */
  invalidateAttribution: async (
    id: string | undefined,
    data: OrderAttributionInvalidationRequest
  ): Promise<OrderAttribution> => {
    const response = await apiClient.post(
      `/store/orders/${requireId(id, '受注')}/attribution/invalidation`,
      data
    );
    return response.data;
  },
  /**
   * 無効化された受注へ伝票トークンを再発行する。申領期限は再発行から 90 日で数え直される。
   *
   * 生値はこの応答にしか現れない（保存されるのはダイジェストだけ）。
   */
  reissueReceiptToken: async (id: string | undefined): Promise<OrderReceiptTokenIssue> => {
    const response = await apiClient.post(`/store/orders/${requireId(id, '受注')}/receipt-token`);
    return response.data;
  },
  /** 誤帰属の訂正の進み具合。差し引く既定値（付与の全額）と引き残しはここから取る。 */
  attributionCorrection: async (
    id: string | undefined,
    attributionId: number
  ): Promise<OrderAttributionCorrection> => {
    const response = await apiClient.get(
      `/store/orders/${requireId(id, '受注')}/attribution/correction`,
      {
        params: { attribution_id: attributionId },
      }
    );
    return response.data;
  },
  /** ポイント巻き戻しの下見。実行前に動く量を示すためだけの読み口で、台帳へは何も書かない。 */
  pointRollbackPreview: async (id: string | undefined): Promise<OrderPointRollbackPreview> => {
    const response = await apiClient.get(
      `/store/orders/${requireId(id, '受注')}/point-rollback-preview`
    );
    return response.data;
  },
  /**
   * 受注を根拠とするポイントの授受を理由付きで打ち消す（巻き戻し。ADR 0023）。POINT_ADJUST 限定。
   *
   * 二度目は 409。冪等キーは取らない — 受注 1 件につき高々 1 行の操作記録が収束を担う。
   * 巻き戻した受注は伝票トークンの事後申領を永久に拒むため、誤帰属の清掃には使えない。
   */
  pointRollback: async (
    id: string | undefined,
    data: OrderPointRollbackRequest
  ): Promise<OrderPointRollbackResult> => {
    const response = await apiClient.post(
      `/store/orders/${requireId(id, '受注')}/point-rollback`,
      data
    );
    return response.data;
  },
  /**
   * 誤帰属で付いたポイントを、名指した帰属記録が持つ会員から差し引く（訂正の二段目。ADR 0012）。
   *
   * 宛先は帰属記録が持つ会員であって、顧客に現在紐づく会員ではない — 申領で成立した帰属は紐づけを
   * 作らず、完了時の帰属でも紐づけはあとから解除・張り替えされうる。
   */
  correctAttributionPoints: async (
    id: string | undefined,
    data: OrderAttributionCorrectionRequest
  ): Promise<OrderAttributionCorrection> => {
    const response = await apiClient.post(
      `/store/orders/${requireId(id, '受注')}/attribution/correction`,
      data
    );
    return response.data;
  },
};

/** 店舗の予約受付箱 API。申請（OrderApplication）の一覧・確定・謝絶を受け持つ。 */
export const orderApplicationApi = {
  previewConfirmation: async (
    id: string | undefined,
    data: OrderApplicationConfirmationRequest
  ): Promise<OrderPreview> =>
    (
      await apiClient.post(
        `/store/order-applications/${requireId(id, '予約申請')}/confirmation-preview`,
        data
      )
    ).data,

  /**
   * 予約申請の一覧。状態の群を指定してカーソルで辿る（受付箱は PENDING）。
   *
   * 続きは応答の nextCursor をそのまま cursor に渡して取る。確定・謝絶で行が消えても位置がずれない。
   */
  list: async (
    params: { statuses: OrderApplicationStatus[] } & CursorParams
  ): Promise<CursorPageResult<OrderApplicationRow>> => {
    const { statuses, ...rest } = params;
    const response = await apiClient.get('/store/order-applications', {
      params: { ...rest, statuses: statuses.join(',') },
    });
    return fromCursorPage(response.data);
  },
  /**
   * 予約申請を確定する — 確定内容で受注を CONFIRMED で生成し、申請行へ order_id を回写する。
   * 応答は生成された受注（申請の行ではない）。申請原文は不変のまま残る。
   */
  confirm: async (
    id: string | undefined,
    data: OrderApplicationConfirmationRequest
  ): Promise<Order> => {
    const response = await apiClient.post(
      `/store/order-applications/${requireId(id, '予約申請')}/confirmation`,
      data
    );
    return response.data;
  },
  /** 予約申請を理由付きで謝絶する。応答は 204（本体なし）で、呼出側は行を消す。 */
  decline: async (id: string | undefined, data: OrderApplicationDeclineRequest): Promise<void> => {
    await apiClient.post(`/store/order-applications/${requireId(id, '予約申請')}/refusal`, data);
  },
};

/**
 * 公開店面からのゲスト予約申請 API（匿名）。店舗は proxy が焼いた x-mw cookie 由来の
 * 店舗文脈ヘッダで決まるため、申請本体では名乗らない。
 */
export const guestOrderApplicationApi = {
  request: async (
    data: GuestOrderApplicationCreateRequest
  ): Promise<GuestOrderApplicationResponse> => {
    const response = await apiClient.post('/store/order-applications/public', data);
    return response.data;
  },
};

/** 会員本人の予約申請 API。店舗文脈を要さない（/platform/me 配下）。 */
export const memberOrderApplicationApi = {
  list: async (params?: CursorParams): Promise<CursorPageResult<MemberOrderApplication>> => {
    const response = await apiClient.get('/platform/me/order-applications', { params });
    return fromCursorPage(response.data);
  },
  create: async (data: MemberOrderApplicationCreateRequest): Promise<MemberOrderApplication> => {
    const response = await apiClient.post('/platform/me/order-applications', data);
    return response.data;
  },
  /** 未処理の申請を取り下げる（WITHDRAWN）。確定・謝絶の後は 400 で撥ねられる。 */
  withdraw: async (id: string | undefined): Promise<MemberOrderApplication> => {
    const response = await apiClient.post(
      `/platform/me/order-applications/${requireId(id, '予約申請')}/withdrawal`
    );
    return response.data;
  },
};

/** 会員本人の来店履歴 API。申請の追跡（memberOrderApplicationApi）とは別の読み口で、確定した来店だけを返す。 */
export const memberVisitApi = {
  list: async (params?: CursorParams): Promise<CursorPageResult<MemberVisit>> => {
    const response = await apiClient.get('/platform/me/visits', { params });
    return fromCursorPage(response.data);
  },
};

/** 会員本人の伝票トークン申領 API。トークンは本体で送る（パスや問い合わせ文字列はアクセスログに残る）。 */
export const memberReceiptApi = {
  claim: async (token: string): Promise<MemberReceiptClaim> => {
    const response = await apiClient.post('/platform/me/receipts', { token });
    return response.data;
  },
};
