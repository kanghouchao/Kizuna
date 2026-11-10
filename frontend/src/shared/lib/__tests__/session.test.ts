import Cookies from 'js-cookie';
import {
  clearSession,
  getSessionKind,
  getCurrentStoreId,
  setCurrentStore,
  startSession,
} from '../session';

describe('session', () => {
  afterEach(() => {
    clearSession();
  });

  it('startSession sets the session-kind cookie, readable via getSessionKind', () => {
    startSession('admin');
    expect(getSessionKind()).toBe('admin');
  });

  it('setCurrentStore sets the current-store-id cookie, readable via getCurrentStoreId', () => {
    setCurrentStore(2);
    expect(getCurrentStoreId()).toBe('2');
  });

  it('clearSession removes both cookies', () => {
    startSession('admin');
    setCurrentStore(1);
    clearSession();
    expect(getSessionKind()).toBeUndefined();
    expect(getCurrentStoreId()).toBeUndefined();
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

  it('setCurrentStore sets the current-store-id cookie with the same expiry as expiresAt', () => {
    const setSpy = jest.spyOn(Cookies, 'set');
    const expiresAt = Date.now() + 60_000;

    setCurrentStore(2, expiresAt);

    expect(setSpy).toHaveBeenCalledWith('current-store-id', '2', { expires: new Date(expiresAt) });
    setSpy.mockRestore();
  });
});
