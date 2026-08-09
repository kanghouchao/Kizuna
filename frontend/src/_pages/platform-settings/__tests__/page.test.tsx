import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { notify } from '@/shared/notify';
import SystemSettingsPage from '../ui/SettingsPage';

const mockGetAllConfigs = jest.fn();
const mockUpdateConfig = jest.fn();

jest.mock('@/entities/system-config', () => ({
  systemConfigService: {
    getAllConfigs: (...args: unknown[]) => mockGetAllConfigs(...args),
    updateConfig: (...args: unknown[]) => mockUpdateConfig(...args),
  },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
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

  it('編集した値は config_key と config_value のペイロードで保存される', async () => {
    render(<SystemSettingsPage />);
    fireEvent.click(await screen.findByText('25'));
    fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '587' } });
    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() =>
      expect(mockUpdateConfig).toHaveBeenCalledWith({
        config_key: 'smtp_port',
        config_value: '587',
      })
    );
  });

  it('秘匿設定の編集はマスク入力欄が空欄の状態から始まる', async () => {
    render(<SystemSettingsPage />);
    fireEvent.click(await screen.findByText('(秘匿設定)'));

    const input = screen.getByPlaceholderText('新しい値を入力');
    expect(input).toHaveAttribute('type', 'password');
    expect(input).toHaveValue('');
  });

  it('取得に失敗したら「設定項目がありません」ではなく一覧の場所が失敗を名乗り、再試行で回復する', async () => {
    mockGetAllConfigs.mockRejectedValueOnce(new Error('boom'));

    render(<SystemSettingsPage />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('設定の取得に失敗しました')).toBeInTheDocument();
    expect(screen.queryByText('設定項目がありません。')).not.toBeInTheDocument();
    expect(notify.error).not.toHaveBeenCalled();

    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByRole('switch', { name: 'maintenance_mode' })).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('文字列設定は編集時に複数行入力欄になり現在値が初期表示される', async () => {
    mockGetAllConfigs.mockResolvedValue([
      ...configs,
      {
        id: 4,
        config_key: 'site_name',
        config_value: 'キズナ',
        value_type: 'STRING',
        secret: false,
        category: 'SYSTEM',
        description: 'サイト名',
        created_at: '',
        updated_at: '',
      },
    ]);

    render(<SystemSettingsPage />);
    fireEvent.click(await screen.findByText('キズナ'));

    const textarea = screen.getByRole('textbox');
    expect(textarea.tagName).toBe('TEXTAREA');
    expect(textarea).toHaveValue('キズナ');
  });
});
