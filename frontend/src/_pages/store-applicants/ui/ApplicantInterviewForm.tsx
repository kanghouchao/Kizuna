'use client';
import { useEffect } from 'react';
import { useFieldArray, useForm } from 'react-hook-form';
import type { ApplicantInterview } from '@/entities/applicant';
import {
  Button,
  Checkbox,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  Textarea,
} from '@/shared/ui';
interface InterviewForm {
  interview_at: string;
  interviewer: string;
  notes: string;
  checklist: { label: string; checked: boolean }[];
}
function localDate(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}
export function ApplicantInterviewForm({
  initial,
  onSave,
  onDirtyChange,
}: {
  onDirtyChange?: (dirty: boolean) => void;
  initial?: ApplicantInterview | null;
  onSave: (value: ApplicantInterview) => Promise<void>;
}) {
  const form = useForm<InterviewForm>({
    defaultValues: {
      interview_at: localDate(initial?.interview_at),
      interviewer: initial?.interviewer ?? '',
      notes: initial?.notes ?? '',
      checklist: Object.entries(initial?.checklist ?? {}).map(([label, checked]) => ({
        label,
        checked,
      })),
    },
  });
  const dirty = form.formState.isDirty;
  useEffect(() => {
    onDirtyChange?.(dirty);
  }, [dirty, onDirtyChange]);
  const fields = useFieldArray({ control: form.control, name: 'checklist' });
  return (
    <Form {...form}>
      <form
        noValidate
        onSubmit={form.handleSubmit(data =>
          onSave({
            interview_at: new Date(data.interview_at).toISOString(),
            interviewer: data.interviewer,
            notes: data.notes || null,
            checklist: Object.fromEntries(
              data.checklist.map(item => [item.label.trim(), item.checked])
            ),
          })
        )}
        className="space-y-6"
      >
        <fieldset disabled={form.formState.isSubmitting} className="space-y-6">
          <div className="grid gap-6 md:grid-cols-2">
            <FormField
              control={form.control}
              name="interview_at"
              rules={{
                required: '面接日時を入力してください',
                validate: value =>
                  !Number.isNaN(new Date(value).getTime()) || '有効な日時を入力してください',
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>面接日時（端末の時刻）</FormLabel>
                  <FormControl>
                    <Input {...field} type="datetime-local" required />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="interviewer"
              rules={{
                validate: v => !!v.trim() || '面接担当者を入力してください',
                maxLength: { value: 100, message: '100文字以内で入力してください' },
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>面接担当者名（記録用）</FormLabel>
                  <FormControl>
                    <Input {...field} required maxLength={100} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
          <FormField
            control={form.control}
            name="notes"
            rules={{ maxLength: { value: 5000, message: '5000文字以内で入力してください' } }}
            render={({ field }) => (
              <FormItem>
                <FormLabel>面接メモ</FormLabel>
                <FormControl>
                  <Textarea {...field} maxLength={5000} rows={5} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <fieldset className="space-y-3">
            <legend className="mb-3 text-sm font-medium">確認項目（30件まで）</legend>
            {fields.fields.map((item, index) => (
              <div key={item.id} className="flex items-start gap-3">
                <FormField
                  control={form.control}
                  name={`checklist.${index}.checked`}
                  render={({ field }) => (
                    <FormItem className="pt-3">
                      <FormControl>
                        <Checkbox
                          checked={field.value}
                          onCheckedChange={field.onChange}
                          ref={field.ref}
                          onBlur={field.onBlur}
                          aria-label={`確認項目${index + 1}の確認済み`}
                        />
                      </FormControl>
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name={`checklist.${index}.label`}
                  rules={{
                    validate: value => {
                      if (!value.trim()) return '項目名を入力してください';
                      if (['__proto__', 'constructor', 'prototype'].includes(value.trim()))
                        return '別の項目名にしてください';
                      if (
                        form.getValues('checklist').filter(row => row.label.trim() === value.trim())
                          .length > 1
                      )
                        return '項目名が重複しています';
                      return true;
                    },
                    maxLength: { value: 100, message: '100文字以内で入力してください' },
                  }}
                  render={({ field }) => (
                    <FormItem className="flex-1">
                      <FormControl>
                        <Input
                          {...field}
                          required
                          maxLength={100}
                          aria-label={`確認項目${index + 1}の名前`}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <Button type="button" variant="outline" onClick={() => fields.remove(index)}>
                  項目を削除
                </Button>
              </div>
            ))}
            <Button
              type="button"
              variant="outline"
              disabled={fields.fields.length >= 30}
              onClick={() => fields.append({ label: '', checked: false })}
            >
              確認項目を追加
            </Button>
          </fieldset>
          <Button type="submit" disabled={form.formState.isSubmitting}>
            面接記録を保存
          </Button>
        </fieldset>
      </form>
    </Form>
  );
}
