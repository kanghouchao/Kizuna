'use client';

import Cookies from 'js-cookie';
import { LoginForm } from '@/features/auth-login';
import { isTenantDomain } from '@/shared/lib';
import { AuthLayout } from '@/shared/ui';

export default function LoginPage() {
  const isTenant = isTenantDomain();
  const tenantName = Cookies.get('x-mw-tenant-name');
  const pageTitle = isTenant ? (tenantName ? `${tenantName}` : '店舗ログイン') : '管理者ログイン';
  const pageSubtitle = isTenant
    ? '店舗アカウントでログインしてください'
    : 'プラットフォーム管理者アカウントでログインしてください';

  return (
    <AuthLayout title={pageTitle} subtitle={pageSubtitle}>
      <LoginForm />
      {/* フッター */}
      <p className="auth-footer mt-12 text-center">ご不明点はKIZUNAサポートまでご連絡ください</p>
    </AuthLayout>
  );
}
