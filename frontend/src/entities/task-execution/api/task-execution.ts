import {
  apiClient,
  fromCursorPage,
  fromSpringPage,
  CursorPageResult,
  PageResult,
} from '@/shared/api';
import {
  ExecutionSummary,
  ExecutionResponse,
  ExecutionRequest,
  ServiceCandidate,
} from '../model/types';
const path = '/platform/task-executions';
export const taskExecutionApi = {
  list: async (cursor?: string): Promise<CursorPageResult<ExecutionSummary>> =>
    fromCursorPage((await apiClient.get(path, { params: { cursor } })).data),
  get: async (id: number): Promise<ExecutionResponse> =>
    (await apiClient.get(`${path}/${id}`)).data,
  create: async (data: ExecutionRequest): Promise<ExecutionResponse> =>
    (await apiClient.post(path, data)).data,
  retry: async (id: number, reason: string): Promise<ExecutionResponse> =>
    (await apiClient.post(`${path}/${id}/retries`, { reason })).data,
  interrupt: async (id: number, reason: string): Promise<ExecutionResponse> =>
    (await apiClient.post(`${path}/${id}/interruption`, { reason })).data,
  candidates: async (
    page: number,
    taskName = 'SERVICE_IDENTITY_CHECK'
  ): Promise<PageResult<ServiceCandidate>> =>
    fromSpringPage(
      (
        await apiClient.get(`${path}/service-identities`, {
          params: { page, size: 20, task_name: taskName },
        })
      ).data
    ),
};
