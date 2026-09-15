import { apiClient, fromSpringPage, PageResult } from '@/shared/api';
import { OwnConsentRequest, OwnServiceConditionSummary } from '../model/types';

export const ownServiceApi = {
  async list(storeId: string, page: number): Promise<PageResult<OwnServiceConditionSummary>> {
    return fromSpringPage(
      (
        await apiClient.get('/platform/me/service-conditions', {
          params: { store_id: storeId, page, size: 20 },
        })
      ).data
    );
  },
  async decide(
    storeId: string,
    id: string,
    body: OwnConsentRequest
  ): Promise<OwnServiceConditionSummary> {
    return (
      await apiClient.put(
        `/platform/me/service-conditions/${encodeURIComponent(id)}/consent`,
        body,
        {
          params: { store_id: storeId },
        }
      )
    ).data;
  },
};
