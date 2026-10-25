import { apiClient } from '@/shared/api';
import { InitialSetupRequest } from '../model/types';

export const initialSetupApi = {
  /** 最初の管理者を登録する（公開・匿名）。トークンは返らず、完了済みなら 404。 */
  register: async (data: InitialSetupRequest): Promise<void> => {
    await apiClient.post('/auth/initial-setup', data);
  },
};
