import redirectToLogin, { __setNavigatorForTests, __resetNavigator } from '../navigation';

describe('navigation helper', () => {
  afterEach(() => {
    __resetNavigator();
    jest.restoreAllMocks();
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
});
