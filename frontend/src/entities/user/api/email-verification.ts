import { apiClient } from '@/shared/api';

export const emailVerificationApi = {
  confirm: async (token: string): Promise<void> => {
    await apiClient.post('/auth/email-verification/confirmation', { token });
  },
};
