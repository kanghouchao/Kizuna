import { Page, PaginationParams, apiClient } from '@/shared/api';
import { Order, OrderCreateRequest, OrderReceptionist } from '../model/types';

export const orderApi = {
  list: async (params?: PaginationParams & { customer_id?: string }): Promise<Page<Order>> => {
    const response = await apiClient.get('/store/orders', { params });
    return response.data;
  },
  create: async (data: OrderCreateRequest): Promise<Order> => {
    const response = await apiClient.post('/store/orders', data);
    return response.data;
  },
  listReceptionists: async (): Promise<OrderReceptionist[]> => {
    const response = await apiClient.get('/store/orders/receptionists');
    return response.data;
  },
};
