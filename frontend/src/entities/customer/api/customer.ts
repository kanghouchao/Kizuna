import type {
  MergeRequest,
  MergePreviewRequest,
  MergePreview,
  MergeAudit,
  ContactPurpose,
  ContactPermissionInput,
} from '../model/types';
import { Page, PageResult, PaginationParams, apiClient, fromSpringPage } from '@/shared/api';
import { requireId } from '@/shared/lib';
import {
  ContactInput,
  ContactResponse,
  ContactHistoryResponse,
  ContactType,
  CustomerCreateRequest,
  CustomerDuplicateGroupResponse,
  CustomerMemberLinkHistoryResponse,
  CustomerMemberLinkResponse,
  CustomerMemberLinkRequest,
  CustomerMemberLinkReleaseRequest,
  CustomerMergeComparisonResponse,
  CustomerMergeHistoryResponse,
  CustomerMergeResponse,
  CustomerPointAdjustmentRequest,
  CustomerPointBalanceResponse,
  CustomerResponse,
  CustomerSummaryResponse,
  CustomerUpdateRequest,
} from '../model/types';

// 一覧のクエリ: 共通ページネーション + classification の絞り込み
export type CustomerListParams = PaginationParams & {
  classification?: string;
};

export const customerApi = {
  mergePreview: async (id: string, request: MergePreviewRequest): Promise<MergePreview> =>
    (await apiClient.post(`/customers/${requireId(id, '顧客')}/merge-preview`, request)).data,
  mergeAudit: async (id: string, mergeId: string): Promise<MergeAudit> =>
    (
      await apiClient.get(
        `/customers/${requireId(id, '顧客')}/merges/${requireId(mergeId, '統合')}`
      )
    ).data,

  contacts: async (id: string, page = 0): Promise<PageResult<ContactResponse>> =>
    fromSpringPage(
      (
        await apiClient.get<Page<ContactResponse>>(`/customers/${requireId(id, '顧客')}/contacts`, {
          params: { page, size: 20 },
        })
      ).data
    ),
  contactHistory: async (id: string, page = 0): Promise<PageResult<ContactHistoryResponse>> =>
    fromSpringPage(
      (
        await apiClient.get<Page<ContactHistoryResponse>>(
          `/customers/${requireId(id, '顧客')}/contact-history`,
          { params: { page, size: 20 } }
        )
      ).data
    ),
  addContact: async (id: string, data: ContactInput): Promise<ContactResponse> =>
    (await apiClient.post(`/customers/${requireId(id, '顧客')}/contacts`, data)).data,
  updateContact: async (
    id: string,
    contactId: string,
    data: ContactInput
  ): Promise<ContactResponse> =>
    (
      await apiClient.put(
        `/customers/${requireId(id, '顧客')}/contacts/${requireId(contactId, '連絡先')}`,
        data
      )
    ).data,
  setContactPermission: async (
    id: string,
    contactId: string,
    purpose: ContactPurpose,
    data: ContactPermissionInput
  ): Promise<ContactResponse> =>
    (
      await apiClient.put(
        `/customers/${requireId(id, '顧客')}/contacts/${requireId(contactId, '連絡先')}/permissions/${purpose}`,
        data
      )
    ).data,
  deleteContact: async (id: string, contactId: string): Promise<void> => {
    await apiClient.delete(
      `/customers/${requireId(id, '顧客')}/contacts/${requireId(contactId, '連絡先')}`
    );
  },
  setContactPreference: async (
    id: string,
    type: ContactType,
    contactId: string | null
  ): Promise<void> => {
    await apiClient.put(`/customers/${requireId(id, '顧客')}/contact-preferences/${type}`, {
      contact_id: contactId,
    });
  },
  /** 顧客一覧を取得する */
  list: async (params?: CustomerListParams): Promise<PageResult<CustomerSummaryResponse>> => {
    const response = await apiClient.get('/customers', { params });
    return fromSpringPage(response.data);
  },
  /**
   * 重複候補（同店・未統合顧客・有効連絡先が一致する 2 行以上のグループ）を取得する。
   * 統合権限が要る読み口で、権限が無ければサーバが 403 を返す。
   */
  duplicates: async (
    page: number,
    filters: { search?: string; type?: ContactType } = {}
  ): Promise<PageResult<CustomerDuplicateGroupResponse>> => {
    const response = await apiClient.get<Page<CustomerDuplicateGroupResponse>>(
      '/customers/duplicates',
      { params: { page, size: 20, ...filters } }
    );
    return fromSpringPage(response.data);
  },
  duplicateCustomers: async (
    filters: { type: ContactType; value: string },
    page = 0
  ): Promise<PageResult<CustomerMergeComparisonResponse>> =>
    fromSpringPage(
      (
        await apiClient.get<Page<CustomerMergeComparisonResponse>>(
          '/customers/duplicates/customers',
          { params: { page, size: 20, ...filters } }
        )
      ).data
    ),
  /**
   * 統合の前に見比べる 2 行を取得する。重複候補に出てこない行どうしでも引ける。
   * 統合権限が要る読み口で、権限が無ければサーバが 403 を返す。
   * 生きた行として引けない ID（統合済み・他店舗・不存在）が混じると 404 で返る。
   */
  mergeComparison: async (
    firstId: string | undefined,
    secondId: string | undefined
  ): Promise<CustomerMergeComparisonResponse[]> => {
    // query は自分で組む。axios の配列 params は既定で `ids[]=` になり、サーバの `ids` に
    // 束縛されない（axios 1.18 の既定は key + '[]'）。キーの綴りを配列表現に委ねない
    const query = [firstId, secondId]
      .map(id => `ids=${encodeURIComponent(requireId(id, '顧客'))}`)
      .join('&');
    const response = await apiClient.get(`/customers/merge-comparison?${query}`);
    return response.data;
  },
  /** 顧客詳細を取得する */
  get: async (id: string | undefined): Promise<CustomerResponse> => {
    const response = await apiClient.get(`/customers/${requireId(id, '顧客')}`);
    return response.data;
  },
  /** 顧客を新規作成する */
  create: async (data: CustomerCreateRequest): Promise<CustomerResponse> => {
    const response = await apiClient.post('/customers', data);
    return response.data;
  },
  /** 顧客情報を更新する */
  update: async (
    id: string | undefined,
    data: CustomerUpdateRequest
  ): Promise<CustomerResponse> => {
    const response = await apiClient.put(`/customers/${requireId(id, '顧客')}`, data);
    return response.data;
  },
  /** 顧客を削除する */
  delete: async (id: string | undefined): Promise<void> => {
    await apiClient.delete(`/customers/${requireId(id, '顧客')}`);
  },
  /**
   * 重複した 2 行を 1 つへまとめる。パスが名指すのが存続行で、本文が被統合行を指す。
   * 取り消せない（ADR 0010）ので、呼び出しは人手の確認を経た後だけ。
   */
  merge: async (
    survivingCustomerId: string | undefined,
    request: MergeRequest
  ): Promise<CustomerMergeResponse> => {
    const response = await apiClient.post(
      `/customers/${requireId(survivingCustomerId, '顧客')}/merges`,
      { ...request, merged_customer_id: requireId(request.merged_customer_id, '顧客') }
    );
    return response.data;
  },
  /**
   * その顧客に関する統合履歴を新しい順に取得する。存続行として受けた統合と、自分が被統合と
   * なった統合の両方が返る。統合権限が要る読み口で、権限が無ければサーバが 403 を返す。
   */
  mergeHistory: async (
    id: string | undefined,
    page = 0
  ): Promise<PageResult<CustomerMergeHistoryResponse>> => {
    const response = await apiClient.get<Page<CustomerMergeHistoryResponse>>(
      `/customers/${requireId(id, '顧客')}/merges`,
      { params: { page, size: 20 } }
    );
    return fromSpringPage(response.data);
  },
  /** 会員コードで会員を顧客へ紐づける（既に別の会員と紐づいていれば付け替える） */
  linkMember: async (
    id: string | undefined,
    request: CustomerMemberLinkRequest
  ): Promise<CustomerMemberLinkResponse> => {
    const response = await apiClient.post(
      `/customers/${requireId(id, '顧客')}/member-link`,
      request
    );
    return response.data;
  },
  /** 会員の紐づけを解除する（履歴は残る） */
  unlinkMember: async (
    id: string | undefined,
    request: CustomerMemberLinkReleaseRequest
  ): Promise<void> => {
    await apiClient.post(`/customers/${requireId(id, '顧客')}/member-link/releases`, request);
  },
  /**
   * 現に有効な会員紐づけを取得する。紐づいていない顧客では 404 で返る — 「紐づいていない」を
   * 本体で表すと、呼出側が読んでからもう一度分岐することになる。
   */
  memberLink: async (id: string | undefined): Promise<CustomerMemberLinkResponse> => {
    const response = await apiClient.get(`/customers/${requireId(id, '顧客')}/member-link`);
    return response.data;
  },
  /** 会員紐づけの履歴を新しい順に取得する。 */
  memberLinkHistory: async (
    id: string | undefined,
    page = 0
  ): Promise<PageResult<CustomerMemberLinkHistoryResponse>> => {
    const response = await apiClient.get<Page<CustomerMemberLinkHistoryResponse>>(
      `/customers/${requireId(id, '顧客')}/member-link/history`,
      { params: { page, size: 20 } }
    );
    return fromSpringPage(response.data);
  },
  /** 紐づく会員のポイント残高を取得する（残高は顧客ではなく会員の台帳が持つ） */
  memberPointBalance: async (id: string | undefined): Promise<CustomerPointBalanceResponse> => {
    const response = await apiClient.get(
      `/customers/${requireId(id, '顧客')}/member-point-balance`
    );
    return response.data;
  },
  /** 会員ポイントを手動で調整し、調整後の残高を受け取る */
  adjustPoints: async (
    id: string | undefined,
    data: CustomerPointAdjustmentRequest
  ): Promise<CustomerPointBalanceResponse> => {
    const response = await apiClient.post(
      `/customers/${requireId(id, '顧客')}/point-adjustments`,
      data
    );
    return response.data;
  },
};
