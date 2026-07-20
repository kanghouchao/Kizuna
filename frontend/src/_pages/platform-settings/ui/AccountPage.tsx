'use client';

import { PasswordChangeForm } from '@/features/password-change';

/** アカウント設定ページ（パスワード変更） */
export default function AccountPage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">アカウント設定</h1>
        <p className="text-sm text-gray-500 mt-1">自分のパスワードを管理します。</p>
      </div>
      <PasswordChangeForm />
    </div>
  );
}
