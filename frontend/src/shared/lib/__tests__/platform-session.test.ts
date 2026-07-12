import {
  clearPlatformSession,
  getPlatformRole,
  getPlatformStoreId,
  isPlatformSession,
  isStoreRole,
  setPlatformStore,
  startPlatformSession,
} from '../platform-session';

describe('platform-session', () => {
  afterEach(() => {
    clearPlatformSession();
  });

  it('startPlatformSession sets the platform-role cookie, readable via getPlatformRole', () => {
    startPlatformSession('HQ_ADMIN');
    expect(getPlatformRole()).toBe('HQ_ADMIN');
  });

  it('setPlatformStore sets the platform-store-id cookie, readable via getPlatformStoreId', () => {
    setPlatformStore(2);
    expect(getPlatformStoreId()).toBe('2');
  });

  it('isPlatformSession is true only when platform-role cookie is present', () => {
    expect(isPlatformSession()).toBe(false);
    startPlatformSession('STORE_MANAGER');
    expect(isPlatformSession()).toBe(true);
  });

  it('clearPlatformSession removes both cookies', () => {
    startPlatformSession('HQ_ADMIN');
    setPlatformStore(1);
    clearPlatformSession();
    expect(getPlatformRole()).toBeUndefined();
    expect(getPlatformStoreId()).toBeUndefined();
  });

  it('isStoreRole is true for STORE_MANAGER and STORE_STAFF only', () => {
    expect(isStoreRole('STORE_MANAGER')).toBe(true);
    expect(isStoreRole('STORE_STAFF')).toBe(true);
    expect(isStoreRole('HQ_ADMIN')).toBe(false);
    expect(isStoreRole('CAST')).toBe(false);
    expect(isStoreRole(undefined)).toBe(false);
  });
});
