import { initialSetupApi } from '@/entities/user';
import { apiClient } from '@/shared/api';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    post: jest.fn(async () => ({ data: { id: 1 } })),
  },
}));

const mockedClient = apiClient as unknown as { post: jest.Mock };

describe('initial setup api', () => {
  it('register は /auth/initial-setup へ登録内容をそのまま送る', async () => {
    const request = {
      email: 'admin@kizuna.test',
      password: 'password1234',
      display_name: '管理者',
    };

    await expect(initialSetupApi.register(request)).resolves.toBeUndefined();

    expect(mockedClient.post).toHaveBeenCalledWith('/auth/initial-setup', request);
  });
});
