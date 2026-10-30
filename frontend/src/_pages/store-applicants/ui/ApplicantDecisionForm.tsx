'use client';
import { useEffect, useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import {
  allowedApplicantDecisions,
  applicantPathToInterviewed,
  applicantStatusLabels,
  type ApplicantDecision,
  type ApplicantDetail,
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
// 面接済への遷移は面接記録を前提とするため、未登録ならその登録を最初の手順に置く。
// 手順の操作には管理権限が要るので、持たない利用者には依頼を促す
function hireStepsHint(applicant: ApplicantDetail, canManage: boolean): string {
  const path = applicantPathToInterviewed(applicant.status);
  if (!path?.length) return '';
  const steps = [
    ...(applicant.interview ? [] : ['面接記録を登録']),
    `選考状態を${path.map(status => applicantStatusLabels[status]).join('→')}に変更`,
  ];
  const request = canManage ? '' : 'これらの操作は採用の管理権限を持つ担当者に依頼してください。';
  return `採用は面接済の応募者だけに確定できます。${steps.join('し、')}すると採用を選べます。${request}`;
}
export function ApplicantDecisionForm({
  applicant,
  canManage,
  onSave,
  busy,
  onDirtyChange,
}: {
  applicant: ApplicantDetail;
  canManage: boolean;
  onSave: (status: ApplicantDecision, reason: string) => Promise<void>;
  busy: boolean;
  onDirtyChange: (dirty: boolean) => void;
}) {
  const allowed = allowedApplicantDecisions(applicant.status);
  const hireHint = hireStepsHint(applicant, canManage);
  const form = useForm<{ status: ApplicantDecision | ''; reason: string }>({
    defaultValues: { status: '', reason: '' },
  });
  const [pending, setPending] = useState<{ status: ApplicantDecision; reason: string } | null>(
    null
  );
  const selected = useWatch({ control: form.control, name: 'status' });
  const dirty = form.formState.isDirty;
  useEffect(() => onDirtyChange(dirty), [dirty, onDirtyChange]);
  if (!allowed.length) return null;
  return (
    <>
      <Form {...form}>
        <form
          noValidate
          onSubmit={form.handleSubmit(value => {
            if (value.status) setPending({ status: value.status, reason: value.reason });
          })}
          className="space-y-6"
        >
          <fieldset disabled={busy} className="space-y-6">
            <FormField
              control={form.control}
              name="status"
              rules={{ required: '採否を選択してください' }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>採否</FormLabel>
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
                validate: v => !!v.trim() || '確定理由を入力してください',
                maxLength: { value: 1000, message: '1000文字以内で入力してください' },
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>確定理由</FormLabel>
                  <FormControl>
                    <Textarea {...field} required maxLength={1000} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <p className="text-sm text-muted-foreground">
              {hireHint}確定後は編集・再開できません。
            </p>
            <Button type="submit" disabled={busy || form.formState.isSubmitting}>
              採否を確定
            </Button>
          </fieldset>
        </form>
      </Form>
      <ConfirmDialog
        open={pending !== null}
        title={`応募者を${selected ? applicantStatusLabels[selected] : ''}に確定しますか？`}
        description="選考を終了します。この操作の後は編集・再開できません。"
        confirmLabel="確定する"
        onClose={() => setPending(null)}
        onConfirm={() => {
          if (!pending || busy) return;
          const value = pending;
          setPending(null);
          void onSave(value.status, value.reason);
        }}
      />
    </>
  );
}
