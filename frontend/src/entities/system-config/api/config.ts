import { SystemConfigResponse, SystemConfigUpdateRequest } from '../model/types';
import { apiClient } from '@/shared/api';

const BASE_URL = '/platform/configs';

export const systemConfigService = {
  // 設定一覧の取得 (Client Component用)
  getAllConfigs: async (category?: string): Promise<SystemConfigResponse[]> => {
    const params = category ? { category } : {};
    const response = await apiClient.get<SystemConfigResponse[]>(BASE_URL, { params });
    return response.data;
  },

  // 設定の更新 (Client Component用)
  updateConfig: async (data: SystemConfigUpdateRequest): Promise<SystemConfigResponse> => {
    const response = await apiClient.put<SystemConfigResponse>(BASE_URL, data);
    return response.data;
  },
};
