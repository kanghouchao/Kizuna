import React from 'react';
import { render, waitFor } from '@testing-library/react';
import Cookies from 'js-cookie';
import { Sidebar } from '../Sidebar';
import { menuApi } from '@/entities/menu';

jest.mock('js-cookie');

jest.mock('next/navigation', () => ({
  usePathname: () => '/store/dashboard',
}));

jest.mock('@/entities/menu', () => ({
  menuApi: { getMenus: jest.fn().mockResolvedValue([]) },
}));

describe('Sidebar', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('store コンソール cookie でも同一 menuApi が呼ばれる', async () => {
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'platform-role') return 'store';
      return undefined;
    });

    render(<Sidebar />);

    await waitFor(() => expect(menuApi.getMenus).toHaveBeenCalled());
    expect(menuApi.getMenus).toHaveBeenCalledTimes(1);
  });

  it('central コンソール cookie でも同一 menuApi が呼ばれる', async () => {
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'platform-role') return 'central';
      return undefined;
    });

    render(<Sidebar />);

    await waitFor(() => expect(menuApi.getMenus).toHaveBeenCalled());
    expect(menuApi.getMenus).toHaveBeenCalledTimes(1);
  });
});
