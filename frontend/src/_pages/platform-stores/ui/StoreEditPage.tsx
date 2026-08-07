'use client';

import { useEffect, useRef, useState, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { Store, UpdateStoreRequest, platformStoreApi } from '@/entities/store';
import { isNotFound } from '@/shared/lib';
import { Button, Card, CardContent, Input, Label, RegionError } from '@/shared/ui';
import { notify } from '@/shared/notify';

export default function EditStorePage() {
  const id = useParams<{ id: string }>()?.id;
  const router = useRouter();

  const [saving, setSaving] = useState(false);
  const [store, setStore] = useState<Store | null>(null);
  const [loading, setLoading] = useState(true);
  const [formData, setFormData] = useState<UpdateStoreRequest>({
    name: '',
    email: '',
  });
  const [errors, setErrors] = useState<Partial<UpdateStoreRequest>>({});
  const [loadFailure, setLoadFailure] = useState<'notFound' | 'error' | null>(null);
  // 並行リクエストが順不同で完了しても、最新のリクエストだけが state を更新する。失敗が
  // 店舗をクリアするので、在途の古い失敗が新しい成功を消し得る
  const requestIdRef = useRef(0);

  // 再試行から呼び直せるよう effect の外に置く。取得できなかったこの頁自身が失敗を名乗るので、
  // 一覧へは送り返さない
  const loadStore = useCallback(async (storeId: string) => {
    const requestId = ++requestIdRef.current;
    try {
      setLoading(true);
      const res = await platformStoreApi.getById(storeId);
      const t = res as unknown as Store;
      if (requestId === requestIdRef.current) {
        setStore(t);
        setFormData({
          name: t.name ?? '',
          email: t.email ?? '',
        });
        setLoadFailure(null);
      }
    } catch (e) {
      console.error('Error loading store:', e);
      if (requestId === requestIdRef.current) {
        setStore(null);
        // 404 は何度押しても取れない。再試行ではなく一覧への導線だけを出す
        setLoadFailure(isNotFound(e) ? 'notFound' : 'error');
      }
    } finally {
      if (requestId === requestIdRef.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (id) {
      loadStore(id);
      return;
    }
    // id を持たない URL で開かれた＝指せる店舗が無い。取りに行けないので再試行も置けない
    setLoading(false);
    setLoadFailure('notFound');
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
      await platformStoreApi.update(store.id ?? '', formData);
      notify.success('店舗情報を更新しました');
      router.push('/platform/stores');
    } catch (err: any) {
      if (err.response?.data?.errors) setErrors(err.response.data.errors);
      notify.error('更新に失敗しました。入力内容をご確認ください');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <div className="p-8 text-center text-muted-foreground">読み込み中...</div>;
  }

  if (loadFailure !== null) {
    return (
      <div className="space-y-6">
        <h1 className="text-2xl font-bold text-foreground">店舗編集</h1>
        {loadFailure === 'notFound' ? (
          <RegionError
            message="この店舗は見つかりませんでした"
            fallback={{ href: '/platform/stores', label: '店舗一覧へ' }}
          />
        ) : (
          <RegionError
            message="店舗情報の取得に失敗しました"
            onRetry={() => {
              if (id) void loadStore(id);
            }}
          />
        )}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">店舗編集</h1>
        <p className="text-sm text-muted-foreground mt-1">
          店舗の基本情報を編集します。ドメインは現在変更できません。
        </p>
      </div>

      <Card>
        <CardContent>
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
              {errors.email && <p className="text-sm text-destructive-strong">{errors.email}</p>}
            </div>

            {/* ドメイン（読み取り専用） */}
            {store && (
              <div className="rounded-lg border p-4">
                <h2 className="text-sm font-medium text-foreground mb-2">ドメイン</h2>
                <p className="text-sm text-foreground break-all">{store.domain}</p>
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
  );
}
