'use client';

import { useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { Store, UpdateStoreRequest, platformStoreApi } from '@/entities/store';
import { getApiErrorMessage, useResource } from '@/shared/lib';
import { Button, Card, CardContent, Input, Label, RegionError } from '@/shared/ui';
import { notify } from '@/shared/notify';

export default function EditStorePage() {
  const id = useParams<{ id: string }>()?.id;
  const router = useRouter();

  const [saving, setSaving] = useState(false);
  const {
    data: store,
    isLoading: loading,
    failure,
    reload: reloadStore,
  } = useResource(id ? () => platformStoreApi.getById(id) : null, [id]);
  // 欄の起点は取得した店舗で、編集を始めたらその下書きが優先する。効果で写すと、店舗が
  // 届いたレンダーで欄が空のまま一度描かれる。下書きは書き始めた時点の店舗に結び付ける —
  // 別の店舗が届いたら、その店舗の値で描き直す
  const [draft, setDraft] = useState<{ store: Store | null; values: UpdateStoreRequest } | null>(
    null
  );
  const formData: UpdateStoreRequest =
    draft !== null && draft.store === store
      ? draft.values
      : { name: store?.name ?? '', email: store?.email ?? '' };
  const edit = (values: UpdateStoreRequest) => setDraft({ store, values });
  const [errors, setErrors] = useState<Partial<UpdateStoreRequest>>({});
  // id を持たない URL で開かれた＝指せる店舗が無い。取りに行けないので再試行も置けない
  const loadFailure = id ? failure : 'notFound';

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
      notify.success('店舗情報を更新しました');
      router.push('/platform/stores');
    } catch (err: any) {
      if (err.response?.data?.errors) setErrors(err.response.data.errors);
      notify.error(getApiErrorMessage(err, '更新に失敗しました。入力内容をご確認ください'));
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <div className="p-8 text-center text-muted-foreground">読み込み中...</div>;
  }

  // 取得できなかったこの頁自身が失敗を名乗るので、一覧へは送り返さない
  if (loadFailure !== null) {
    return (
      <div className="space-y-6">
        <h1 className="text-2xl font-bold text-foreground">店舗編集</h1>
        {loadFailure === 'notFound' ? (
          // 404 は何度押しても取れない。再試行ではなく一覧への導線だけを出す
          <RegionError
            message="この店舗は見つかりませんでした"
            fallback={{ href: '/platform/stores', label: '店舗一覧へ' }}
          />
        ) : (
          <RegionError message="店舗情報の取得に失敗しました" onRetry={() => void reloadStore()} />
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
                onChange={e => edit({ ...formData, name: e.target.value })}
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
                onChange={e => edit({ ...formData, email: e.target.value })}
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
