'use client';

import { useRouter } from 'next/navigation';
import { staffApi } from '@/entities/user';
import { getApiErrorMessage } from '@/shared/lib';
import { notify } from '@/shared/notify';
import { PageHeader } from '@/widgets/page-header';
import { StaffForm, type StaffFormValues } from './StaffForm';
import { toGrantRequest } from './toGrantRequest';

export default function StaffCreatePage() {
  const router = useRouter();

  const submit = async (values: StaffFormValues) => {
    try {
      await staffApi.create({
        email: values.email,
        password: values.password,
        display_name: values.display_name,
        ...toGrantRequest(values),
      });
    } catch (error) {
      notify.error(getApiErrorMessage(error, '従業員の追加に失敗しました'));
      return;
    }
    notify.success('従業員を追加しました');
    router.push('/admin/staff');
  };

  return (
    <div className="space-y-6">
      <PageHeader title="従業員を追加" />
      <StaffForm onSubmit={submit} />
    </div>
  );
}
