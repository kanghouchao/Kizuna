import { apiClient, fromSpringPage, PageResult, toSpringPageParams } from '@/shared/api';
import {
  AdminCastSummaryResponse,
  AdminCastResponse,
  AdminCastEnrollmentResponse,
} from '../model/admin-types';

export const adminCastApi = {
  list: async (params: {
    page: number;
    size: number;
    search?: string;
  }): Promise<PageResult<AdminCastSummaryResponse>> => {
    const response = await apiClient.get('/cast-persons', {
      params: { ...toSpringPageParams(params.page, params.size), search: params.search },
    });
    return fromSpringPage(response.data);
  },
  get: async (id: number): Promise<AdminCastResponse> => {
    const response = await apiClient.get(`/cast-persons/${id}`);
    return response.data;
  },
  enrollments: async (
    id: number,
    params: { page: number; size: number }
  ): Promise<PageResult<AdminCastEnrollmentResponse>> => {
    const response = await apiClient.get(`/cast-persons/${id}/enrollments`, {
      params: toSpringPageParams(params.page, params.size),
    });
    return fromSpringPage(response.data);
  },
};
