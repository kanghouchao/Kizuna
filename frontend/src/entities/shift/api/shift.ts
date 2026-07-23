import { apiClient } from '@/shared/api';
import {
  CastScheduleItem,
  ShiftCreateRequest,
  ShiftResponse,
  ShiftUpdateRequest,
} from '../model/types';

export const shiftApi = {
  /** 期間内のシフト一覧を取得する（from==to で単日、月初〜月末で月間） */
  list: async (params: { from: string; to: string }): Promise<ShiftResponse[]> => {
    const response = await apiClient.get('/store/shifts', { params });
    return response.data;
  },
  /** 本人（キャスト）の週間確定シフトを跨店で取得する（cast_id 単層自限）。 */
  mySchedule: async (params: { from: string; to: string }): Promise<CastScheduleItem[]> => {
    const response = await apiClient.get('/platform/me/schedule', { params });
    return response.data;
  },
  /** シフトを新規作成する */
  create: async (data: ShiftCreateRequest): Promise<ShiftResponse> => {
    const response = await apiClient.post('/store/shifts', data);
    return response.data;
  },
  /** シフトを更新する */
  update: async (id: string, data: ShiftUpdateRequest): Promise<ShiftResponse> => {
    const response = await apiClient.put(`/store/shifts/${id}`, data);
    return response.data;
  },
  /** シフトを削除する */
  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/store/shifts/${id}`);
  },
};
