import { apiClient } from '@/shared/api';
import { Admin, LoginRequest, LoginResponse, PasswordChangeRequest } from '../model/types';

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

  changePassword: async (data: PasswordChangeRequest): Promise<void> => {
    await apiClient.put('/central/password', data);
  },
};
