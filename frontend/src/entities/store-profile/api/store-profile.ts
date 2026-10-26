import { apiClient } from '@/shared/api';
import { StoreProfileResponse, StoreProfileUpdateRequest } from '../model/types';

export const storeProfileApi = {
  get: async (): Promise<StoreProfileResponse> => {
    const response = await apiClient.get('/config');
    return response.data;
  },
  update: async (data: StoreProfileUpdateRequest): Promise<StoreProfileResponse> => {
    const response = await apiClient.put('/config', data);
    return response.data;
  },
  // 現在店舗（X-Store-ID）を準備中から稼働中へ移す。既に稼働中なら 400。
  activate: async (): Promise<void> => {
    await apiClient.post('/config/activation');
  },
};
