import {
  apiClient,
  fromSpringPage,
  fromCursorPage,
  PageResult,
  CursorPageResult,
} from '@/shared/api';
import {
  ServiceCreateRequest,
  ServiceUpdateRequest,
  ServiceSummary,
  ServiceResponse,
  ServiceRevisionResponse,
  ServiceKind,
} from '../model/types';
export const serviceApi = {
  async list(params: {
    page: number;
    size: number;
    kind?: ServiceKind;
    deleted: boolean;
  }): Promise<PageResult<ServiceSummary>> {
    return fromSpringPage((await apiClient.get('/store/services', { params })).data);
  },
  async get(id: string): Promise<ServiceResponse> {
    return (await apiClient.get(`/store/services/${encodeURIComponent(id)}`)).data;
  },
  async create(body: ServiceCreateRequest): Promise<{ id: string }> {
    return (await apiClient.post('/store/services', body)).data;
  },
  async update(id: string, body: ServiceUpdateRequest): Promise<ServiceResponse> {
    return (await apiClient.put(`/store/services/${encodeURIComponent(id)}`, body)).data;
  },
  async remove(id: string, expectedVersion: number): Promise<void> {
    await apiClient.delete(`/store/services/${encodeURIComponent(id)}`, {
      params: { expected_version: expectedVersion },
    });
  },
  async history(id: string, cursor?: string): Promise<CursorPageResult<ServiceRevisionResponse>> {
    return fromCursorPage(
      (
        await apiClient.get(`/store/services/${encodeURIComponent(id)}/revisions`, {
          params: { cursor, size: 20 },
        })
      ).data
    );
  },
};
