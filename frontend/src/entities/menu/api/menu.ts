import { apiClient } from '@/shared/api';
import { MenuTree } from '../model/types';

export const menuApi = {
  getMenus: async (): Promise<MenuTree[]> => {
    const response = await apiClient.get('/menus/me');
    return response.data;
  },
};
