import { apiClient } from '@/shared/api';
import { Admin, LoginRequest, LoginResponse } from '../model/types';

export const centralAuthApi = {
  login: async (credentials: LoginRequest): Promise<LoginResponse> => {
    const response = await apiClient.post('/central/login', credentials);
    return response.data;
  },

  me: async (): Promise<Admin> => {
    const response = await apiClient.get('/central/me');
    return response.data;
  },

  logout: async (): Promise<void> => {
    await apiClient.post('/central/logout');
  },
};
