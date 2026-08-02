import Cookies from 'js-cookie';
import { clearMeCache, platformAuthApi } from '../api/platform';

jest.mock('js-cookie');

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    put: jest.fn(),
    post: jest.fn(),
  },
}));

const client = jest.requireMock('@/shared/api/client').default;
const mockedCookies = Cookies as jest.Mocked<typeof Cookies>;

const me = (name: string) => ({ display_name: name, permissions: ['PLATFORM_VIEW'] });

describe('platformAuthApi.me の token 単位キャッシュ', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    window.localStorage.clear();
    (mockedCookies.get as jest.Mock).mockReturnValue('token-a');
    client.get.mockResolvedValue({ data: me('山田') });
  });

  it('同一 token の 2 回目はリクエストせずキャッシュを返す', async () => {
    const first = await platformAuthApi.me();
    const second = await platformAuthApi.me();

    expect(client.get).toHaveBeenCalledTimes(1);
    expect(second).toEqual(first);
  });

  it('token が替わると（再ログイン）取り直す', async () => {
    await platformAuthApi.me();

    (mockedCookies.get as jest.Mock).mockReturnValue('token-b');
    client.get.mockResolvedValue({ data: me('別人') });
    const refreshed = await platformAuthApi.me();

    expect(client.get).toHaveBeenCalledTimes(2);
    expect(refreshed).toEqual(me('別人'));
  });

  it('token が無ければキャッシュに関与せず毎回取得する', async () => {
    (mockedCookies.get as jest.Mock).mockReturnValue(undefined);

    await platformAuthApi.me();
    await platformAuthApi.me();

    expect(client.get).toHaveBeenCalledTimes(2);
    expect(window.localStorage.getItem('platform-me-cache')).toBeNull();
  });

  it('併走する同一 token の呼び出しは 1 リクエストへ束ねる', async () => {
    const [first, second] = await Promise.all([platformAuthApi.me(), platformAuthApi.me()]);

    expect(client.get).toHaveBeenCalledTimes(1);
    expect(second).toEqual(first);
  });

  it('取得失敗はキャッシュせず、次の呼び出しで改めて取得する', async () => {
    client.get.mockRejectedValueOnce(new Error('network'));

    await expect(platformAuthApi.me()).rejects.toThrow('network');
    await expect(platformAuthApi.me()).resolves.toEqual(me('山田'));

    expect(client.get).toHaveBeenCalledTimes(2);
  });

  it('updateMe は応答でキャッシュを上書きする（次の me は新表示名を返す）', async () => {
    await platformAuthApi.me();
    client.put.mockResolvedValue({ data: me('改名後') });

    await platformAuthApi.updateMe({ display_name: '改名後' });
    const after = await platformAuthApi.me();

    expect(after).toEqual(me('改名後'));
    // 表示名の更新後もキャッシュが有効なままなので GET は初回の 1 回だけ
    expect(client.get).toHaveBeenCalledTimes(1);
  });

  it('clearMeCache（ログアウト）後は取り直す', async () => {
    await platformAuthApi.me();

    clearMeCache();
    await platformAuthApi.me();

    expect(client.get).toHaveBeenCalledTimes(2);
  });

  it('壊れた保存値は無視して取り直す', async () => {
    window.localStorage.setItem('platform-me-cache', '{broken json');

    await expect(platformAuthApi.me()).resolves.toEqual(me('山田'));
    expect(client.get).toHaveBeenCalledTimes(1);
  });
});
