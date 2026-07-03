'use client';

import { useState } from 'react';
import { toast } from 'react-hot-toast';
import { centralAuthApi, storeAuthApi, useAuth } from '@/entities/user';
import { isTenantDomain } from '@/shared/lib';

// axios エラーからサーバーのバリデーションメッセージを取り出す
function errorMessage(error: unknown): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const data = (error as { response?: { data?: { error?: string } } }).response?.data;
    if (data?.error) return data.error;
  }
  return 'パスワードの変更に失敗しました';
}

/** パスワード変更フォーム。成功するとトークンが失効するため、ログアウトして再ログインを促す。 */
export function PasswordChangeForm() {
  const { logout } = useAuth();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (newPassword !== confirmPassword) {
      toast.error('新しいパスワードが一致しません');
      return;
    }
    if (newPassword.length < 8) {
      toast.error('新しいパスワードは8文字以上で入力してください');
      return;
    }
    setIsSubmitting(true);
    try {
      const api = isTenantDomain() ? storeAuthApi : centralAuthApi;
      await api.changePassword({
        current_password: currentPassword,
        new_password: newPassword,
      });
      toast.success('パスワードを変更しました。再度ログインしてください');
      logout();
    } catch (error) {
      toast.error(errorMessage(error));
      setIsSubmitting(false);
    }
  };

  return (
    <form
      onSubmit={handleSubmit}
      className="bg-white p-8 rounded-xl shadow-sm border border-gray-100 space-y-6"
    >
      <h3 className="text-lg font-semibold text-gray-900 border-l-4 border-indigo-500 pl-3">
        パスワード変更
      </h3>
      <p className="text-sm text-gray-500">
        変更後は自動的にログアウトされ、新しいパスワードでの再ログインが必要です。
      </p>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">現在のパスワード *</label>
          <input
            type="password"
            value={currentPassword}
            onChange={e => setCurrentPassword(e.target.value)}
            required
            autoComplete="current-password"
            className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            新しいパスワード（8文字以上）*
          </label>
          <input
            type="password"
            value={newPassword}
            onChange={e => setNewPassword(e.target.value)}
            required
            minLength={8}
            autoComplete="new-password"
            className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            新しいパスワード（確認）*
          </label>
          <input
            type="password"
            value={confirmPassword}
            onChange={e => setConfirmPassword(e.target.value)}
            required
            minLength={8}
            autoComplete="new-password"
            className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
          />
        </div>
      </div>
      <div className="flex justify-end">
        <button
          type="submit"
          disabled={isSubmitting}
          className="px-10 py-2.5 rounded-md bg-indigo-600 text-white font-semibold shadow-lg shadow-indigo-200 hover:bg-indigo-700 disabled:opacity-50 transition-all"
        >
          {isSubmitting ? '変更中...' : 'パスワードを変更する'}
        </button>
      </div>
    </form>
  );
}
