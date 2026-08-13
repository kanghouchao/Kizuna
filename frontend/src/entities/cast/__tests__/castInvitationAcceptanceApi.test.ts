import { castInvitationAcceptanceApi } from '@/entities/cast';
import { apiClient } from '@/shared/api';

// get を生やさない: トークンをパスに載せる GET へ戻した瞬間に「関数でない」で落ちる
jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    post: jest.fn(async (url: string, body?: unknown) => ({ data: { ok: true, url, body } })),
  },
}));

const mockedPost = apiClient.post as unknown as jest.Mock;

const TOKEN = 'raw-invitation-token';
const PAYLOAD = { email: 'a@example.com', password: 'pass1234', display_name: '花子' };

describe('castInvitationAcceptanceApi', () => {
  beforeEach(() => {
    mockedPost.mockClear();
  });

  it('view は /platform/cast-invitations/view へトークンを本文で POST する', async () => {
    await castInvitationAcceptanceApi.view(TOKEN);

    expect(mockedPost).toHaveBeenCalledWith('/platform/cast-invitations/view', { token: TOKEN });
  });

  it('acceptAsNewUser は /platform/cast-invitations/acceptance へ登録内容とトークンを本文で POST する', async () => {
    await castInvitationAcceptanceApi.acceptAsNewUser(TOKEN, PAYLOAD);

    expect(mockedPost).toHaveBeenCalledWith('/platform/cast-invitations/acceptance', {
      ...PAYLOAD,
      token: TOKEN,
    });
  });

  it('acceptAsExistingUser は /platform/cast-invitations/acceptance/existing へトークンを本文で POST する', async () => {
    await castInvitationAcceptanceApi.acceptAsExistingUser(TOKEN);

    expect(mockedPost).toHaveBeenCalledWith('/platform/cast-invitations/acceptance/existing', {
      token: TOKEN,
    });
  });

  it('どの呼び出しもリクエストターゲットにトークンを載せない', async () => {
    // パスも問い合わせ文字列もリクエストターゲットとして送られ、アクセスログへ 72 時間有効の
    // 生値が残る。招待は所持だけで受諾が通るため、ログがそのままクレデンシャル置き場になる
    await castInvitationAcceptanceApi.view(TOKEN);
    await castInvitationAcceptanceApi.acceptAsNewUser(TOKEN, PAYLOAD);
    await castInvitationAcceptanceApi.acceptAsExistingUser(TOKEN);

    expect(mockedPost).toHaveBeenCalledTimes(3);
    mockedPost.mock.calls.forEach(([url]) => expect(url).not.toContain(TOKEN));
  });
});
