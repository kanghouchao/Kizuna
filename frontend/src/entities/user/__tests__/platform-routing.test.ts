import { resolvePlatformDestination } from '@/entities/user';

describe('resolvePlatformDestination', () => {
  it('routes HQ_ADMIN to central', () => {
    expect(resolvePlatformDestination('HQ_ADMIN')).toBe('central');
  });
  it('routes STORE_MANAGER to store', () => {
    expect(resolvePlatformDestination('STORE_MANAGER')).toBe('store');
  });
  it('routes STORE_STAFF to store', () => {
    expect(resolvePlatformDestination('STORE_STAFF')).toBe('store');
  });
  it('routes CAST to unsupported', () => {
    expect(resolvePlatformDestination('CAST')).toBe('unsupported');
  });
  it('routes MEMBER to unsupported', () => {
    expect(resolvePlatformDestination('MEMBER')).toBe('unsupported');
  });
});
