import { apiClient } from '@/shared/api';
import {
  CastScheduleItem,
  CastShiftRequestItem,
  CastStoreItem,
  ShiftCreateRequest,
  ShiftRequestCreateRequest,
  ShiftRequestResponse,
  ShiftResponse,
  ShiftUpdateRequest,
  StoreShiftRequestItem,
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
  /** 出勤希望を提出する（本人・cast）。 */
  submitShiftRequest: async (data: ShiftRequestCreateRequest): Promise<ShiftRequestResponse> => {
    const response = await apiClient.post('/platform/me/shift-requests', data);
    return response.data;
  },
  /** 本人（キャスト）の出勤希望履歴を取得する（全所属店横断・全量・新しい順）。 */
  myShiftRequests: async (): Promise<CastShiftRequestItem[]> => {
    const response = await apiClient.get('/platform/me/shift-requests');
    return response.data;
  },
  /** 本人（キャスト）の所属店舗一覧を取得する（提出フォームの店舗セレクタ用）。 */
  myStores: async (): Promise<CastStoreItem[]> => {
    const response = await apiClient.get('/platform/me/stores');
    return response.data;
  },
  /** 店舗側 inbox の出勤希望一覧を取得する（status 省略で全件）。 */
  listShiftRequests: async (params?: { status?: string }): Promise<StoreShiftRequestItem[]> => {
    const response = await apiClient.get('/store/shift-requests', { params });
    return response.data;
  },
  /** 出勤希望を承認する（確定シフトが新規作成される）。 */
  approveShiftRequest: async (id: string): Promise<StoreShiftRequestItem> => {
    const response = await apiClient.post(`/store/shift-requests/${id}/approval`);
    return response.data;
  },
  /** 出勤希望を辞退する。 */
  declineShiftRequest: async (id: string): Promise<StoreShiftRequestItem> => {
    const response = await apiClient.post(`/store/shift-requests/${id}/decline`);
    return response.data;
  },
};
