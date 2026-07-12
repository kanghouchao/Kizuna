import { apiClient } from '@/shared/api';
import {
  LoginResponse,
  PlatformLoginRequest,
  PlatformMeResponse,
  PlatformStore,
} from '../model/types';

export const platformAuthApi = {
  login: async (credentials: PlatformLoginRequest): Promise<LoginResponse> => {
    const response = await apiClient.post('/platform/login', credentials);
    return response.data;
  },
  me: async (): Promise<PlatformMeResponse> => {
    const response = await apiClient.get('/platform/me');
    return response.data;
  },
  stores: async (): Promise<PlatformStore[]> => {
    const response = await apiClient.get('/platform/stores');
    return response.data;
  },
  logout: async (): Promise<void> => {
    await apiClient.post('/platform/logout');
  },
};
