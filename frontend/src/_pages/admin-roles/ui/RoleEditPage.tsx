'use client';

import { useParams, useRouter } from 'next/navigation';
import { roleApi } from '@/entities/user';
import { getApiErrorMessage, useResource, useVersionedWrite } from '@/shared/lib';
import { notify } from '@/shared/notify';
import { Card, CardContent, RegionError, VersionConflictNotice } from '@/shared/ui';
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
  const writer = useVersionedWrite(reload);

  const submit = async (values: RoleFormValues) => {
    if (id === null || role === null) return;
    await writer.write(
      () =>
        roleApi.update(id, {
          name: values.name,
          permissions: values.permissions,
          // 楽観ロック用バージョン（詳細応答の version をそのまま往復する）
          version: role.version,
        }),
      {
        success: 'ロールを更新しました',
        failure: (error, kind) => {
          if (kind === 'conflict') {
            notify.warning(
              '他の管理者が更新したため保存できませんでした。入力中の内容は画面に残しています'
            );
          } else {
            notify.error(getApiErrorMessage(error, 'ロールの保存に失敗しました'));
          }
        },
        onSuccess: () => router.push('/admin/roles'),
      }
    );
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
          <RegionError
            message="ロール情報の取得に失敗しました"
            onRetry={() => void writer.reload()}
          />
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
        <CardContent className="space-y-4">
          <RoleForm
            initial={{
              name: role.name ?? '',
              permissions: role.permissions ?? [],
              system: role.system,
            }}
            onSubmit={submit}
            busy={writer.busy}
            locked={writer.conflict}
          />
          {writer.conflict && (
            <VersionConflictNotice
              className="max-w-2xl"
              busy={writer.busy}
              onLoad={() => void writer.reload()}
            />
          )}
        </CardContent>
      </Card>
    </div>
  );
}
