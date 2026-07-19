import { Page, PaginationParams, apiClient } from '@/shared/api';
import { Order, OrderCreateRequest } from '../model/types';

export const orderApi = {
  list: async (params?: PaginationParams & { customer_id?: string }): Promise<Page<Order>> => {
    const response = await apiClient.get('/store/orders', { params });
    return response.data;
  },
  create: async (data: OrderCreateRequest): Promise<Order> => {
    const response = await apiClient.post('/store/orders', data);
    return response.data;
  },
};
