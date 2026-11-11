import Cookies from 'js-cookie';
import { clearSession, getSessionKind, startSession } from '../session';

describe('session', () => {
  afterEach(() => {
    clearSession();
  });

  it('startSession sets the session-kind cookie, readable via getSessionKind', () => {
    startSession('admin');
    expect(getSessionKind()).toBe('admin');
  });

  it('clearSession removes the session-kind cookie', () => {
    startSession('admin');
    clearSession();
    expect(getSessionKind()).toBeUndefined();
  });

  it('startSession sets the session-kind cookie with the same expiry as expiresAt', () => {
    const setSpy = jest.spyOn(Cookies, 'set');
    const expiresAt = Date.now() + 60_000;

    startSession('admin', expiresAt);

    expect(setSpy).toHaveBeenCalledWith('session-kind', 'admin', {
      expires: new Date(expiresAt),
    });
    setSpy.mockRestore();
  });
});
