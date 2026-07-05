import { render, screen } from '@testing-library/react';
import LoginPage from '../ui/LoginPage';

// Cookie ストアのモック（server-client.test.ts と同パターン）
const mockCookieStore = { get: jest.fn() };
jest.mock('next/headers', () => ({
  cookies: jest.fn(() => Promise.resolve(mockCookieStore)),
}));

// LoginForm はタイトル判定と無関係のためスタブ化
jest.mock('@/features/auth-login', () => ({
  LoginForm: () => <div data-testid="login-form" />,
}));

function setCookies(values: Record<string, string>) {
  mockCookieStore.get.mockImplementation((name: string) =>
    values[name] !== undefined ? { name, value: values[name] } : undefined
  );
}

describe('LoginPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('central ロールでは管理者ログインを表示すること', async () => {
    setCookies({ 'x-mw-role': 'central' });
    render(await LoginPage());
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent('管理者ログイン');
    expect(
      screen.getByText('プラットフォーム管理者アカウントでログインしてください')
    ).toBeInTheDocument();
  });

  it('tenant ロールでは店名をタイトルに表示すること', async () => {
    setCookies({ 'x-mw-role': 'tenant', 'x-mw-tenant-name': 'Sample Tenant' });
    render(await LoginPage());
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent('Sample Tenant');
  });

  it('tenant ロールで店名 Cookie がない場合は店舗ログインを表示すること', async () => {
    setCookies({ 'x-mw-role': 'tenant' });
    render(await LoginPage());
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent('店舗ログイン');
  });

  it('URL エンコードされた店名をデコードして表示すること', async () => {
    setCookies({
      'x-mw-role': 'tenant',
      'x-mw-tenant-name': '%E3%81%8D%E3%81%9A%E3%81%AA',
    });
    render(await LoginPage());
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent('きずな');
  });

  it('ログインフォームを描画すること', async () => {
    setCookies({ 'x-mw-role': 'central' });
    render(await LoginPage());
    expect(screen.getByTestId('login-form')).toBeInTheDocument();
  });
});
