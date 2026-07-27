'use client';

import { useEffect, useState } from 'react';
import { toast } from 'react-hot-toast';
import { platformAuthApi } from '@/entities/user';
import { PasswordChangeForm } from '@/features/password-change';
import {
  Button,
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
  Input,
  Label,
} from '@/shared/ui';

/** アカウント設定ページ（プロフィール + パスワード変更） */
export default function AccountPage() {
  const [nickname, setNickname] = useState('');
  const [email, setEmail] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    const fetchMe = async () => {
      try {
        const me = await platformAuthApi.me();
        setNickname(me.display_name);
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
      const me = await platformAuthApi.updateMe({ display_name: nickname });
      setNickname(me.display_name);
      toast.success('プロフィールを更新しました');
    } catch {
      toast.error('プロフィールの更新に失敗しました');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return <div className="p-8 text-center text-muted-foreground">読み込み中...</div>;
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">アカウント設定</h1>
        <p className="text-sm text-muted-foreground mt-1">
          自分のプロフィールとパスワードを管理します。
        </p>
      </div>

      {/* プロフィール */}
      <form onSubmit={handleProfileSubmit}>
        <Card>
          <CardHeader>
            <CardTitle role="heading" aria-level={2}>
              プロフィール
            </CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="grid gap-2">
              <Label htmlFor="account-email">メールアドレス（ログインID）</Label>
              <Input id="account-email" type="email" value={email} disabled />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="account-nickname">ニックネーム *</Label>
              <Input
                id="account-nickname"
                type="text"
                value={nickname}
                onChange={e => setNickname(e.target.value)}
                required
                maxLength={150}
              />
            </div>
          </CardContent>
          <CardFooter className="justify-end">
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? '保存中...' : '保存する'}
            </Button>
          </CardFooter>
        </Card>
      </form>

      <PasswordChangeForm />
    </div>
  );
}
