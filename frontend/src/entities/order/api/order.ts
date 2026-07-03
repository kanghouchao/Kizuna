import { Page, PaginationParams, apiClient } from '@/shared/api';
import { Order, OrderCreateRequest } from '../model/types';

export const orderApi = {
  list: async (params?: PaginationParams): Promise<Page<Order>> => {
    const response = await apiClient.get('/tenant/orders', { params });
    return response.data;
  },
  create: async (data: OrderCreateRequest): Promise<Order> => {
    const response = await apiClient.post('/tenant/orders', data);
    return response.data;
  },
};
