import { apiClient, fromCursorPage, CursorPageResult } from '@/shared/api';
import { Delivery, DeliveryInput, DeliverySummary, DeliveryAttempt } from '../model/types';
const path = '/store/notification-deliveries';
export const notificationApi = {
  list: async (cursor?: string): Promise<CursorPageResult<DeliverySummary>> =>
    fromCursorPage((await apiClient.get(path, { params: { cursor } })).data),
  get: async (id: string): Promise<Delivery> => (await apiClient.get(`${path}/${id}`)).data,
  create: async (input: DeliveryInput): Promise<Delivery> =>
    (await apiClient.post(path, input)).data,
  queue: async (id: string, version: number, reason: string): Promise<Delivery> =>
    (await apiClient.post(`${path}/${id}/queue`, { version, reason })).data,
  retry: async (id: string, version: number, reason: string): Promise<Delivery> =>
    (await apiClient.post(`${path}/${id}/retries`, { version, reason })).data,
  history: async (id: string, cursor?: string): Promise<CursorPageResult<DeliveryAttempt>> =>
    fromCursorPage((await apiClient.get(`${path}/${id}/attempts`, { params: { cursor } })).data),
};
