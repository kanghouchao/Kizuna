import redirectToLogin, { __setNavigatorForTests, __resetNavigator } from '../navigation';
import { clearSession } from '../session';

describe('navigation helper', () => {
  afterEach(() => {
    __resetNavigator();
    jest.restoreAllMocks();
    clearSession();
  });

  it('calls provided navigator with /auth/login when available', () => {
    const navMock = jest.fn();
    __setNavigatorForTests(navMock);
    redirectToLogin();
    expect(navMock).toHaveBeenCalledWith('/auth/login');
  });

  it('理由を渡すと理由コードを載せたログイン画面へ送る', () => {
    const navMock = jest.fn();
    __setNavigatorForTests(navMock);
    redirectToLogin('expired');
    expect(navMock).toHaveBeenCalledWith('/auth/login?reason=expired');
  });

  it('does not throw when provided navigator throws (caught)', () => {
    const badNav = jest.fn(() => {
      throw new Error('boom');
    });
    __setNavigatorForTests(badNav);
    expect(() => redirectToLogin()).not.toThrow();
  });
});
