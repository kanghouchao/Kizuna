import { apiClient, PageResult, fromSpringPage, toSpringPageParams } from '@/shared/api';
import {
  AdminMemberDetail,
  AdminMemberLedgerEntry,
  AdminMemberSummary,
  MemberStatusFilter,
  StaffWithdrawalRequest,
} from '../model/types';

const BASE_URL = '/members';

/** 職員の会員コンソール API（照会は MEMBER_VIEW、代行退会は MEMBER_WITHDRAW）。 */
export const adminMemberApi = {
  /** 一覧。状態の既定は有効で、検索語は会員コード・電子メール・表示名の部分一致。並びはサーバが登録の新しい順に固定する。 */
  list: async (params: {
    page: number;
    size: number;
    status: MemberStatusFilter;
    search?: string;
  }): Promise<PageResult<AdminMemberSummary>> => {
    const response = await apiClient.get(BASE_URL, {
      params: {
        ...toSpringPageParams(params.page, params.size),
        status: params.status,
        search: params.search || undefined,
      },
    });
    return fromSpringPage(response.data);
  },
  get: async (id: number): Promise<AdminMemberDetail> => {
    const response = await apiClient.get(`${BASE_URL}/${id}`);
    return response.data;
  },
  /** 明細。並びはサーバが記帳の新しい順に固定する。 */
  pointEntries: async (
    id: number,
    params: { page: number; size: number }
  ): Promise<PageResult<AdminMemberLedgerEntry>> => {
    const response = await apiClient.get(`${BASE_URL}/${id}/point-entries`, {
      params: toSpringPageParams(params.page, params.size),
    });
    return fromSpringPage(response.data);
  },
  /** 代行退会（204）。二度目は 409。 */
  withdraw: async (id: number, data: StaffWithdrawalRequest): Promise<void> => {
    await apiClient.post(`${BASE_URL}/${id}/withdrawal`, data);
  },
};
