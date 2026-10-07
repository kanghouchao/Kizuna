'use client';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import {
  allowedApplicantTransitions,
  applicantStatusLabels,
  type ApplicantDetail,
  type ApplicantStatus,
} from '@/entities/applicant';
import {
  Button,
  ConfirmDialog,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Textarea,
} from '@/shared/ui';
export function ApplicantTransitionForm({
  applicant,
  onSave,
  busy,
  onDirtyChange,
}: {
  applicant: ApplicantDetail;
  onSave: (status: ApplicantStatus, reason: string) => Promise<void>;
  busy: boolean;
  onDirtyChange: (dirty: boolean) => void;
}) {
  const allowed = allowedApplicantTransitions(applicant.status);
  const form = useForm<{ status: ApplicantStatus | ''; reason: string }>({
    defaultValues: { status: '', reason: '' },
  });
  const [pending, setPending] = useState<{ status: ApplicantStatus; reason: string } | null>(null);
  const dirty = form.formState.isDirty;
  useEffect(() => onDirtyChange(dirty), [dirty, onDirtyChange]);
  async function submit(value: { status: ApplicantStatus; reason: string }) {
    if (busy) return;
    setPending(null);
    await onSave(value.status, value.reason);
  }
  if (!allowed.length) return null;
  return (
    <>
      <Form {...form}>
        <form
          noValidate
          onSubmit={form.handleSubmit(async value => {
            if (!value.status) return;
            const data = { status: value.status, reason: value.reason };
            if (value.status === 'WITHDRAWN') setPending(data);
            else await submit(data);
          })}
          className="space-y-6"
        >
          <fieldset disabled={busy} className="space-y-6">
            <FormField
              control={form.control}
              name="status"
              rules={{ required: '変更先を選択してください' }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>変更先</FormLabel>
                  <Select
                    value={field.value || null}
                    onValueChange={field.onChange}
                    items={applicantStatusLabels}
                    required
                  >
                    <FormControl>
                      <SelectTrigger ref={field.ref} onBlur={field.onBlur}>
                        <SelectValue placeholder="選択してください" />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {allowed.map(status => (
                        <SelectItem key={status} value={status}>
                          {applicantStatusLabels[status]}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="reason"
              rules={{
                validate: v => !!v.trim() || '変更理由を入力してください',
                maxLength: { value: 1000, message: '1000文字以内で入力してください' },
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>変更理由</FormLabel>
                  <FormControl>
                    <Textarea {...field} required maxLength={1000} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <p className="text-sm text-muted-foreground">
              辞退は応募者による応募の撤回です。選考終了後は編集・再開できません。
            </p>
            <Button type="submit" disabled={busy || form.formState.isSubmitting}>
              選考状態を変更
            </Button>
          </fieldset>
        </form>
      </Form>
      <ConfirmDialog
        open={pending !== null}
        title="応募者の辞退を記録しますか？"
        description="選考を終了します。この操作の後は編集・再開できません。"
        confirmLabel="辞退を記録"
        onClose={() => setPending(null)}
        onConfirm={() => {
          if (pending) void submit(pending);
        }}
      />
    </>
  );
}
