'use client';
import { useEffect } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import {
  receptionChannelLabels,
  sourceTypeLabels,
  type ApplicantIntake,
} from '@/entities/applicant';
import {
  Button,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Textarea,
} from '@/shared/ui';
const defaults: ApplicantIntake = {
  name: '',
  channel: 'WEB',
  source_type: 'DIRECT',
  source_media: '',
  referrer: '',
  assignee: '',
  phone: '',
  email: '',
  address: '',
  experience: '',
  desired_conditions: '',
};
const textFields = [
  ['name', '氏名', 100],
  ['source_media', '応募元媒体名', 200],
  ['referrer', '紹介者・スカウト名', 100],
  ['assignee', '担当者名（記録用）', 100],
  ['phone', '電話番号', 50],
  ['email', 'メールアドレス', 254],
  ['address', '住所', 500],
  ['experience', '経歴', 3000],
  ['desired_conditions', '希望条件', 3000],
] as const;
export function ApplicantIntakeForm({
  initial,
  onSave,
  onDirtyChange,
}: {
  onDirtyChange?: (dirty: boolean) => void;
  initial?: ApplicantIntake;
  onSave: (data: ApplicantIntake) => Promise<void>;
}) {
  const form = useForm<ApplicantIntake>({
    defaultValues: Object.fromEntries(
      Object.entries(defaults).map(([key, value]) => [
        key,
        initial?.[key as keyof ApplicantIntake] ?? value,
      ])
    ) as unknown as ApplicantIntake,
  });
  const dirty = form.formState.isDirty;
  useEffect(() => {
    onDirtyChange?.(dirty);
  }, [dirty, onDirtyChange]);
  const source = useWatch({ control: form.control, name: 'source_type' });
  return (
    <Form {...form}>
      <form
        noValidate
        onSubmit={form.handleSubmit(onSave)}
        className="rounded-xl border bg-card p-6 space-y-6"
      >
        <fieldset disabled={form.formState.isSubmitting} className="space-y-6">
          <p className="text-sm text-muted-foreground">
            担当者・紹介者は記録用の名前です。システムの権限やアカウントを割り当てるものではありません。
          </p>
          <div className="grid gap-6 md:grid-cols-2">
            {(['channel', 'source_type'] as const).map(name => {
              const labels = name === 'channel' ? receptionChannelLabels : sourceTypeLabels;
              return (
                <FormField
                  key={name}
                  control={form.control}
                  name={name}
                  rules={{ required: '選択してください' }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{name === 'channel' ? '受付チャネル' : '応募元区分'}</FormLabel>
                      <Select
                        required
                        name={field.name}
                        value={field.value}
                        items={labels}
                        onValueChange={value => {
                          if (!value) return;
                          field.onChange(value);
                          if (name === 'source_type') {
                            form.setValue('source_media', '');
                            form.setValue('referrer', '');
                          }
                        }}
                      >
                        <FormControl>
                          <SelectTrigger ref={field.ref} onBlur={field.onBlur}>
                            <SelectValue />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {Object.entries(labels).map(([value, label]) => (
                            <SelectItem key={value} value={value}>
                              {label}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              );
            })}
            {textFields
              .filter(([name]) => name !== 'source_media' || source === 'MEDIA')
              .filter(
                ([name]) => name !== 'referrer' || source === 'REFERRAL' || source === 'SCOUT'
              )
              .map(([name, label, max]) => (
                <FormField
                  key={name}
                  control={form.control}
                  name={name}
                  rules={{
                    maxLength: { value: max, message: `${max}文字以内で入力してください` },
                    validate: value => {
                      const required =
                        name === 'name' || name === 'source_media' || name === 'referrer';
                      if (required && !value?.trim()) return `${label}を入力してください`;
                      if (
                        name === 'email' &&
                        value?.trim() &&
                        !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim())
                      )
                        return 'メールアドレスの形式を確認してください';
                      return true;
                    },
                  }}
                  render={({ field }) => (
                    <FormItem className={max > 500 ? 'md:col-span-2' : ''}>
                      <FormLabel>{label}</FormLabel>
                      <FormControl>
                        {max > 500 ? (
                          <Textarea {...field} value={field.value ?? ''} maxLength={max} rows={3} />
                        ) : (
                          <Input
                            {...field}
                            value={field.value ?? ''}
                            maxLength={max}
                            type={name === 'email' ? 'email' : name === 'phone' ? 'tel' : 'text'}
                            required={
                              name === 'name' || name === 'source_media' || name === 'referrer'
                            }
                          />
                        )}
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              ))}
          </div>
          <Button type="submit" disabled={form.formState.isSubmitting}>
            受付情報を保存
          </Button>
        </fieldset>
      </form>
    </Form>
  );
}
