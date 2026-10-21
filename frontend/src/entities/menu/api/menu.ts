import { apiClient } from '@/shared/api';
import { MenuVO } from '../model/types';

export const menuApi = {
  getMenus: async (): Promise<MenuVO[]> => {
    const response = await apiClient.get('/admin/menus/me');
    return response.data;
  },
};
