import React from 'react';
import { render, waitFor } from '@testing-library/react';
import Cookies from 'js-cookie';
import { Sidebar } from '../Sidebar';
import { centralMenuApi, storeMenuApi } from '@/entities/menu';

jest.mock('js-cookie');

jest.mock('next/navigation', () => ({
  usePathname: () => '/tenant/dashboard',
}));

jest.mock('@/entities/menu', () => ({
  centralMenuApi: { getMenus: jest.fn().mockResolvedValue([]) },
  storeMenuApi: { getMenus: jest.fn().mockResolvedValue([]) },
}));

describe('Sidebar', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('does not fetch central menus when a store platform-role cookie is present — only store menus are fetched (#324)', async () => {
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'platform-role') return 'STORE_MANAGER';
      return undefined;
    });

    render(<Sidebar />);

    await waitFor(() => expect(storeMenuApi.getMenus).toHaveBeenCalledTimes(1));

    expect(centralMenuApi.getMenus).not.toHaveBeenCalled();
  });
});
