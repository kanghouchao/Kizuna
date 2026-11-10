import type { AxiosRequestConfig } from 'axios';
import { apiClient, fromSpringPage, PageResult } from '@/shared/api';
import {
  Delivery,
  DeliveryInput,
  DeliveryResolution,
  DeliverySummary,
  DeliveryAttempt,
} from '../model/types';
const deliveriesAt = (path: string) => ({
  list: async (page: number): Promise<PageResult<DeliverySummary>> =>
    fromSpringPage((await apiClient.get(path, { params: { page, size: 50 } })).data),
  get: async (id: string): Promise<Delivery> => (await apiClient.get(`${path}/${id}`)).data,
  create: async (
    input: DeliveryInput,
    dedupeKey: string,
    config?: AxiosRequestConfig
  ): Promise<Delivery> =>
    (await apiClient.post(path, { ...input, dedupe_key: dedupeKey }, config)).data,
  queue: async (id: string, version: number, reason: string): Promise<Delivery> =>
    (await apiClient.post(`${path}/${id}/queue`, { version, reason })).data,
  retry: async (id: string, version: number, reason: string): Promise<Delivery> =>
    (await apiClient.post(`${path}/${id}/retries`, { version, reason })).data,
  resolve: async (
    id: string,
    version: number,
    outcome: DeliveryResolution,
    reason: string
  ): Promise<Delivery> =>
    (await apiClient.post(`${path}/${id}/resolutions`, { version, outcome, reason })).data,
  history: async (id: string, page: number): Promise<PageResult<DeliveryAttempt>> =>
    fromSpringPage(
      (await apiClient.get(`${path}/${id}/attempts`, { params: { page, size: 50 } })).data
    ),
});
export type NotificationApi = ReturnType<typeof deliveriesAt>;
// 店舗の行は X-Store-ID の店舗で、会員宛（店舗を持たない）の行は別の資源で扱う。
export const notificationApi: NotificationApi = deliveriesAt('/notification-deliveries');
export const memberNotificationApi: NotificationApi = deliveriesAt(
  '/member-notification-deliveries'
);
