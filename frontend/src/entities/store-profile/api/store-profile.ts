import { apiClient } from '@/shared/api';
import { StoreProfileResponse, StoreProfileUpdateRequest } from '../model/types';

export const storeProfileApi = {
  get: async (): Promise<StoreProfileResponse> => {
    const response = await apiClient.get('/tenant/config');
    return response.data;
  },
  update: async (data: StoreProfileUpdateRequest): Promise<StoreProfileResponse> => {
    const response = await apiClient.put('/tenant/config', data);
    return response.data;
  },
};
