import { apiClient, fromCursorPage, CursorPageResult } from '@/shared/api';
import { AuditEventSummary, AuditEventResponse } from '../model/types';
export const auditEventApi = {
  list: async (cursor?: string, action?: string): Promise<CursorPageResult<AuditEventSummary>> =>
    fromCursorPage(
      (
        await apiClient.get('/platform/audit-events', {
          params: { cursor, action: action || undefined },
        })
      ).data
    ),
  get: async (id: number): Promise<AuditEventResponse> =>
    (await apiClient.get(`/platform/audit-events/${id}`)).data,
};
