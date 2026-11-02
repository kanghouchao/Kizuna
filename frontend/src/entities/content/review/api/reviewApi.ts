import type { AxiosRequestConfig } from 'axios';
import { apiClient, fromSpringPage, PageResult } from '@/shared/api';
import {
  Review,
  ReviewSummary,
  ReviewHistory,
  ReviewSearch,
  ReviewCommand,
  ReviewWriteResponse,
} from '../model/types';
const path = '/reviews';
const children = {
  CORRECT: 'corrections',
  DECIDE: 'decisions',
  WITHDRAW: 'withdrawals',
  GRANT: 'permissions',
  REVOKE: 'permission-revocations',
  PUBLISH: 'publication',
};
export const reviewApi = {
  list: async (page: number, search: ReviewSearch): Promise<PageResult<ReviewSummary>> =>
    fromSpringPage((await apiClient.get(path, { params: { page, size: 20, ...search } })).data),
  get: async (id: string): Promise<Review> => (await apiClient.get(`${path}/${id}`)).data,
  history: async (id: string, page = 0): Promise<PageResult<ReviewHistory>> =>
    fromSpringPage(
      (await apiClient.get(`${path}/${id}/history`, { params: { page, size: 20 } })).data
    ),
  write: async (
    command: ReviewCommand,
    dedupeKey: string,
    config?: AxiosRequestConfig
  ): Promise<ReviewWriteResponse> => {
    const body = { ...command.input, dedupe_key: dedupeKey };
    if (command.kind === 'CREATE') return (await apiClient.post(path, body, config)).data;
    if (command.kind === 'PUBLISH')
      return (await apiClient.put(`${path}/${command.id}/${children.PUBLISH}`, body, config)).data;
    return (await apiClient.post(`${path}/${command.id}/${children[command.kind]}`, body, config))
      .data;
  },
};
