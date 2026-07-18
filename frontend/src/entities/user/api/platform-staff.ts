import { apiClient } from '@/shared/api';
import {
  CapabilityBundleResponse,
  GrantHistoryEntryResponse,
  PlatformStaffCreateRequest,
  PlatformStaffResponse,
  PlatformStaffUpdateRequest,
} from '../model/types';

export const platformStaffApi = {
  list: async (): Promise<PlatformStaffResponse[]> => {
    const response = await apiClient.get('/platform/staff');
    return response.data;
  },
  create: async (data: PlatformStaffCreateRequest): Promise<PlatformStaffResponse> => {
    const response = await apiClient.post('/platform/staff', data);
    return response.data;
  },
  update: async (id: number, data: PlatformStaffUpdateRequest): Promise<PlatformStaffResponse> => {
    const response = await apiClient.put(`/platform/staff/${id}`, data);
    return response.data;
  },
  bundles: async (): Promise<CapabilityBundleResponse[]> => {
    const response = await apiClient.get('/platform/capability-bundles');
    return response.data;
  },
  grantHistory: async (id: number): Promise<GrantHistoryEntryResponse[]> => {
    const response = await apiClient.get(`/platform/staff/${id}/grant-history`);
    return response.data;
  },
};
