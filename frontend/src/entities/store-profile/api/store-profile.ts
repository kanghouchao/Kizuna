import { apiClient } from '@/shared/api';
import { StoreProfileResponse, StoreProfileUpdateRequest } from '../model/types';

export const storeProfileApi = {
  get: async (): Promise<StoreProfileResponse> => {
    const response = await apiClient.get('/store/config');
    return response.data;
  },
  update: async (data: StoreProfileUpdateRequest): Promise<StoreProfileResponse> => {
    const response = await apiClient.put('/store/config', data);
    return response.data;
  },
};
