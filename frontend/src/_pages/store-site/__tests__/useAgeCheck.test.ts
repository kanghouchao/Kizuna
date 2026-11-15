import { act, renderHook } from '@testing-library/react';
import { useAgeCheck } from '../model/useAgeCheck';

const STORAGE_KEY = 'kizuna_age_verified';

describe('useAgeCheck', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('同意の記録が無ければ確認を求める', () => {
    const { result } = renderHook(() => useAgeCheck());

    expect(result.current.phase).toBe('gate');
  });

  it('同意の記録があれば確認済みとして扱う', () => {
    localStorage.setItem(STORAGE_KEY, 'true');

    const { result } = renderHook(() => useAgeCheck());

    expect(result.current.phase).toBe('verified');
  });

  it('同意すると記録し、次の訪問では確認を求めない', () => {
    const first = renderHook(() => useAgeCheck());

    act(() => first.result.current.accept());

    expect(first.result.current.phase).toBe('verified');
    expect(localStorage.getItem(STORAGE_KEY)).toBe('true');
    expect(renderHook(() => useAgeCheck()).result.current.phase).toBe('verified');
  });

  it('拒否は記録せず、次の訪問でまた確認を求める', () => {
    const first = renderHook(() => useAgeCheck());

    act(() => first.result.current.decline());

    expect(first.result.current.phase).toBe('declined');
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
    expect(renderHook(() => useAgeCheck()).result.current.phase).toBe('gate');
  });

  it('拒否した来訪者はウィンドウを閉じて立ち去れる', () => {
    const close = jest.spyOn(window, 'close').mockImplementation(() => {});
    const { result } = renderHook(() => useAgeCheck());

    act(() => result.current.leave());

    expect(close).toHaveBeenCalled();
    close.mockRestore();
  });
});
