import Cookies from 'js-cookie';
import { extendSession } from '../session';

describe('extendSession', () => {
  afterEach(() => {
    Cookies.remove('session-kind');
    jest.restoreAllMocks();
  });

  it('セッション種別の cookie だけを延長し、種別でない値は延長しない', () => {
    const set = jest.spyOn(Cookies, 'set');
    Cookies.set('session-kind', 'cast');
    set.mockClear();
    extendSession(2_000);
    expect(set).toHaveBeenCalledWith('session-kind', 'cast', { expires: new Date(2_000) });

    Cookies.set('session-kind', 'staff');
    set.mockClear();
    extendSession(3_000);
    expect(set).not.toHaveBeenCalled();
  });
});
