'use client';

import { useRouter } from 'next/navigation';
import { staffApi } from '@/entities/user';
import { GrantForm, toGrantRequest, type GrantFormValues } from '@/features/staff-management';
import { EMAIL_PATTERN, EMAIL_PATTERN_MESSAGE, getApiErrorMessage } from '@/shared/lib';
import { notify } from '@/shared/notify';
import { FormControl, FormField, FormItem, FormLabel, FormMessage, Input } from '@/shared/ui';
import { PageHeader } from '@/widgets/page-header';
import { LIST_HREF, STAFF_GRANT_FORM } from './staffForm';

interface StaffCreateValues extends GrantFormValues {
  email: string;
  password: string;
}

export default function StaffCreatePage() {
  const router = useRouter();

  const submit = async (values: StaffCreateValues) => {
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
    router.push(LIST_HREF);
  };

  return (
    <div className="space-y-6">
      <PageHeader title="従業員を追加" />
      <GrantForm<StaffCreateValues>
        {...STAFF_GRANT_FORM}
        defaultValues={{
          email: '',
          password: '',
          display_name: '',
          role_ids: [],
          store_ids: [],
        }}
        profile={{
          title: '従業員',
          fields: control => (
            <>
              <FormField
                control={control}
                name="display_name"
                rules={{ required: '氏名を入力してください' }}
                render={({ field }) => (
                  <FormItem className="gap-2">
                    <FormLabel>氏名</FormLabel>
                    <FormControl>
                      <Input type="text" required maxLength={150} {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={control}
                name="email"
                rules={{
                  required: 'メールアドレスを入力してください',
                  pattern: { value: EMAIL_PATTERN, message: EMAIL_PATTERN_MESSAGE },
                }}
                render={({ field }) => (
                  <FormItem className="gap-2">
                    <FormLabel>メールアドレス</FormLabel>
                    <FormControl>
                      <Input required type="email" maxLength={127} {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={control}
                name="password"
                rules={{ required: '初期パスワードを入力してください' }}
                render={({ field }) => (
                  <FormItem className="gap-2">
                    <FormLabel>初期パスワード</FormLabel>
                    <FormControl>
                      <Input required type="password" autoComplete="new-password" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </>
          ),
        }}
        unnamedSubject="新しい従業員"
        onSubmit={submit}
      />
    </div>
  );
}
