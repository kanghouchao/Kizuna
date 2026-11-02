import { apiClient } from '@/shared/api';
import { requireId } from '@/shared/lib';
import type { MediumOption, MediumStatus, MediumSummary } from '../model/types';

const resource = (id: string) => `/media/${requireId(id, '媒体')}`;

export const mediumApi = {
  /** 停止中を含む媒体一覧（名前昇順） */
  list: async (status?: MediumStatus): Promise<MediumSummary[]> => {
    const response = await apiClient.get<{ items: MediumSummary[] }>('/media', {
      params: status ? { status } : undefined,
    });
    return response.data.items;
  },
  /** 新規選択の候補（有効な媒体だけ、名前昇順） */
  options: async (): Promise<MediumOption[]> => {
    const response = await apiClient.get<{ items: MediumOption[] }>('/media/options');
    return response.data.items;
  },
  create: async (name: string): Promise<string> => {
    const response = await apiClient.post<{ id: string }>('/media', { name });
    return response.data.id;
  },
  rename: async (id: string, name: string, version: number): Promise<MediumSummary> => {
    const response = await apiClient.put<MediumSummary>(resource(id), {
      name,
      version,
    });
    return response.data;
  },
  stop: async (id: string, version: number): Promise<MediumSummary> => {
    const response = await apiClient.post<MediumSummary>(`${resource(id)}/stop`, { version });
    return response.data;
  },
  resume: async (id: string, version: number): Promise<MediumSummary> => {
    const response = await apiClient.post<MediumSummary>(`${resource(id)}/resume`, { version });
    return response.data;
  },
  delete: async (id: string, version: number): Promise<void> => {
    await apiClient.delete(resource(id), { params: { version } });
  },
};
