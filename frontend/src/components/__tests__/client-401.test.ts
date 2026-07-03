import Cookies from 'js-cookie';

// mock navigation helper before importing client so that interceptor uses the mock
const redirectMock = jest.fn();
jest.mock('@/shared/lib', () => ({
  redirectToLogin: () => redirectMock(),
}));

import { apiClient } from '@/shared/api';

jest.mock('js-cookie');

describe('apiClient 401 interceptor', () => {
  const originalHref = window.location.href;
  const originalLocation = window.location;
  beforeEach(() => {
    jest.clearAllMocks();
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'token') return 'tkn';
      return undefined;
    });
  });
  afterEach(() => {
    // restore any spies/mocks on location.assign
    jest.restoreAllMocks();
    // restore original URL to avoid affecting other tests
    try {
      window.history.pushState({}, '', originalHref);
    } catch {
      // ignore if pushState fails
    }
  });

  it('clears token and redirects on 401', async () => {
    const removeSpy = jest.spyOn(Cookies, 'remove');
    const original = apiClient.defaults.adapter as any;
    // Let location.assign run (can't reliably spy on it in jsdom v30)
    apiClient.defaults.adapter = (async (_config: any) => {
      const error: any = new Error('Unauthorized');
      error.response = { status: 401 };
      return Promise.reject(error);
    }) as any;

    // avoid infinite loop by calling request to any url
    await expect(apiClient.get('/central/me')).rejects.toBeDefined();
    expect(removeSpy).toHaveBeenCalledWith('token');
    // assert that our navigation helper was called (no real navigation in jsdom)
    expect(redirectMock).toHaveBeenCalled();

    apiClient.defaults.adapter = original;
  });
});
