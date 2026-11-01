import { apiClient, fromSpringPage, PageResult } from '@/shared/api';
import {
  ExecutionSummary,
  ExecutionResponse,
  ExecutionRequest,
  ServiceCandidate,
  TaskType,
  TaskStoreOption,
} from '../model/types';
const path = '/task-executions';
export const taskExecutionApi = {
  taskTypes: async (): Promise<TaskType[]> => (await apiClient.get(`${path}/task-types`)).data,
  stores: async (page: number): Promise<PageResult<TaskStoreOption>> =>
    fromSpringPage((await apiClient.get(`${path}/stores`, { params: { page, size: 20 } })).data),
  list: async (page: number): Promise<PageResult<ExecutionSummary>> =>
    fromSpringPage((await apiClient.get(path, { params: { page, size: 50 } })).data),
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
    taskName = 'SERVICE_IDENTITY_CHECK',
    storeId?: string
  ): Promise<PageResult<ServiceCandidate>> =>
    fromSpringPage(
      (
        await apiClient.get(`${path}/service-identities`, {
          params: { page, size: 20, task_name: taskName, store_id: storeId },
        })
      ).data
    ),
};
