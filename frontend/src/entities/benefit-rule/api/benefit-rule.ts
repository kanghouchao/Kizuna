import { apiClient, PageResult, fromSpringPage, toSpringPageParams } from '@/shared/api';
import {
  BenefitRuleCreateRequest,
  BenefitRuleResponse,
  BenefitRuleSummaryResponse,
  BenefitRuleUpdateRequest,
} from '../model/types';

const BASE_URL = '/platform/benefit-rules';

/** 特典規則の管理 API（BENEFIT_MANAGE 限定）。削除の口は無く、退場は停用で表す。 */
export const benefitRuleApi = {
  /** 一覧を取得する。page は他一覧と同じ 0 起点（Spring Page 形）で、停用済みも並ぶ。 */
  list: async (params: {
    page: number;
    size: number;
  }): Promise<PageResult<BenefitRuleSummaryResponse>> => {
    const response = await apiClient.get(BASE_URL, {
      params: toSpringPageParams(params.page, params.size),
    });
    return fromSpringPage(response.data);
  },
  get: async (id: number): Promise<BenefitRuleResponse> => {
    const response = await apiClient.get(`${BASE_URL}/${id}`);
    return response.data;
  },
  create: async (data: BenefitRuleCreateRequest): Promise<BenefitRuleResponse> => {
    const response = await apiClient.post(BASE_URL, data);
    return response.data;
  },
  update: async (id: number, data: BenefitRuleUpdateRequest): Promise<BenefitRuleResponse> => {
    const response = await apiClient.put(`${BASE_URL}/${id}`, data);
    return response.data;
  },
  /** 停用（退場）。再開の口は無い。 */
  deactivate: async (id: number): Promise<void> => {
    await apiClient.post(`${BASE_URL}/${id}/deactivation`);
  },
};
