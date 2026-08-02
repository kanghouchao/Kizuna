import {
  LINE_AUTHORIZE_ENDPOINT,
  consumeLineAuthorization,
  lineCallbackRedirectUri,
  prepareLineAuthorization,
  startLineAuthorization,
} from '../line-oauth';

const STORAGE_KEY = 'kizuna-line-oauth';

describe('line-oauth', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  describe('prepareLineAuthorization', () => {
    it('認可URLに PKCE(S256) と state を載せ、code_verifier を保存する', async () => {
      const url = new URL(await prepareLineAuthorization('channel-1', 'login'));

      expect(`${url.origin}${url.pathname}`).toBe(LINE_AUTHORIZE_ENDPOINT);
      expect(url.searchParams.get('response_type')).toBe('code');
      expect(url.searchParams.get('client_id')).toBe('channel-1');
      expect(url.searchParams.get('redirect_uri')).toBe(lineCallbackRedirectUri());
      expect(url.searchParams.get('scope')).toBe('profile openid');
      expect(url.searchParams.get('code_challenge_method')).toBe('S256');

      const stored = JSON.parse(sessionStorage.getItem(STORAGE_KEY) as string);
      expect(stored.intent).toBe('login');
      expect(stored.state).toBe(url.searchParams.get('state'));
      // code_verifier は PKCE が要求する 43〜128 文字の URL 安全文字列
      expect(stored.verifier).toMatch(/^[A-Za-z0-9\-_]{43}$/);
    });

    it('code_challenge は code_verifier の SHA-256 を base64url にしたもの', async () => {
      const url = new URL(await prepareLineAuthorization('channel-1', 'login'));
      const { verifier } = JSON.parse(sessionStorage.getItem(STORAGE_KEY) as string);

      const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
      const expected = btoa(String.fromCharCode(...new Uint8Array(digest)))
        .replace(/\+/g, '-')
        .replace(/\//g, '_')
        .replace(/=+$/, '');

      expect(url.searchParams.get('code_challenge')).toBe(expected);
    });

    it('呼び出しごとに state と code_verifier が変わる', async () => {
      await prepareLineAuthorization('channel-1', 'login');
      const first = JSON.parse(sessionStorage.getItem(STORAGE_KEY) as string);
      await prepareLineAuthorization('channel-1', 'login');
      const second = JSON.parse(sessionStorage.getItem(STORAGE_KEY) as string);

      expect(second.state).not.toBe(first.state);
      expect(second.verifier).not.toBe(first.verifier);
    });

    it('SubtleCrypto が使えない環境（平文HTTP等）では失敗し、保存もしない', async () => {
      const original = globalThis.crypto.subtle;
      Object.defineProperty(globalThis.crypto, 'subtle', {
        value: undefined,
        configurable: true,
      });

      await expect(prepareLineAuthorization('channel-1', 'login')).rejects.toThrow();
      expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();

      Object.defineProperty(globalThis.crypto, 'subtle', { value: original, configurable: true });
    });
  });

  describe('startLineAuthorization', () => {
    // jsdom の location は差し替え不能（unforgeable）で遷移も未実装のため、
    // 遷移そのものは観測できない。ここでは意図の保存までを検証し、
    // 遷移の引数は各画面のテストが startLineAuthorization を差し替えて確認する。
    it('遷移前に意図つきで認可情報を保存する', async () => {
      const consoleError = jest.spyOn(console, 'error').mockImplementation(() => {});

      await startLineAuthorization('channel-1', 'link');

      expect(JSON.parse(sessionStorage.getItem(STORAGE_KEY) as string).intent).toBe('link');

      consoleError.mockRestore();
    });
  });

  describe('consumeLineAuthorization', () => {
    it('state が一致すれば code_verifier と意図を返し、保存値を破棄する', async () => {
      await prepareLineAuthorization('channel-1', 'link');
      const stored = JSON.parse(sessionStorage.getItem(STORAGE_KEY) as string);

      expect(consumeLineAuthorization(stored.state)).toEqual({
        state: stored.state,
        verifier: stored.verifier,
        intent: 'link',
      });
      // 認可要求 1 回に対して引き換えは 1 回きり
      expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
      expect(consumeLineAuthorization(stored.state)).toBeNull();
    });

    it('state が食い違う場合は null（保存値も破棄する）', async () => {
      await prepareLineAuthorization('channel-1', 'login');

      expect(consumeLineAuthorization('別のstate')).toBeNull();
      expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
    });

    it('保存が無い場合は null', () => {
      expect(consumeLineAuthorization('state')).toBeNull();
    });

    it('state を伴わないコールバックは null', async () => {
      await prepareLineAuthorization('channel-1', 'login');

      expect(consumeLineAuthorization(null)).toBeNull();
    });

    it('保存値が壊れている場合は null', () => {
      sessionStorage.setItem(STORAGE_KEY, '{壊れたJSON');
      expect(consumeLineAuthorization('state')).toBeNull();

      sessionStorage.setItem(STORAGE_KEY, '"文字列"');
      expect(consumeLineAuthorization('state')).toBeNull();

      sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ state: 'state', verifier: 1 }));
      expect(consumeLineAuthorization('state')).toBeNull();

      // 未知の意図は受け付けない（分岐先が無い）
      sessionStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ state: 'state', verifier: 'v', intent: 'unknown' })
      );
      expect(consumeLineAuthorization('state')).toBeNull();
    });
  });
});
