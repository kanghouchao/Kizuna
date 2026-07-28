'use client';

import { useEffect, useState, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useAuth } from '@/entities/user';
import { Store, UpdateStoreRequest, platformStoreApi } from '@/entities/store';
import { Button, Card, CardContent, Input, Label } from '@/shared/ui';
import toast from 'react-hot-toast';

export default function EditStorePage() {
  const id = useParams<{ id: string }>()?.id;
  const router = useRouter();
  const { logout } = useAuth();

  const [saving, setSaving] = useState(false);
  const [store, setStore] = useState<Store | null>(null);
  const [loading, setLoading] = useState(true);
  const [formData, setFormData] = useState<UpdateStoreRequest>({
    name: '',
    email: '',
  });
  const [errors, setErrors] = useState<Partial<UpdateStoreRequest>>({});

  const loadStore = useCallback(
    async (storeId: string) => {
      if (!id) {
        toast.error('店舗情報の取得できませんでした');
        return;
      }
      try {
        setLoading(true);
        const res = await platformStoreApi.getById(storeId);
        const t = res as unknown as Store;
        setStore(t);
        setFormData({
          name: t.name,
          email: t.email,
        });
      } catch (e) {
        console.error('Error loading store:', e);
        toast.error('店舗情報の取得に失敗しました');
      } finally {
        setLoading(false);
      }
    },
    [id]
  );

  useEffect(() => {
    if (id) {
      loadStore(id);
    }
  }, [id, loadStore]);

  const validate = (): boolean => {
    const next: Partial<UpdateStoreRequest> = {};
    if (!formData.name.trim()) next.name = '店舗名は必須です';
    if (!formData.email.trim()) next.email = 'メールアドレスは必須です';
    else if (!/^([^\s@])+@([^\s@])+\.[^\s@]+$/.test(formData.email))
      next.email = 'メールアドレスの形式が正しくありません';
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!store) return;
    if (!validate()) return;
    setSaving(true);
    try {
      await platformStoreApi.update(store.id, formData);
      toast.success('店舗情報を更新しました');
      router.push('/platform/stores');
    } catch (err: any) {
      if (err.response?.data?.errors) setErrors(err.response.data.errors);
      toast.error('更新に失敗しました。入力内容をご確認ください');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="min-h-screen bg-background">
      {/* ナビゲーションバー */}
      <nav className="bg-card shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex items-center space-x-4">
              <Button
                variant="ghost"
                size="sm"
                className="text-primary-strong"
                onClick={() => router.push('/platform/stores')}
              >
                ← 店舗一覧に戻る
              </Button>
              <h1 className="text-xl font-semibold text-foreground">店舗編集</h1>
            </div>
            <div className="flex items-center space-x-4">
              <span className="text-sm text-muted-foreground">ようこそ、someone さん</span>
              <Button variant="ghost" size="sm" onClick={logout}>
                ログアウト
              </Button>
            </div>
          </div>
        </div>
      </nav>

      {/* メイン */}
      <div className="max-w-3xl mx-auto py-6 sm:px-6 lg:px-8">
        <div className="px-4 py-6 sm:px-0">
          <Card>
            <CardContent>
              <div className="mb-6">
                <h3 className="text-lg leading-6 font-medium text-foreground">店舗情報</h3>
                <p className="mt-1 text-sm text-muted-foreground">
                  店舗の基本情報を編集します。ドメインは現在変更できません。
                </p>
              </div>

              <form onSubmit={handleSave} className="space-y-6">
                {/* 店舗名 */}
                <div className="grid gap-2">
                  <Label htmlFor="name">
                    店舗名 <span className="text-destructive-strong">*</span>
                  </Label>
                  <Input
                    id="name"
                    type="text"
                    value={formData.name}
                    onChange={e => setFormData(p => ({ ...p, name: e.target.value }))}
                    aria-invalid={!!errors.name}
                  />
                  {errors.name && <p className="text-sm text-destructive-strong">{errors.name}</p>}
                </div>

                {/* 連絡用メール */}
                <div className="grid gap-2">
                  <Label htmlFor="email">
                    連絡用メール <span className="text-destructive-strong">*</span>
                  </Label>
                  <Input
                    id="email"
                    type="email"
                    value={formData.email}
                    onChange={e => setFormData(p => ({ ...p, email: e.target.value }))}
                    aria-invalid={!!errors.email}
                  />
                  {errors.email && (
                    <p className="text-sm text-destructive-strong">{errors.email}</p>
                  )}
                </div>

                {/* ドメイン（読み取り専用） */}
                {store && (
                  <div className="rounded-lg border p-4">
                    <h4 className="text-sm font-medium text-foreground mb-2">ドメイン</h4>
                    {/* DB の domain 列は unique だが NOT NULL ではないため空値があり得る */}
                    {store.domain ? (
                      <p className="text-sm text-foreground break-all">{store.domain}</p>
                    ) : (
                      <p className="text-sm text-muted-foreground">ドメインは設定されていません</p>
                    )}
                  </div>
                )}

                {/* 操作 */}
                <div className="flex justify-end space-x-3">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => router.push('/platform/stores')}
                  >
                    キャンセル
                  </Button>
                  <Button type="submit" disabled={saving}>
                    {saving ? '保存中...' : '保存'}
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
