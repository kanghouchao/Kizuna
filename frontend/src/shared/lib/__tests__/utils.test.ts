import { cn } from '@/shared/lib';

describe('cn', () => {
  it('マージと重複解決を行う', () => {
    expect(cn('p-2', 'p-4')).toBe('p-4');
    expect(cn('text-sm', false && 'hidden', 'font-bold')).toBe('text-sm font-bold');
  });
});
