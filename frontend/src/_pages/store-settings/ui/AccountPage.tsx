'use client';

import { useState } from 'react';
import { notify } from '@/shared/notify';
import { platformAuthApi, useMe } from '@/entities/user';
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
  // 自分の情報は /platform/me の共有 seam から読む。保存の応答は同じ seam の setMe で
  // 差し替え、持久 layout 側のヘッダー表示も再取得なしで追随させる。
  const { me, setMe, isLoading, failure, reload: fetchMe } = useMe();
  const [isSubmitting, setIsSubmitting] = useState(false);
  // 欄の起点は取得した自分の情報で、編集を始めたらその下書きが優先する。効果で写すと、
  // 届いたレンダーで欄が空のまま一度描かれる。下書きは書き始めた時点の取得結果に結び付ける
  // — 取り直しが届いたら、その値で描き直す（失敗して消えたときは空欄へ戻る）
  const [draft, setDraft] = useState<{ me: typeof me; nickname: string } | null>(null);
  const nickname = draft !== null && draft.me === me ? draft.nickname : (me?.display_name ?? '');
  const email = me?.email ?? '';

  const handleProfileSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    try {
      const updated = await platformAuthApi.updateMe({ display_name: nickname });
      // 保存できた値をそのまま欄の起点にする。取り直すと、直した欄が読み込み表示で一瞬消える
      setMe(updated);
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
          {failure !== null ? (
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
                    onChange={e => setDraft({ me, nickname: e.target.value })}
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
