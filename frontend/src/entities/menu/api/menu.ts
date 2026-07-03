import { apiClient } from '@/shared/api';
import { MenuVO } from '../model/types';

export const centralMenuApi = {
  getMenus: async (): Promise<MenuVO[]> => {
    const response = await apiClient.get('/central/menus/me');
    return response.data;
  },
};

export const storeMenuApi = {
  getMenus: async (): Promise<MenuVO[]> => {
    const response = await apiClient.get('/tenant/menus/me');
    return response.data;
  },
};
