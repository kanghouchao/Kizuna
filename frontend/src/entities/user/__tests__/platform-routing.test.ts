import { resolvePlatformDestination } from '@/entities/user';

describe('resolvePlatformDestination', () => {
  it('routes central console to central', () => {
    expect(resolvePlatformDestination('central')).toBe('central');
  });
  it('routes store console to store', () => {
    expect(resolvePlatformDestination('store')).toBe('store');
  });
  it('routes none console (CAST/MEMBER or capability-less) to unsupported', () => {
    expect(resolvePlatformDestination('none')).toBe('unsupported');
  });
  it('routes unknown cookie values to unsupported (fail-closed)', () => {
    expect(resolvePlatformDestination('HQ_ADMIN' as never)).toBe('unsupported');
  });
});
