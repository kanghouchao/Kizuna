'use client';

import { LineLinkSection } from '@/features/line-auth';
import { PasswordChangeForm } from '@/features/password-change';

/** アカウント設定ページ（パスワード変更・LINE連携） */
export default function AccountPage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">アカウント設定</h1>
        <p className="text-sm text-muted-foreground mt-1">
          自分のパスワードとLINE連携を管理します。
        </p>
      </div>
      <PasswordChangeForm />
      <LineLinkSection />
    </div>
  );
}
