import { apiClient, fromSpringPage, PageResult, toSpringPageParams } from '@/shared/api';
import {
  PlatformCastSummaryResponse,
  PlatformCastResponse,
  PlatformCastEnrollmentResponse,
} from '../model/platform-types';

export const platformCastApi = {
  list: async (params: {
    page: number;
    size: number;
    search?: string;
  }): Promise<PageResult<PlatformCastSummaryResponse>> => {
    const response = await apiClient.get('/platform/casts', {
      params: { ...toSpringPageParams(params.page, params.size), search: params.search },
    });
    return fromSpringPage(response.data);
  },
  get: async (id: number): Promise<PlatformCastResponse> => {
    const response = await apiClient.get(`/platform/casts/${id}`);
    return response.data;
  },
  enrollments: async (
    id: number,
    params: { page: number; size: number }
  ): Promise<PageResult<PlatformCastEnrollmentResponse>> => {
    const response = await apiClient.get(`/platform/casts/${id}/enrollments`, {
      params: toSpringPageParams(params.page, params.size),
    });
    return fromSpringPage(response.data);
  },
};
