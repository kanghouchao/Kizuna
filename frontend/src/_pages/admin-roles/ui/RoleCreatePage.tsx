'use client';

import { useRouter } from 'next/navigation';
import { roleApi } from '@/entities/user';
import { useWrite } from '@/shared/lib';
import { Card, CardContent } from '@/shared/ui';
import { RoleForm, RoleFormValues } from './RoleForm';

export default function RoleCreatePage() {
  const router = useRouter();

  const { busy, write } = useWrite();

  const submit = (values: RoleFormValues) =>
    write(() => roleApi.create({ name: values.name, permissions: values.permissions }), {
      success: 'ロールを追加しました',
      failure: 'ロールの保存に失敗しました',
      onSuccess: () => router.push('/admin/roles'),
    });

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-foreground">ロールを追加</h1>
      <Card>
        <CardContent>
          <RoleForm onSubmit={submit} busy={busy} />
        </CardContent>
      </Card>
    </div>
  );
}
