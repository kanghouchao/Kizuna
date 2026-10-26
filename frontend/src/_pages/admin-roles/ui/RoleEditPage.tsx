'use client';

import { useParams, useRouter } from 'next/navigation';
import { roleApi } from '@/entities/user';
import { getApiErrorMessage, isConflict, useResource } from '@/shared/lib';
import { notify } from '@/shared/notify';
import { Card, CardContent, RegionError } from '@/shared/ui';
import { RoleForm, RoleFormValues } from './RoleForm';

export default function RoleEditPage() {
  const rawId = useParams<{ id: string }>()?.id;
  // 数字でない id は指せるロールが無いので、取りに行かず notFound として扱う
  const id = rawId !== undefined && /^\d+$/.test(rawId) ? Number(rawId) : null;
  const router = useRouter();
  const {
    data: role,
    isLoading,
    failure,
    reload,
  } = useResource(id !== null ? () => roleApi.get(id) : null, [id]);
  const loadFailure = id !== null ? failure : 'notFound';

  const submit = async (values: RoleFormValues) => {
    if (id === null || role === null) return;
    try {
      await roleApi.update(id, {
        name: values.name,
        permissions: values.permissions,
        // 楽観ロック用バージョン（詳細応答の version をそのまま往復する）
        version: role.version,
      });
    } catch (error) {
      if (isConflict(error)) {
        // 古い version を抱えたままだと再試行が同じ 409 を繰り返すため、取り直して欄を最新で描き直す
        notify.warning('他の管理者が更新しました。最新の内容を確認してください');
        void reload();
      } else {
        notify.error(getApiErrorMessage(error, 'ロールの保存に失敗しました'));
      }
      return;
    }
    notify.success('ロールを更新しました');
    router.push('/admin/roles');
  };

  if (loadFailure !== null) {
    return (
      <div className="space-y-6">
        <h1 className="text-2xl font-bold text-foreground">ロールを編集</h1>
        {loadFailure === 'notFound' ? (
          // 404 は何度押しても取れない。再試行ではなく一覧への導線だけを出す
          <RegionError
            message="このロールは見つかりませんでした"
            fallback={{ href: '/admin/roles', label: 'ロール一覧へ' }}
          />
        ) : (
          <RegionError message="ロール情報の取得に失敗しました" onRetry={() => void reload()} />
        )}
      </div>
    );
  }

  if (isLoading || role === null) {
    return <div className="p-8 text-center text-muted-foreground">読み込み中...</div>;
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-foreground">{role.name} を編集</h1>
      <Card>
        <CardContent>
          {/* version が変わったら欄を取得した最新の値で描き直す */}
          <RoleForm
            key={role.version}
            initial={{
              name: role.name ?? '',
              permissions: role.permissions ?? [],
              system: role.system,
            }}
            onSubmit={submit}
          />
        </CardContent>
      </Card>
    </div>
  );
}
