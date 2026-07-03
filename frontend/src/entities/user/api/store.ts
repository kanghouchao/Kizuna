import { apiClient } from '@/shared/api';
import {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterResponse,
  StoreUserResponse,
} from '../model/types';

export const storeAuthApi = {
  register: async (data: RegisterRequest): Promise<RegisterResponse> => {
    const response = await apiClient.post('/tenant/init-admin-use', data);
    return response.data;
  },
  login: async (data: LoginRequest): Promise<LoginResponse> => {
    const response = await apiClient.post('/tenant/login', data);
    return response.data;
  },
  logout: async (): Promise<void> => {
    await apiClient.post('/tenant/logout');
  },
  me: async (): Promise<StoreUserResponse> => {
    const response = await apiClient.get('/tenant/me');
    return response.data;
  },
};
