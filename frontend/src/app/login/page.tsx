'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Cookies from 'js-cookie';
import { centralAuthApi, storeAuthApi } from '@/entities/user';
import toast from 'react-hot-toast';
import { isTenantDomain } from '@/shared/lib';
import AuthLayout from '@/components/auth/AuthLayout';

function getAuthApi() {
  return isTenantDomain() ? storeAuthApi : centralAuthApi;
}

export default function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  const router = useRouter();

  const isTenant = isTenantDomain();
  const tenantName = Cookies.get('x-mw-tenant-name');
  const pageTitle = isTenant ? (tenantName ? `${tenantName}` : '店舗ログイン') : '管理者ログイン';
  const pageSubtitle = isTenant
    ? '店舗アカウントでログインしてください'
    : 'プラットフォーム管理者アカウントでログインしてください';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    // 期限切れトークンがログインを妨げないように削除
    Cookies.remove('token');
    try {
      const response = await getAuthApi().login({ username, password });
      if (response.token && response.expires_at) {
        Cookies.set('token', response.token, { expires: response.expires_at });
        router.push(isTenant ? '/tenant/dashboard/' : '/central/dashboard/');
      }
    } catch (error) {
      console.error('Login failed:', error);
      toast.error('ログインに失敗しました。しばらくしてから再度お試しください');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthLayout title={pageTitle} subtitle={pageSubtitle}>
      <form onSubmit={handleSubmit} className="space-y-7">
        {/* ログイン名 */}
        <div className="auth-field">
          <label
            htmlFor="username"
            className="block text-xs font-medium text-[#8a8580] uppercase tracking-wider mb-1.5"
          >
            ログイン名
          </label>
          <input
            id="username"
            name="username"
            type="text"
            autoComplete="username"
            required
            value={username}
            onChange={e => setUsername(e.target.value)}
            className="auth-field__input"
            placeholder="ユーザー名を入力"
          />
          <span className="auth-field__accent" />
        </div>

        {/* パスワード */}
        <div className="auth-field">
          <label
            htmlFor="password"
            className="block text-xs font-medium text-[#8a8580] uppercase tracking-wider mb-1.5"
          >
            パスワード
          </label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={e => setPassword(e.target.value)}
            className="auth-field__input"
            placeholder="パスワードを入力"
          />
          <span className="auth-field__accent" />
        </div>

        {/* ログイン状態保持 */}
        <div className="flex items-center pt-1">
          <input
            id="remember-me"
            name="remember-me"
            type="checkbox"
            checked={rememberMe}
            onChange={e => setRememberMe(e.target.checked)}
            className="auth-check"
          />
          <label htmlFor="remember-me" className="ml-2.5 text-sm text-[#6b6560] select-none">
            ログイン状態を保持
          </label>
        </div>

        {/* ログインボタン */}
        <div className="pt-2">
          <button type="submit" disabled={isLoading} className="auth-btn">
            {isLoading ? (
              <span className="flex items-center justify-center gap-2.5">
                <span className="auth-spinner" />
                ログイン中...
              </span>
            ) : (
              'ログイン'
            )}
          </button>
        </div>
      </form>

      {/* フッター */}
      <p className="auth-footer mt-12 text-center">ご不明点はKIZUNAサポートまでご連絡ください</p>
    </AuthLayout>
  );
}
