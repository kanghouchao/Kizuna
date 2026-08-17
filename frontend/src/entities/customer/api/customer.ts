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
  CustomerCreateRequest,
  CustomerDuplicateCandidatesResponse,
  CustomerMemberLinkHistoryResponse,
  CustomerMemberLinkResponse,
  CustomerMergeResponse,
  CustomerPointAdjustmentRequest,
  CustomerPointBalanceResponse,
  CustomerResponse,
  CustomerSummaryResponse,
  CustomerUpdateRequest,
} from '../model/types';

// 一覧のクエリ: 共通ページネーション + rank / classification の絞り込み
export type CustomerListParams = PaginationParams & {
  rank?: string;
  classification?: string;
};

export const customerApi = {
  /** 顧客一覧を取得する */
  list: async (params?: CustomerListParams): Promise<PageResult<CustomerSummaryResponse>> => {
    const response = await apiClient.get('/store/customers', { params });
    return fromSpringPage(response.data);
  },
  /**
   * 重複候補（同店・生きた行・第一電話番号が一致する 2 行以上のグループ）を取得する。
   * 統合権限が要る読み口で、権限が無ければサーバが 403 を返す。
   */
  duplicates: async (): Promise<CustomerDuplicateCandidatesResponse> => {
    const response = await apiClient.get('/store/customers/duplicates');
    return response.data;
  },
  /** 顧客詳細を取得する */
  get: async (id: string): Promise<CustomerResponse> => {
    const response = await apiClient.get(`/store/customers/${id}`);
    return response.data;
  },
  /** 顧客を新規作成する */
  create: async (data: CustomerCreateRequest): Promise<CustomerResponse> => {
    const response = await apiClient.post('/store/customers', data);
    return response.data;
  },
  /** 顧客情報を更新する */
  update: async (id: string, data: CustomerUpdateRequest): Promise<CustomerResponse> => {
    const response = await apiClient.put(`/store/customers/${id}`, data);
    return response.data;
  },
  /** 顧客を削除する */
  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/store/customers/${id}`);
  },
  /**
   * 重複した 2 行を 1 つへまとめる。パスが名指すのが存続行で、本文が被統合行を指す。
   * 取り消せない（ADR 0010）ので、呼び出しは人手の確認を経た後だけ。
   */
  merge: async (
    survivingCustomerId: string,
    mergedCustomerId: string
  ): Promise<CustomerMergeResponse> => {
    const response = await apiClient.post(`/store/customers/${survivingCustomerId}/merges`, {
      merged_customer_id: mergedCustomerId,
    });
    return response.data;
  },
  /** 会員コードで会員を顧客へ紐づける（既に別の会員と紐づいていれば付け替える） */
  linkMember: async (id: string, memberCode: string): Promise<CustomerMemberLinkResponse> => {
    const response = await apiClient.post(`/store/customers/${id}/member-link`, {
      member_code: memberCode,
    });
    return response.data;
  },
  /** 会員の紐づけを解除する（履歴は残る） */
  unlinkMember: async (id: string): Promise<void> => {
    await apiClient.delete(`/store/customers/${id}/member-link`);
  },
  /**
   * 現に有効な会員紐づけを取得する。紐づいていない顧客では 404 で返る — 「紐づいていない」を
   * 本体で表すと、呼出側が読んでからもう一度分岐することになる。
   */
  memberLink: async (id: string): Promise<CustomerMemberLinkResponse> => {
    const response = await apiClient.get(`/store/customers/${id}/member-link`);
    return response.data;
  },
  /** 会員紐づけの履歴を新しい順に取得する。続きは応答の nextCursor をそのまま cursor に渡して取る。 */
  memberLinkHistory: async (
    id: string,
    params?: CursorParams
  ): Promise<CursorPageResult<CustomerMemberLinkHistoryResponse>> => {
    const response = await apiClient.get(`/store/customers/${id}/member-link/history`, { params });
    return fromCursorPage(response.data);
  },
  /** 紐づく会員のポイント残高を取得する（残高は顧客ではなく会員の台帳が持つ） */
  memberPointBalance: async (id: string): Promise<CustomerPointBalanceResponse> => {
    const response = await apiClient.get(`/store/customers/${id}/member-point-balance`);
    return response.data;
  },
  /** 会員ポイントを手動で調整し、調整後の残高を受け取る */
  adjustPoints: async (
    id: string,
    data: CustomerPointAdjustmentRequest
  ): Promise<CustomerPointBalanceResponse> => {
    const response = await apiClient.post(`/store/customers/${id}/point-adjustments`, data);
    return response.data;
  },
};
