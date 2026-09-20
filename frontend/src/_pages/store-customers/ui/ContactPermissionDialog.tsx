'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import {
  customerApi,
  ContactPermissionInput,
  ContactPurpose,
  ContactResponse,
} from '@/entities/customer';
import { getApiErrorMessage, isNotFound } from '@/shared/lib';
import { notify } from '@/shared/notify';
import {
  Button,
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  Textarea,
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
} from '@/shared/ui';
import { CONTACT_PURPOSES, PERMISSION_STATUSES } from '../lib/contactPermissions';

export function ContactPermissionDialog({
  customerId,
  contact,
  purpose,
  open,
  onClose,
  onSaved,
}: {
  customerId: string;
  contact: ContactResponse;
  purpose: ContactPurpose;
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [gone, setGone] = useState(false);
  const status = purpose === 'BUSINESS' ? contact.business_status : contact.marketing_status;
  const form = useForm<ContactPermissionInput>({
    defaultValues: { status, source: '', reason: '' },
  });
  const close = () => {
    onClose();
    if (gone) onSaved();
  };
  const save = form.handleSubmit(async data => {
    try {
      await customerApi.setContactPermission(customerId, contact.id, purpose, {
        ...data,
        source: data.source.trim(),
        reason: data.reason.trim(),
      });
      onClose();
      onSaved();
      notify.success('連絡可否を更新しました');
    } catch (error) {
      if (isNotFound(error)) setGone(true);
      else notify.error(getApiErrorMessage(error, '連絡可否を更新できませんでした'));
    }
  });
  return (
    <Dialog
      open={open}
      onOpenChange={next => {
        if (!next) close();
      }}
    >
      <DialogContent
        className="max-h-[calc(100vh-2rem)] overflow-y-auto"
        aria-describedby={undefined}
      >
        <DialogHeader>
          <DialogTitle>{CONTACT_PURPOSES[purpose]}の可否を変更</DialogTitle>
        </DialogHeader>
        {gone ? (
          <div role="alert" className="space-y-4">
            <p>この連絡先は見つかりませんでした。</p>
            <Button onClick={close}>閉じる</Button>
          </div>
        ) : (
          <Form {...form}>
            <form onSubmit={save} noValidate className="space-y-6">
              <p className="break-all">{contact.value}</p>
              <p className="text-sm text-muted-foreground">
                同じ連絡先の有効行がすべて許可になるまで、連絡はできません。変更の出所と根拠を記録してください。
              </p>
              <FormField
                control={form.control}
                name="status"
                rules={{ required: '状態を選択してください' }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>状態</FormLabel>
                    <Select
                      value={field.value}
                      onValueChange={field.onChange}
                      items={PERMISSION_STATUSES}
                      required
                    >
                      <FormControl>
                        <SelectTrigger ref={field.ref}>
                          <SelectValue />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {Object.entries(PERMISSION_STATUSES).map(([value, label]) => (
                          <SelectItem
                            key={value}
                            value={value}
                            disabled={status === 'DENIED' && value === 'UNKNOWN'}
                          >
                            {label}
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
                name="source"
                rules={{
                  validate: value => value.trim().length > 0 || '出所を入力してください',
                  maxLength: { value: 200, message: '200 文字以内で入力してください' },
                }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>出所</FormLabel>
                    <FormControl>
                      <Input {...field} required />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="reason"
                rules={{
                  validate: value => value.trim().length > 0 || '根拠を入力してください',
                  maxLength: { value: 2000, message: '2000 文字以内で入力してください' },
                }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>根拠</FormLabel>
                    <FormControl>
                      <Textarea {...field} required className="max-h-48 overflow-y-auto" />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <Button type="submit" disabled={form.formState.isSubmitting}>
                可否を保存
              </Button>
            </form>
          </Form>
        )}
      </DialogContent>
    </Dialog>
  );
}
