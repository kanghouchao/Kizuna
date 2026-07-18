import Cookies from 'js-cookie';
import {
  clearPlatformSession,
  getPlatformConsole,
  getPlatformStoreId,
  isPlatformSession,
  isStoreConsole,
  setPlatformStore,
  startPlatformSession,
} from '../platform-session';

describe('platform-session', () => {
  afterEach(() => {
    clearPlatformSession();
  });

  it('startPlatformSession sets the platform-role cookie, readable via getPlatformConsole', () => {
    startPlatformSession('central');
    expect(getPlatformConsole()).toBe('central');
  });

  it('setPlatformStore sets the platform-store-id cookie, readable via getPlatformStoreId', () => {
    setPlatformStore(2);
    expect(getPlatformStoreId()).toBe('2');
  });

  it('isPlatformSession is true only when platform-role cookie is present', () => {
    expect(isPlatformSession()).toBe(false);
    startPlatformSession('store');
    expect(isPlatformSession()).toBe(true);
  });

  it('clearPlatformSession removes both cookies', () => {
    startPlatformSession('central');
    setPlatformStore(1);
    clearPlatformSession();
    expect(getPlatformConsole()).toBeUndefined();
    expect(getPlatformStoreId()).toBeUndefined();
  });

  it('isStoreConsole is true only for the store console value', () => {
    expect(isStoreConsole('store')).toBe(true);
    expect(isStoreConsole('central')).toBe(false);
    expect(isStoreConsole('none')).toBe(false);
    // 旧形式（ロール名）の cookie が残っていても店舗文脈を確立しない（fail-closed）。
    expect(isStoreConsole('STORE_MANAGER')).toBe(false);
    expect(isStoreConsole(undefined)).toBe(false);
  });

  it('startPlatformSession sets the platform-role cookie with the same expiry as expiresAt', () => {
    const setSpy = jest.spyOn(Cookies, 'set');
    const expiresAt = Date.now() + 60_000;

    startPlatformSession('central', expiresAt);

    expect(setSpy).toHaveBeenCalledWith('platform-role', 'central', {
      expires: new Date(expiresAt),
    });
    setSpy.mockRestore();
  });

  it('setPlatformStore sets the platform-store-id cookie with the same expiry as expiresAt', () => {
    const setSpy = jest.spyOn(Cookies, 'set');
    const expiresAt = Date.now() + 60_000;

    setPlatformStore(2, expiresAt);

    expect(setSpy).toHaveBeenCalledWith('platform-store-id', '2', { expires: new Date(expiresAt) });
    setSpy.mockRestore();
  });
});
