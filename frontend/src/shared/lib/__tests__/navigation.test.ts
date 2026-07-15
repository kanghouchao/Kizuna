import redirectToLogin, { __setNavigatorForTests, __resetNavigator } from '../navigation';
import { clearPlatformSession } from '../platform-session';

describe('navigation helper', () => {
  afterEach(() => {
    __resetNavigator();
    jest.restoreAllMocks();
    clearPlatformSession();
  });

  it('calls provided navigator with /platform/login when available', () => {
    const navMock = jest.fn();
    __setNavigatorForTests(navMock);
    redirectToLogin();
    expect(navMock).toHaveBeenCalledWith('/platform/login');
  });

  it('does not throw when provided navigator throws (caught)', () => {
    const badNav = jest.fn(() => {
      throw new Error('boom');
    });
    __setNavigatorForTests(badNav);
    expect(() => redirectToLogin()).not.toThrow();
  });
});
