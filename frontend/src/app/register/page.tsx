'use client';

import { useState, useEffect, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { authApi } from '@/services/tenant/api';
import { isTenantDomain } from '@/lib/config';
import AuthLayout from '@/components/auth/AuthLayout';

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
  const [token, setToken] = useState('');
  const [email, setEmail] = useState('');

  useEffect(() => {
    // Centralドメインからのアクセスは登録不可
    if (!isTenantDomain()) {
      router.replace('/login');
      return;
    }
    setToken(searchParams?.get('token') || '');
  }, [searchParams, router]);

  const resolveLoginUrl = (loginUrl?: string | null, domain?: string | null) => {
    if (loginUrl) return loginUrl.trim();
    if (!domain) return null;
    const sanitizedDomain = domain.trim().replace(/\/+$/g, '');
    if (!sanitizedDomain) return null;
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
            console.error('テナントログインへのリダイレクトに失敗', navigationError);
          }
        }
        fallback();
      };
      setTimeout(navigate, 1500);
    } catch (err) {
      console.error('テナント登録に失敗', err);
      const message =
        (err as any)?.response?.data?.message ||
        '登録に失敗しました。リンクの有効期限や入力内容をご確認のうえ、必要に応じてサポートへご連絡ください。';
      setError(message);
    }
  };

  return (
    <AuthLayout title="管理者登録" subtitle="アカウント情報を設定してください">
      <form onSubmit={handleSubmit} className="space-y-7">
        {/* メールアドレス */}
        <div className="auth-field">
          <label
            htmlFor="email"
            className="block text-xs font-medium text-[#8a8580] uppercase tracking-wider mb-1.5"
          >
            メールアドレス
          </label>
          <input
            type="email"
            name="email"
            id="email"
            placeholder="example@mail.com"
            value={email}
            onChange={e => setEmail(e.target.value)}
            className="auth-field__input"
            required
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
            type="password"
            name="password"
            id="password"
            placeholder="8文字以上で設定"
            value={password}
            onChange={e => setPassword(e.target.value)}
            className="auth-field__input"
            required
            minLength={8}
          />
          <span className="auth-field__accent" />
        </div>

        {/* パスワード確認 */}
        <div className="auth-field">
          <label
            htmlFor="confirm-password"
            className="block text-xs font-medium text-[#8a8580] uppercase tracking-wider mb-1.5"
          >
            パスワード（確認用）
          </label>
          <input
            type="password"
            name="confirm-password"
            id="confirm-password"
            placeholder="パスワードを再入力"
            value={confirmPassword}
            onChange={e => setConfirmPassword(e.target.value)}
            className="auth-field__input"
            required
            minLength={8}
          />
          <span className="auth-field__accent" />
        </div>

        {/* 登録ボタン */}
        <div className="pt-2">
          <button type="submit" className="auth-btn">
            登録を完了する
          </button>
        </div>
      </form>

      {/* エラーメッセージ */}
      {error && (
        <div className="auth-alert auth-alert--error mt-6">
          <p>{error}</p>
        </div>
      )}

      {/* 成功メッセージ */}
      {success && (
        <div className="mt-6 space-y-3">
          <div className="auth-alert auth-alert--success">
            <p>{success}</p>
          </div>
          {redirectUrl && (
            <div className="text-center">
              <button
                type="button"
                className="auth-link text-sm"
                onClick={() => window.location.assign(redirectUrl)}
              >
                すぐにログインページへ移動する
              </button>
            </div>
          )}
        </div>
      )}

      {/* フッター */}
      <p className="auth-footer mt-12 text-center">ご不明点はKIZUNAサポートまでご連絡ください</p>
    </AuthLayout>
  );
}
