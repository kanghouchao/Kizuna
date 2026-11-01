import { apiClient, fromSpringPage, PageResult } from '@/shared/api';
import { Delivery, DeliveryInput, DeliverySummary, DeliveryAttempt } from '../model/types';
const path = '/notification-deliveries';
export const notificationApi = {
  list: async (page: number): Promise<PageResult<DeliverySummary>> =>
    fromSpringPage((await apiClient.get(path, { params: { page, size: 50 } })).data),
  get: async (id: string): Promise<Delivery> => (await apiClient.get(`${path}/${id}`)).data,
  create: async (input: DeliveryInput): Promise<Delivery> =>
    (await apiClient.post(path, input)).data,
  queue: async (id: string, version: number, reason: string): Promise<Delivery> =>
    (await apiClient.post(`${path}/${id}/queue`, { version, reason })).data,
  retry: async (id: string, version: number, reason: string): Promise<Delivery> =>
    (await apiClient.post(`${path}/${id}/retries`, { version, reason })).data,
  history: async (id: string, page: number): Promise<PageResult<DeliveryAttempt>> =>
    fromSpringPage(
      (await apiClient.get(`${path}/${id}/attempts`, { params: { page, size: 50 } })).data
    ),
};
