import { apiClient, fromSpringPage, PageResult } from '@/shared/api';
import { AuditEventSummary, AuditEventResponse } from '../model/types';
export const auditEventApi = {
  list: async (page: number, action?: string): Promise<PageResult<AuditEventSummary>> =>
    fromSpringPage(
      (
        await apiClient.get('/audit-events', {
          params: { page, size: 50, action: action || undefined },
        })
      ).data
    ),
  get: async (id: number): Promise<AuditEventResponse> =>
    (await apiClient.get(`/audit-events/${id}`)).data,
};
