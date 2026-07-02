import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import SystemSettingsPage from '../page';

const mockGetAllConfigs = jest.fn();
const mockUpdateConfig = jest.fn();

jest.mock('@/services/central/config', () => ({
  systemConfigService: {
    getAllConfigs: (...args: unknown[]) => mockGetAllConfigs(...args),
    updateConfig: (...args: unknown[]) => mockUpdateConfig(...args),
  },
}));

jest.mock('react-hot-toast', () => ({
  toast: { success: jest.fn(), error: jest.fn() },
}));

const configs = [
  {
    id: 1,
    config_key: 'maintenance_mode',
    config_value: 'false',
    value_type: 'BOOLEAN',
    secret: false,
    category: 'SYSTEM',
    description: 'システムメンテナンスモード',
    created_at: '',
    updated_at: '',
  },
  {
    id: 2,
    config_key: 'smtp_port',
    config_value: '25',
    value_type: 'NUMBER',
    secret: false,
    category: 'SMTP',
    description: 'SMTPサーバーポート',
    created_at: '',
    updated_at: '',
  },
  {
    id: 3,
    config_key: 'smtp_password',
    value_type: 'STRING',
    secret: true,
    category: 'SMTP',
    description: 'SMTP認証パスワード',
    created_at: '',
    updated_at: '',
  },
];

describe('SystemSettingsPage', () => {
  beforeEach(() => {
    mockGetAllConfigs.mockResolvedValue(configs);
    mockUpdateConfig.mockResolvedValue({});
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('真偽値設定はトグルで表示され、クリックで更新される', async () => {
    render(<SystemSettingsPage />);
    const toggle = await screen.findByRole('switch', { name: 'maintenance_mode' });
    expect(toggle).toHaveAttribute('aria-checked', 'false');

    fireEvent.click(toggle);

    await waitFor(() =>
      expect(mockUpdateConfig).toHaveBeenCalledWith({
        config_key: 'maintenance_mode',
        config_value: 'true',
      })
    );
  });

  it('秘匿設定は値がマスク表示される', async () => {
    render(<SystemSettingsPage />);
    expect(await screen.findByText('(秘匿設定)')).toBeInTheDocument();
  });

  it('数値設定は編集時に数値入力欄になる', async () => {
    render(<SystemSettingsPage />);
    fireEvent.click(await screen.findByText('25'));
    expect(screen.getByRole('spinbutton')).toBeInTheDocument();
  });
});
