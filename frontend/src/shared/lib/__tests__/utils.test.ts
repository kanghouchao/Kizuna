import { cn } from '@/shared/lib';

describe('cn', () => {
  it('マージと重複解決を行う', () => {
    const hidden: boolean = false;
    expect(cn('p-2', 'p-4')).toBe('p-4');
    expect(cn('text-sm', hidden && 'hidden', 'font-bold')).toBe('text-sm font-bold');
  });
});
