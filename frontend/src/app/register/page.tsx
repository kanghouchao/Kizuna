'use client';

import { useState, useEffect, Suspense } from 'react';
import Image from 'next/image';
import { useRouter, useSearchParams } from 'next/navigation';
import { authApi } from '@/services/tenant/api';
import { isTenantDomain } from '@/lib/config';

export default function RegisterPage() {
  return (
    <Suspense>
      <RegisterForm />
    </Suspense>
  );
}

function RegisterForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [redirectUrl, setRedirectUrl] = useState<string | null>(null);
  const [token, setToken] = useState<string>('');
  const [email, setEmail] = useState<string>('');

  useEffect(() => {
    // If accessed from Central domain, registration is not allowed.
    if (!isTenantDomain()) {
      router.replace('/login');
      return;
    }
    setToken(searchParams?.get('token') || '');
  }, [searchParams, router]);

  const resolveLoginUrl = (loginUrl?: string | null, domain?: string | null) => {
    if (loginUrl) {
      return loginUrl.trim();
    }

    if (!domain) {
      return null;
    }

    const sanitizedDomain = domain.trim().replace(/\/+$/g, '');
    if (!sanitizedDomain) {
      return null;
    }

    const hasProtocol = /^https?:\/\//i.test(sanitizedDomain);
    const base = hasProtocol
      ? sanitizedDomain
      : `${typeof window !== 'undefined' ? window.location.protocol : 'https:'}//${sanitizedDomain}`;
    return base.endsWith('/login') ? base : `${base}/login`;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    setRedirectUrl(null);
    if (!token) {
      setError(
        'リンクが無効、またはトークンが不足しています。メールのリンクから再度アクセスしてください。'
      );
      return;
    }
    if (password.length < 8) {
      setError('パスワードは8文字以上で入力してください。');
      return;
    }
    if (password !== confirmPassword) {
      setError('パスワードが一致しません。もう一度ご確認ください。');
      return;
    }
    try {
      const response = await authApi.register({ token, email, password });
      const tenantLabel = response.tenant_name || response.tenant_domain || '店舗';
      const nextUrl = resolveLoginUrl(response.login_url, response.tenant_domain);
      setRedirectUrl(nextUrl || null);
      setSuccess(`${tenantLabel} のログインページへリダイレクトします。数秒お待ちください。`);
      const fallback = () => router.push('/login');
      const navigate = () => {
        if (nextUrl) {
          try {
            window.location.assign(nextUrl);
            return;
          } catch (navigationError) {
            console.error('Failed to redirect to tenant login', navigationError);
          }
        }
        fallback();
      };
      setTimeout(navigate, 1500);
    } catch (err) {
      console.error('Tenant registration failed', err);
      const message =
        (err as any)?.response?.data?.message ||
        '登録に失敗しました。リンクの有効期限や入力内容をご確認のうえ、必要に応じてサポートへご連絡ください。';
      setError(message);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-50 to-blue-200">
      <div className="w-full max-w-md p-8 bg-white rounded-xl shadow-lg border border-blue-100">
        <div className="flex items-center mb-6">
          {/* Next.js Image 优化图片加载 */}
          <Image
            src="/images/logos/32.svg"
            alt="KIZUNA Logo"
            width={40}
            height={40}
            className="h-10 mr-3"
          />
          <h2 className="text-2xl font-bold text-blue-700">KIZUNA 管理者登録</h2>
        </div>
        <form onSubmit={handleSubmit} className="space-y-6">
          <div>
            <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-2">
              メールアドレス
            </label>
            <input
              type="email"
              name="email"
              id="email"
              placeholder="メールアドレス"
              value={email}
              onChange={e => setEmail(e.target.value)}
              className="w-full px-4 py-2 border rounded focus:outline-none focus:ring-2 focus:ring-blue-400"
              required
            />
          </div>
          <div>
            <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-2">
              ログイン用パスワード設定
            </label>
            <input
              type="password"
              name="password"
              id="password"
              placeholder="パスワード（8文字以上）"
              value={password}
              onChange={e => setPassword(e.target.value)}
              className="w-full px-4 py-2 border rounded focus:outline-none focus:ring-2 focus:ring-blue-400"
              required
              minLength={8}
            />
          </div>
          <div>
            <label
              htmlFor="confirm-password"
              className="block text-sm font-medium text-gray-700 mb-2"
            >
              パスワード（確認用）
            </label>
            <input
              type="password"
              name="confirm-password"
              id="confirm-password"
              placeholder="パスワード（確認用）"
              value={confirmPassword}
              onChange={e => setConfirmPassword(e.target.value)}
              className="w-full px-4 py-2 border rounded focus:outline-none focus:ring-2 focus:ring-blue-400"
              required
              minLength={8}
            />
          </div>
          <button
            type="submit"
            className="w-full py-2 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded transition"
          >
            登録を完了する
          </button>
        </form>
        {error && <div className="text-red-500 mt-4 text-center">{error}</div>}
        {success && (
          <div className="text-green-600 mt-4 text-center space-y-2">
            <div>{success}</div>
            {redirectUrl && (
              <button
                type="button"
                className="text-sm text-blue-600 hover:underline"
                onClick={() => window.location.assign(redirectUrl)}
              >
                すぐにログインページへ移動する
              </button>
            )}
          </div>
        )}
        <div className="mt-8 text-xs text-gray-400 text-center">
          ご不明点はKIZUNAサポートまでご連絡ください
        </div>
      </div>
    </div>
  );
}
