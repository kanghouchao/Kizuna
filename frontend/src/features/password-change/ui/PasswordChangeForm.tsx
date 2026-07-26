'use client';

import { useState } from 'react';
import { toast } from 'react-hot-toast';
import { platformAuthApi, useAuth } from '@/entities/user';
import { getApiErrorMessage } from '@/shared/lib';
import {
  Button,
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
  Input,
  Label,
} from '@/shared/ui';

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
      await platformAuthApi.changePassword({
        current_password: currentPassword,
        new_password: newPassword,
      });
      toast.success('パスワードを変更しました。再度ログインしてください');
      logout();
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'パスワードの変更に失敗しました'));
      setIsSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <Card>
        <CardHeader>
          <CardTitle role="heading" aria-level={3}>
            パスワード変更
          </CardTitle>
          <CardDescription>
            変更後は自動的にログアウトされ、新しいパスワードでの再ログインが必要です。
          </CardDescription>
        </CardHeader>
        <CardContent className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="grid gap-2">
            <Label htmlFor="current-password">現在のパスワード *</Label>
            <Input
              id="current-password"
              type="password"
              value={currentPassword}
              onChange={e => setCurrentPassword(e.target.value)}
              required
              autoComplete="current-password"
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="new-password">新しいパスワード（8文字以上）*</Label>
            <Input
              id="new-password"
              type="password"
              value={newPassword}
              onChange={e => setNewPassword(e.target.value)}
              required
              minLength={8}
              autoComplete="new-password"
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="confirm-password">新しいパスワード（確認）*</Label>
            <Input
              id="confirm-password"
              type="password"
              value={confirmPassword}
              onChange={e => setConfirmPassword(e.target.value)}
              required
              minLength={8}
              autoComplete="new-password"
            />
          </div>
        </CardContent>
        <CardFooter className="justify-end">
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? '変更中...' : 'パスワードを変更する'}
          </Button>
        </CardFooter>
      </Card>
    </form>
  );
}
