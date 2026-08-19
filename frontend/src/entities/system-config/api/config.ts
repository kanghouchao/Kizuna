import { SystemConfigResponse, SystemConfigUpdateRequest } from '../model/types';
import { apiClient } from '@/shared/api';
import { requireId } from '@/shared/lib';

const BASE_URL = '/platform/configs';

export const systemConfigService = {
  // 設定一覧の取得 (Client Component用)
  getAllConfigs: async (category?: string): Promise<SystemConfigResponse[]> => {
    const params = category ? { category } : {};
    const response = await apiClient.get<SystemConfigResponse[]>(BASE_URL, { params });
    return response.data;
  },

  // 設定の更新 (Client Component用)。宛先は設定キー 1 件で、本体は値だけを送る
  updateConfig: async (
    configKey: string | undefined,
    data: SystemConfigUpdateRequest
  ): Promise<SystemConfigResponse> => {
    const response = await apiClient.put<SystemConfigResponse>(
      `${BASE_URL}/${requireId(configKey, '設定')}`,
      data
    );
    return response.data;
  },
};
