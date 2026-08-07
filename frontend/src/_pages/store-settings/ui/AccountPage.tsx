'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { notify } from '@/shared/notify';
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
  RegionError,
} from '@/shared/ui';

/** アカウント設定ページ（プロフィール + パスワード変更） */
export default function AccountPage() {
  const [nickname, setNickname] = useState('');
  const [email, setEmail] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  // 並行リクエストが順不同で完了しても、最新のリクエストだけが state を更新する。失敗が
  // 欄をクリアするので、在途の古い失敗が新しい成功を消し得る
  const requestIdRef = useRef(0);

  // 再試行から呼び直せるよう effect の外に置く
  const fetchMe = useCallback(async () => {
    const requestId = ++requestIdRef.current;
    setIsLoading(true);
    try {
      const me = await platformAuthApi.me();
      if (requestId === requestIdRef.current) {
        setNickname(me.display_name ?? '');
        setEmail(me.email ?? '');
        setFailed(false);
      }
    } catch {
      // 空欄のまま出すと「ニックネーム未設定」に見え、保存するとその空欄が本当になる
      if (requestId === requestIdRef.current) {
        setNickname('');
        setEmail('');
        setFailed(true);
      }
    } finally {
      if (requestId === requestIdRef.current) setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchMe();
  }, [fetchMe]);

  const handleProfileSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    try {
      const me = await platformAuthApi.updateMe({ display_name: nickname });
      setNickname(me.display_name ?? '');
      notify.success('プロフィールを更新しました');
    } catch {
      notify.error('プロフィールの更新に失敗しました');
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
          {/* 取得できなかった値で欄を埋めない。空欄のまま保存できると表示名が空文字に化ける */}
          {failed ? (
            <CardContent>
              <RegionError
                message="アカウント情報の取得に失敗しました"
                onRetry={() => void fetchMe()}
              />
            </CardContent>
          ) : (
            <>
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
            </>
          )}
        </Card>
      </form>

      <PasswordChangeForm />
    </div>
  );
}
