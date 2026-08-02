import { platformLineApi } from '@/entities/user';
import { apiClient } from '@/shared/api';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { url } })),
    post: jest.fn(async (url: string) => ({ data: { url } })),
  },
}));

const mockedClient = apiClient as unknown as { get: jest.Mock; post: jest.Mock };

const authorization = {
  code: 'auth-code',
  redirect_uri: 'https://kizuna.test/platform/line/callback',
  code_verifier: 'verifier',
};

describe('platform line api', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('config は /platform/line/config を GET する', async () => {
    await platformLineApi.config();
    expect(mockedClient.get).toHaveBeenCalledWith('/platform/line/config');
  });

  it('login は /platform/line/login へ引き換え要求を送り、401 の共通処理を回避する', async () => {
    await platformLineApi.login(authorization);
    expect(mockedClient.post).toHaveBeenCalledWith('/platform/line/login', authorization, {
      skipAuthRedirect: true,
    });
  });

  it('register は /platform/line/register へ登録チケットを送る', async () => {
    const request = {
      registration_ticket: 'ticket',
      display_name: '会員太郎',
      email: 'member@kizuna.test',
    };
    await platformLineApi.register(request);
    expect(mockedClient.post).toHaveBeenCalledWith('/platform/line/register', request, {
      skipAuthRedirect: true,
    });
  });

  it('link は /platform/me/line へ引き換え要求を送る（Bearer は共通配線が付与する）', async () => {
    await platformLineApi.link(authorization);
    expect(mockedClient.post).toHaveBeenCalledWith('/platform/me/line', authorization);
  });
});
