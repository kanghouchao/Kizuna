import redirectToLogin, { __setNavigatorForTests, __resetNavigator } from '../navigation';
import { clearPlatformSession, startPlatformSession } from '../platform-session';

describe('navigation helper', () => {
  afterEach(() => {
    __resetNavigator();
    jest.restoreAllMocks();
    clearPlatformSession();
  });

  it('calls provided navigator with /login when available', () => {
    const navMock = jest.fn();
    __setNavigatorForTests(navMock);
    redirectToLogin();
    expect(navMock).toHaveBeenCalledWith('/login');
  });

  it('does not throw when provided navigator throws (caught)', () => {
    const badNav = jest.fn(() => {
      throw new Error('boom');
    });
    __setNavigatorForTests(badNav);
    expect(() => redirectToLogin()).not.toThrow();
  });

  it('calls provided navigator with /platform/login when platform-role cookie is present', () => {
    startPlatformSession('HQ_ADMIN');
    const navMock = jest.fn();
    __setNavigatorForTests(navMock);
    redirectToLogin();
    expect(navMock).toHaveBeenCalledWith('/platform/login');
  });
});
