'use client';

import { useRouter } from 'next/navigation';
import { serviceIdentityApi } from '@/entities/user';
import { GrantForm, toGrantRequest, type GrantFormValues } from '@/features/staff-management';
import { getApiErrorMessage } from '@/shared/lib';
import { notify } from '@/shared/notify';
import { FormControl, FormField, FormItem, FormLabel, FormMessage, Input } from '@/shared/ui';
import { PageHeader } from '@/widgets/page-header';
import { LIST_HREF, SERVICE_IDENTITY_GRANT_FORM } from './serviceIdentityForm';

export default function ServiceIdentityCreatePage() {
  const router = useRouter();

  const submit = async (values: GrantFormValues) => {
    try {
      await serviceIdentityApi.create({
        display_name: values.display_name,
        ...toGrantRequest(values),
      });
    } catch (error) {
      notify.error(getApiErrorMessage(error, 'サービスIDの追加に失敗しました'));
      return;
    }
    notify.success('サービスIDを追加しました');
    router.push(LIST_HREF);
  };

  return (
    <div className="space-y-6">
      <PageHeader title="サービスIDを追加" />
      <GrantForm
        {...SERVICE_IDENTITY_GRANT_FORM}
        defaultValues={{
          display_name: '',
          role_ids: [],
          store_ids: [],
        }}
        profile={{
          title: 'サービスID',
          fields: control => (
            <FormField
              control={control}
              name="display_name"
              rules={{ required: '用途名を入力してください' }}
              render={({ field }) => (
                <FormItem className="gap-2 md:col-span-2">
                  <FormLabel>用途名</FormLabel>
                  <FormControl>
                    <Input
                      type="text"
                      required
                      maxLength={150}
                      placeholder="例: 夜間ポイント失効バッチ"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          ),
        }}
        unnamedSubject="新しいサービスID"
        loadRoles={serviceIdentityApi.grantableRoles}
        onSubmit={submit}
      />
    </div>
  );
}
