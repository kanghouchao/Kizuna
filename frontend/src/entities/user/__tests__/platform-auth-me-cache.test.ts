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
    // localStorage と、書けなかった変異の in-memory 控えの両方を初期化する
    clearMeCache();
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

  it('updateMe はキャッシュを失効させ、次の me() はサーバから取り直す', async () => {
    await platformAuthApi.me();
    client.put.mockResolvedValue({ data: me('改名後') });

    // 変異の応答は書き戻さない（並行変異との順序をクライアントで決めない）。失効印だけ残る
    await platformAuthApi.updateMe({ display_name: '改名後' });

    client.get.mockResolvedValue({ data: me('改名後') });
    await expect(platformAuthApi.me()).resolves.toEqual(me('改名後'));
    expect(client.get).toHaveBeenCalledTimes(2);
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

  it('待機中に一時 token へ替わっていても、元 token の失効標は残る（復元後に変異前キャッシュが生き返らない）', async () => {
    await platformAuthApi.me();
    client.put.mockImplementation(async () => {
      // 別タブ（招待受諾）が一時 token を設置した状況で PUT が完了する
      (mockedCookies.get as jest.Mock).mockReturnValue('token-b');
      return { data: me('改名後') };
    });
    await platformAuthApi.updateMe({ display_name: '改名後' });

    // 受諾失敗により元の token が復元された。変異前のキャッシュを配信してはならない
    (mockedCookies.get as jest.Mock).mockReturnValue('token-a');
    client.get.mockResolvedValue({ data: me('改名後') });
    await expect(platformAuthApi.me()).resolves.toEqual(me('改名後'));
    expect(client.get).toHaveBeenCalledTimes(2);
  });

  it('失効標は指紋ごとに保持され、別 token の変異で消えない（別タブ相当の新モジュールで検証）', async () => {
    // token-b の GET が遅延している間に updateMe(B)、続いて一時 token-a の updateMe(A) が走る
    (mockedCookies.get as jest.Mock).mockReturnValue('token-b');
    let resolveGet!: (value: { data: unknown }) => void;
    client.get.mockImplementationOnce(
      () =>
        new Promise(resolve => {
          resolveGet = resolve;
        })
    );
    const pending = platformAuthApi.me();
    client.put.mockResolvedValue({ data: me('B改名後') });
    await platformAuthApi.updateMe({ display_name: 'B改名後' });
    (mockedCookies.get as jest.Mock).mockReturnValue('token-a');
    // A の失効標が B の失効標を消してはならない（単一枠だとここで B の標が失われる）
    await platformAuthApi.updateMe({ display_name: 'A側' });
    (mockedCookies.get as jest.Mock).mockReturnValue('token-b');
    resolveGet({ data: me('B旧') });
    await pending;

    // 別タブ相当: in-memory の変異控えを持たない新しいモジュールから読む
    let freshApi!: typeof platformAuthApi;
    jest.isolateModules(() => {
      freshApi = (require('../api/platform') as { platformAuthApi: typeof platformAuthApi })
        .platformAuthApi;
    });
    client.get.mockResolvedValue({ data: me('B改名後') });
    await expect(freshApi.me()).resolves.toEqual(me('B改名後'));
    // 陳腐な B旧（遅延応答の書き込み）は配信されず、サーバから取り直している
    expect(client.get).toHaveBeenCalledTimes(2);
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

  it('未キャッシュの me() 応答が遅延しても、待機中に済んだ updateMe の失効印を潰さない', async () => {
    let resolveGet!: (value: { data: unknown }) => void;
    client.get.mockImplementationOnce(
      () =>
        new Promise(resolve => {
          resolveGet = resolve;
        })
    );
    const pending = platformAuthApi.me();

    // GET の応答待ちの間に（別タブ相当の）updateMe が完了して失効印を残す
    client.put.mockResolvedValue({ data: me('新しい名前') });
    await platformAuthApi.updateMe({ display_name: '新しい名前' });

    resolveGet({ data: me('古い名前') });
    await expect(pending).resolves.toEqual(me('古い名前'));

    // 遅延応答（古い名前）はキャッシュされず、次の me() はサーバから新値を取り直す
    client.get.mockResolvedValue({ data: me('新しい名前') });
    await expect(platformAuthApi.me()).resolves.toEqual(me('新しい名前'));
    expect(client.get).toHaveBeenCalledTimes(2);
  });

  it('updateMe の失効印の保存に失敗しても（quota 超過）、遅延中の GET が旧値を書き戻さない', async () => {
    let resolveGet!: (value: { data: unknown }) => void;
    client.get.mockImplementationOnce(
      () =>
        new Promise(resolve => {
          resolveGet = resolve;
        })
    );
    const pending = platformAuthApi.me();

    // 失効印の書き込みも失敗する storage 環境で updateMe が完了する
    const setSpy = jest.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('quota exceeded');
    });
    client.put.mockResolvedValue({ data: me('新しい名前') });
    await platformAuthApi.updateMe({ display_name: '新しい名前' });
    setSpy.mockRestore();

    resolveGet({ data: me('古い名前') });
    await pending;

    // in-memory の変異控えが遅延応答の配信を弾く。次の me() はサーバへ取りに行く
    client.get.mockResolvedValue({ data: me('新しい名前') });
    await expect(platformAuthApi.me()).resolves.toEqual(me('新しい名前'));
    expect(client.get).toHaveBeenCalledTimes(2);
  });

  it('書き込みの直後に logout と交錯していたら（token 消失）自分の書き込みを取り消す', async () => {
    // me() は token を3回読む: 入口・書き込み前・書き込み後。3回目だけ logout 後の状況にする
    let readCount = 0;
    (mockedCookies.get as jest.Mock).mockImplementation(() => {
      readCount += 1;
      return readCount >= 3 ? undefined : 'token-a';
    });

    await platformAuthApi.me();

    // 書き込み後の再確認が logout の破棄を打ち消したことを検知し、自分の記録を消す
    expect(window.localStorage.getItem('platform-me-cache')).toBeNull();
  });

  it('保存値に token そのものを含めない（cookie 除去後の localStorage から JWT を回収させない）', async () => {
    await platformAuthApi.me();

    const stored = window.localStorage.getItem('platform-me-cache');
    expect(stored).not.toBeNull();
    expect(stored).not.toContain('token-a');
  });

  it('失効印の保存に失敗したら既存の記録も消し、次の me() はサーバへ取りに行く', async () => {
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
