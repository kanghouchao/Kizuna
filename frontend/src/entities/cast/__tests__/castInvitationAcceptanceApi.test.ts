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
const CREDENTIALS = { email: 'cast@example.com', password: 'pass1234' };

describe('castInvitationAcceptanceApi', () => {
  beforeEach(() => {
    mockedPost.mockClear();
  });

  it('view は /cast-invitations/view へトークンを本文で POST する', async () => {
    await castInvitationAcceptanceApi.view(TOKEN);

    expect(mockedPost).toHaveBeenCalledWith('/cast-invitations/view', { token: TOKEN });
  });

  it('acceptAsNewUser は /cast-invitations/acceptance へ登録内容とトークンを本文で POST する', async () => {
    await castInvitationAcceptanceApi.acceptAsNewUser(TOKEN, PAYLOAD);

    expect(mockedPost).toHaveBeenCalledWith('/cast-invitations/acceptance', {
      ...PAYLOAD,
      token: TOKEN,
    });
  });

  it('acceptAsExistingUser は /cast-invitations/acceptance/existing へ資格情報とトークンを本文で POST し、401 をグローバルのセッション処理に渡さない', async () => {
    await castInvitationAcceptanceApi.acceptAsExistingUser(TOKEN, CREDENTIALS);

    expect(mockedPost).toHaveBeenCalledWith(
      '/cast-invitations/acceptance/existing',
      { ...CREDENTIALS, token: TOKEN },
      { skipAuthRedirect: true }
    );
  });

  it('どの呼び出しもリクエストターゲットにトークンを載せない', async () => {
    // パスも問い合わせ文字列もリクエストターゲットとして送られ、アクセスログへ 72 時間有効の
    // 生値が残る。招待は所持だけで受諾が通るため、ログがそのままクレデンシャル置き場になる
    await castInvitationAcceptanceApi.view(TOKEN);
    await castInvitationAcceptanceApi.acceptAsNewUser(TOKEN, PAYLOAD);
    await castInvitationAcceptanceApi.acceptAsExistingUser(TOKEN, CREDENTIALS);

    expect(mockedPost).toHaveBeenCalledTimes(3);
    mockedPost.mock.calls.forEach(([url]) => expect(url).not.toContain(TOKEN));
  });
});
