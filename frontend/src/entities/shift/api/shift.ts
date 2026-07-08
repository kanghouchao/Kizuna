import { apiClient } from '@/shared/api';
import { ShiftCreateRequest, ShiftResponse, ShiftUpdateRequest } from '../model/types';

export const shiftApi = {
  /** 期間内のシフト一覧を取得する（from==to で単日、月初〜月末で月間） */
  list: async (params: { from: string; to: string }): Promise<ShiftResponse[]> => {
    const response = await apiClient.get('/tenant/shifts', { params });
    return response.data;
  },
  /** シフトを新規作成する */
  create: async (data: ShiftCreateRequest): Promise<ShiftResponse> => {
    const response = await apiClient.post('/tenant/shifts', data);
    return response.data;
  },
  /** シフトを更新する */
  update: async (id: string, data: ShiftUpdateRequest): Promise<ShiftResponse> => {
    const response = await apiClient.put(`/tenant/shifts/${id}`, data);
    return response.data;
  },
  /** シフトを削除する */
  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/tenant/shifts/${id}`);
  },
};
