'use client';

import { useRouter } from 'next/navigation';
import { roleApi } from '@/entities/user';
import { getApiErrorMessage } from '@/shared/lib';
import { notify } from '@/shared/notify';
import { Card, CardContent } from '@/shared/ui';
import { RoleForm, RoleFormValues } from './RoleForm';

export default function RoleCreatePage() {
  const router = useRouter();

  const submit = async (values: RoleFormValues) => {
    try {
      await roleApi.create({ name: values.name, permissions: values.permissions });
    } catch (error) {
      notify.error(getApiErrorMessage(error, 'ロールの保存に失敗しました'));
      return;
    }
    notify.success('ロールを追加しました');
    router.push('/admin/roles');
  };

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-foreground">ロールを追加</h1>
      <Card>
        <CardContent>
          <RoleForm onSubmit={submit} />
        </CardContent>
      </Card>
    </div>
  );
}
