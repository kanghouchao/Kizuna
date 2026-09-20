'use client';

import { useState } from 'react';
import { validateContactValue } from '../lib/contactValidation';
import { useForm } from 'react-hook-form';
import { customerApi, ContactInput, ContactResponse, ContactState } from '@/entities/customer';
import { useCursorList, getApiErrorMessage, isNotFound } from '@/shared/lib';
import { notify } from '@/shared/notify';
import {
  Button,
  Card,
  CardHeader,
  CardTitle,
  CardContent,
  ConfirmDialog,
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  Form,
  FormField,
  FormItem,
  FormLabel,
  FormControl,
  FormMessage,
  Input,
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
  RegionError,
} from '@/shared/ui';

const TYPES = { PHONE: '電話', EMAIL: 'メール', LINE: 'LINE ID' };
const ACTIONS = {
  CREATE: '追加',
  UPDATE: '変更',
  DELETE: '削除',
  PREFERENCE: '優先指定',
  TRANSFER: '統合による移動',
};
const stateLabel = (state?: ContactState) =>
  state
    ? `${TYPES[state.type]}: ${state.value}／${state.preferred ? '優先' : '指定なし'}${state.deleted ? '／削除済み' : ''}／顧客 ${state.customer_id}`
    : '未登録';

export function CustomerContactsSection({ customerId }: { customerId: string }) {
  const contacts = useCursorList(cursor => customerApi.contacts(customerId, { cursor }));
  const history = useCursorList(cursor => customerApi.contactHistory(customerId, { cursor }));
  const [open, setOpen] = useState(false);
  const [gone, setGone] = useState(false);
  const [editing, setEditing] = useState<ContactResponse | null>(null);
  const [deleting, setDeleting] = useState<ContactResponse | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const form = useForm<ContactInput>({ defaultValues: { type: 'PHONE', value: '' } });
  const refresh = () => {
    contacts.reload();
    history.reload();
  };
  const beginEdit = (contact: ContactResponse | null) => {
    setGone(false);
    setEditing(contact);
    form.reset(
      contact ? { type: contact.type, value: contact.value } : { type: 'PHONE', value: '' }
    );
    setOpen(true);
  };
  const mutate = async (operation: () => Promise<unknown>) => {
    setBusy(true);
    try {
      await operation();
      refresh();
      notify.success('連絡先を更新しました');
    } catch (error) {
      if (isNotFound(error)) {
        refresh();
        notify.warning(getApiErrorMessage(error, '連絡先が見つからないため一覧を更新しました'));
      } else notify.error(getApiErrorMessage(error, '連絡先を更新できませんでした'));
    } finally {
      setBusy(false);
    }
  };
  const save = form.handleSubmit(async data => {
    try {
      if (editing) await customerApi.updateContact(customerId, editing.id, data);
      else await customerApi.addContact(customerId, data);
      setOpen(false);
      refresh();
      notify.success('連絡先を保存しました');
    } catch (error) {
      if (isNotFound(error)) setGone(true);
      else notify.error(getApiErrorMessage(error, '連絡先を保存できませんでした'));
    }
  });
  return (
    <>
      <Card>
        <CardHeader className="flex-row items-center justify-between gap-4">
          <CardTitle role="heading" aria-level={2}>
            連絡先
          </CardTitle>
          <Button type="button" onClick={() => beginEdit(null)}>
            連絡先を追加
          </Button>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-muted-foreground">
            登録や優先指定は、本人確認や連絡の許可を意味しません。
          </p>
          {contacts.failed ? (
            <RegionError message="連絡先を取得できませんでした" onRetry={contacts.reload} />
          ) : contacts.isLoading && contacts.rows.length === 0 ? (
            <p>読み込み中...</p>
          ) : contacts.rows.length === 0 ? (
            <p>連絡先はありません</p>
          ) : (
            contacts.rows.map(contact => (
              <div
                key={contact.id}
                className="flex flex-wrap items-center justify-between gap-4 rounded-lg border p-4"
              >
                <div className="min-w-0 flex-1">
                  <p className="text-sm">
                    {TYPES[contact.type]}
                    {contact.preferred && '・優先'}
                  </p>
                  <p className="break-all">{contact.value}</p>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button
                    variant="outline"
                    disabled={busy}
                    onClick={() =>
                      void mutate(() =>
                        customerApi.setContactPreference(
                          customerId,
                          contact.type,
                          contact.preferred ? null : contact.id
                        )
                      )
                    }
                  >
                    {contact.preferred ? '優先を解除' : '優先にする'}
                  </Button>
                  <Button variant="outline" disabled={busy} onClick={() => beginEdit(contact)}>
                    編集
                  </Button>
                  <Button
                    variant="destructive"
                    disabled={busy}
                    onClick={() => {
                      setDeleting(contact);
                      setConfirmOpen(true);
                    }}
                  >
                    削除
                  </Button>
                </div>
              </div>
            ))
          )}
          {contacts.hasMore && (
            <Button variant="outline" disabled={contacts.isLoading} onClick={contacts.loadMore}>
              連絡先をさらに読み込む
            </Button>
          )}
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle role="heading" aria-level={2}>
            連絡先の変更履歴
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {history.failed ? (
            <RegionError message="連絡先の履歴を取得できませんでした" onRetry={history.reload} />
          ) : history.isLoading && history.rows.length === 0 ? (
            <p>読み込み中...</p>
          ) : history.rows.length === 0 ? (
            <p>変更履歴はありません</p>
          ) : (
            history.rows.map(row => (
              <div key={row.id} className="space-y-2 rounded-lg border p-4 text-sm">
                <p>
                  {ACTIONS[row.action]}・{new Date(row.occurred_at).toLocaleString('ja-JP')}・操作者{' '}
                  {row.actor_id}
                </p>
                <p className="break-all">
                  連絡先 ID: {row.contact_id}／登録元顧客: {row.origin_customer_id}
                </p>
                <p className="break-all">変更前: {stateLabel(row.before)}</p>
                <p className="break-all">変更後: {stateLabel(row.after)}</p>
              </div>
            ))
          )}
          {history.hasMore && (
            <Button variant="outline" disabled={history.isLoading} onClick={history.loadMore}>
              履歴をさらに読み込む
            </Button>
          )}
        </CardContent>
      </Card>
      <Dialog
        open={open}
        onOpenChange={next => {
          setOpen(next);
          if (!next && gone) refresh();
        }}
      >
        <DialogContent
          className="max-h-[calc(100vh-2rem)] overflow-y-auto"
          aria-describedby={undefined}
        >
          <DialogHeader>
            <DialogTitle>{editing ? '連絡先を編集' : '連絡先を追加'}</DialogTitle>
          </DialogHeader>
          {gone ? (
            <div role="alert" className="space-y-4">
              <p>この連絡先は見つかりませんでした。</p>
              <Button
                onClick={() => {
                  setOpen(false);
                  refresh();
                }}
              >
                閉じる
              </Button>
            </div>
          ) : (
            <Form {...form}>
              <form onSubmit={save} noValidate className="space-y-6">
                <FormField
                  control={form.control}
                  name="type"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>種類</FormLabel>
                      <Select value={field.value} onValueChange={field.onChange} items={TYPES}>
                        <FormControl>
                          <SelectTrigger ref={field.ref}>
                            <SelectValue />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {Object.entries(TYPES).map(([value, label]) => (
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
                <FormField
                  control={form.control}
                  name="value"
                  rules={{
                    required: '連絡先を入力してください',
                    maxLength: { value: 320, message: '320 文字以内で入力してください' },
                    validate: value => validateContactValue(value, form.getValues('type')),
                  }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>連絡先の値</FormLabel>
                      <FormControl>
                        <Input
                          {...field}
                          type={
                            form.watch('type') === 'EMAIL'
                              ? 'email'
                              : form.watch('type') === 'PHONE'
                                ? 'tel'
                                : 'text'
                          }
                          required
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <Button type="submit" disabled={form.formState.isSubmitting}>
                  保存する
                </Button>
              </form>
            </Form>
          )}
        </DialogContent>
      </Dialog>
      <ConfirmDialog
        open={confirmOpen}
        title="連絡先を削除しますか？"
        description={
          deleting ? `${deleting.value} を通常の表示から除外します。履歴は残ります。` : undefined
        }
        onClose={() => setConfirmOpen(false)}
        onConfirm={() => {
          if (deleting) void mutate(() => customerApi.deleteContact(customerId, deleting.id));
        }}
      />
    </>
  );
}
