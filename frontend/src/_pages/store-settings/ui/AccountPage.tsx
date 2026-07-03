'use client';

import { useEffect, useState } from 'react';
import { toast } from 'react-hot-toast';
import { storeAuthApi } from '@/entities/user';
import { PasswordChangeForm } from '@/features/password-change';

/** アカウント設定ページ（プロフィール + パスワード変更） */
export default function AccountPage() {
  const [nickname, setNickname] = useState('');
  const [email, setEmail] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    const fetchMe = async () => {
      try {
        const me = await storeAuthApi.me();
        setNickname(me.nickname);
        setEmail(me.email);
      } catch {
        toast.error('アカウント情報の取得に失敗しました');
      } finally {
        setIsLoading(false);
      }
    };
    fetchMe();
  }, []);

  const handleProfileSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    try {
      const me = await storeAuthApi.updateMe({ nickname });
      setNickname(me.nickname);
      toast.success('プロフィールを更新しました');
    } catch {
      toast.error('プロフィールの更新に失敗しました');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return <div className="p-8 text-center text-gray-500">読み込み中...</div>;
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">アカウント設定</h1>
        <p className="text-sm text-gray-500 mt-1">自分のプロフィールとパスワードを管理します。</p>
      </div>

      {/* プロフィール */}
      <form
        onSubmit={handleProfileSubmit}
        className="bg-white p-8 rounded-xl shadow-sm border border-gray-100 space-y-6"
      >
        <h3 className="text-lg font-semibold text-gray-900 border-l-4 border-indigo-500 pl-3">
          プロフィール
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              メールアドレス（ログインID）
            </label>
            <input
              type="email"
              value={email}
              disabled
              className="w-full rounded-md border-gray-300 bg-gray-50 text-gray-500 shadow-sm"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">ニックネーム *</label>
            <input
              type="text"
              value={nickname}
              onChange={e => setNickname(e.target.value)}
              required
              maxLength={150}
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
            {isSubmitting ? '保存中...' : '保存する'}
          </button>
        </div>
      </form>

      <PasswordChangeForm />
    </div>
  );
}
