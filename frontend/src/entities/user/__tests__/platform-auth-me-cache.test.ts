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

  it('updateMe の待機中に token が替わったら（別タブの再ログイン）応答をキャッシュしない', async () => {
    client.put.mockImplementation(async () => {
      // PUT の応答待ちの間に別タブでログインし直し、token が入れ替わった状況
      (mockedCookies.get as jest.Mock).mockReturnValue('token-b');
      return { data: me('旧利用者') };
    });

    await platformAuthApi.updateMe({ display_name: '旧利用者' });

    // token-b の me はキャッシュに汚染されず、サーバから取り直す
    client.get.mockResolvedValue({ data: me('新利用者') });
    await expect(platformAuthApi.me()).resolves.toEqual(me('新利用者'));
    expect(client.get).toHaveBeenCalledTimes(1);
  });

  it('clearMeCache（ログアウト）後は取り直す', async () => {
    await platformAuthApi.me();

    clearMeCache();
    await platformAuthApi.me();

    expect(client.get).toHaveBeenCalledTimes(2);
  });

  it('応答待ちの間に logout が走ったら（token 除去済み）キャッシュへ書き戻さない', async () => {
    client.get.mockImplementation(async () => {
      // GET /platform/me の応答待ちの間に logout がキャッシュ破棄と token 除去を終えた状況
      clearMeCache();
      (mockedCookies.get as jest.Mock).mockReturnValue(undefined);
      return { data: me('山田') };
    });

    await platformAuthApi.me();

    expect(window.localStorage.getItem('platform-me-cache')).toBeNull();
  });

  it('未キャッシュの me() 応答が遅延しても、待機中に済んだ updateMe の書き込みを潰さない', async () => {
    let resolveGet!: (value: { data: unknown }) => void;
    client.get.mockImplementationOnce(
      () =>
        new Promise(resolve => {
          resolveGet = resolve;
        })
    );
    const pending = platformAuthApi.me();

    // GET の応答待ちの間に（別タブ相当の）updateMe が完了して新値をキャッシュする
    client.put.mockResolvedValue({ data: me('新しい名前') });
    await platformAuthApi.updateMe({ display_name: '新しい名前' });

    resolveGet({ data: me('古い名前') });
    await expect(pending).resolves.toEqual(me('古い名前'));

    // キャッシュは updateMe の新値のまま。次の me() は再取得せず新値を返す
    await expect(platformAuthApi.me()).resolves.toEqual(me('新しい名前'));
    expect(client.get).toHaveBeenCalledTimes(1);
  });

  it('保存値に token そのものを含めない（cookie 除去後の localStorage から JWT を回収させない）', async () => {
    await platformAuthApi.me();

    const stored = window.localStorage.getItem('platform-me-cache');
    expect(stored).not.toBeNull();
    expect(stored).not.toContain('token-a');
  });

  it('上書きの失敗時は既存の記録も消し、次の me() はサーバへ取りに行く', async () => {
    await platformAuthApi.me();
    expect(window.localStorage.getItem('platform-me-cache')).not.toBeNull();

    const setSpy = jest.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('quota exceeded');
    });
    client.put.mockResolvedValue({ data: me('改名後') });
    await platformAuthApi.updateMe({ display_name: '改名後' });
    setSpy.mockRestore();

    // 古い値が残って命中し続けてはならない。キャッシュは空になり、サーバから取り直す
    expect(window.localStorage.getItem('platform-me-cache')).toBeNull();
    client.get.mockResolvedValue({ data: me('改名後') });
    await expect(platformAuthApi.me()).resolves.toEqual(me('改名後'));
    expect(client.get).toHaveBeenCalledTimes(2);
  });

  it('clearMeCache は storage が塞がれていても投げない（logout の後続処理を止めない）', () => {
    const removeSpy = jest.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new Error('storage blocked');
    });

    expect(() => clearMeCache()).not.toThrow();

    removeSpy.mockRestore();
  });

  it('壊れた保存値は無視して取り直す', async () => {
    window.localStorage.setItem('platform-me-cache', '{broken json');

    await expect(platformAuthApi.me()).resolves.toEqual(me('山田'));
    expect(client.get).toHaveBeenCalledTimes(1);
  });
});
