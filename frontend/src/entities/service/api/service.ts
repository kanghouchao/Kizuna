import { apiClient, fromSpringPage, PageResult } from '@/shared/api';
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
    return fromSpringPage((await apiClient.get('/services', { params })).data);
  },
  async get(id: string): Promise<ServiceResponse> {
    return (await apiClient.get(`/services/${encodeURIComponent(id)}`)).data;
  },
  async create(body: ServiceCreateRequest): Promise<{ id: string }> {
    return (await apiClient.post('/services', body)).data;
  },
  async update(id: string, body: ServiceUpdateRequest): Promise<ServiceResponse> {
    return (await apiClient.put(`/services/${encodeURIComponent(id)}`, body)).data;
  },
  async remove(id: string, expectedVersion: number): Promise<void> {
    await apiClient.delete(`/services/${encodeURIComponent(id)}`, {
      params: { expected_version: expectedVersion },
    });
  },
  async history(id: string, page = 0): Promise<PageResult<ServiceRevisionResponse>> {
    return fromSpringPage(
      (
        await apiClient.get(`/services/${encodeURIComponent(id)}/revisions`, {
          params: { page, size: 20 },
        })
      ).data
    );
  },
};
