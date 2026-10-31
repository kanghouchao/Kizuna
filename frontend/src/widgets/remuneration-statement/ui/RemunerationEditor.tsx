'use client';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import {
  createRequestId,
  submitOperation,
  usePendingOperation,
  type EditTarget,
  type EditValues,
  type Operation,
} from '../model/operation';
import { getApiErrorMessage } from '@/shared/lib';
import { notify } from '@/shared/notify';
import {
  Button,
  Checkbox,
  ConfirmDialog,
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
} from '@/shared/ui';

export function RemunerationEditor({
  target,
  open,
  personId,
  personName,
  scope,
  recovery,
  onClose,
  onSaved,
}: {
  target: EditTarget;
  open: boolean;
  personId: number;
  personName: string;
  scope: string;
  recovery?: Operation;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [confirm, setConfirm] = useState(false);
  const [pending, setPending] = useState<EditValues | null>(null);
  const outstanding = usePendingOperation(scope);
  const sending = outstanding?.phase === 'submitting';
  const locked = outstanding !== null;
  const correcting = !!target.item;
  const form = useForm<EditValues>({
    defaultValues: recovery?.values ?? {
      date:
        target.kind === 'guarantee'
          ? (target.item?.effective_from ?? '')
          : (target.item?.award_date ?? ''),
      amount: target.kind === 'guarantee' ? target.item?.daily_amount : target.item?.amount,
      stopped: target.kind === 'guarantee' && target.item?.state === 'STOPPED',
      reason: target.cancel ? '' : (target.item?.reason ?? ''),
      correction_reason: '',
    },
  });
  const stopped = useWatch({ control: form.control, name: 'stopped' });
  const save = async (values?: EditValues) => {
    const original = outstanding?.operation;
    if (sending || (outstanding && !original)) return;
    const operation = original ?? {
      scope,
      personId,
      personName,
      target: { ...target, ...(target.item ? { item: { ...target.item } } : {}) } as EditTarget,
      values: { ...values! },
      requestId: createRequestId(),
    };
    try {
      if (!(await submitOperation(operation, !!original))) return;
      notify.success(target.cancel ? '記録を取り消しました' : '記録を保存しました');
      onSaved();
      onClose();
    } catch (error) {
      notify.error(
        getApiErrorMessage(
          error,
          '保存結果を確認できませんでした。元の内容で結果を確認してください。'
        )
      );
    }
  };
  const submit = form.handleSubmit(async values => {
    if (locked) return;
    if (target.cancel) {
      setPending(values);
      setConfirm(true);
    } else await save(values);
  });
  const title = `${target.kind === 'guarantee' ? '日額保証' : 'ボーナス'}${target.cancel ? 'の取消' : correcting ? 'の訂正' : 'の登録'}`;
  return (
    <>
      <Dialog
        open={open}
        onOpenChange={open => {
          if (!open && !sending) onClose();
        }}
      >
        <DialogContent className="max-h-[calc(100vh-2rem)] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{title}</DialogTitle>
          </DialogHeader>
          {outstanding?.phase === 'unknown' && (
            <div role="alert" className="space-y-2 rounded-lg border p-4">
              <p>送信結果が未確認です。{personName} の元の内容を保持しています。</p>
              <p>
                重複を防ぐため編集と新しい登録を止めています。結果確認後に続けてください。ページの再読み込みや終了は避けてください。
              </p>
            </div>
          )}
          <Form {...form}>
            <form
              noValidate
              onSubmit={event => {
                event.stopPropagation();
                void submit(event);
              }}
              className="space-y-6"
            >
              <fieldset disabled={locked} className="space-y-6">
                {!target.cancel && (
                  <>
                    <FormField
                      control={form.control}
                      name="date"
                      rules={{ required: '日付を入力してください' }}
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>
                            {target.kind === 'guarantee' ? '適用開始日' : '帰属日'}
                          </FormLabel>
                          <FormControl>
                            <Input type="date" required {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    {target.kind === 'guarantee' && (
                      <FormField
                        control={form.control}
                        name="stopped"
                        render={({ field }) => (
                          <FormItem className="flex items-center gap-3">
                            <FormControl>
                              <Checkbox
                                checked={field.value}
                                onCheckedChange={field.onChange}
                                ref={field.ref}
                              />
                            </FormControl>
                            <FormLabel>この日から保証を停止する</FormLabel>
                          </FormItem>
                        )}
                      />
                    )}
                    {!(target.kind === 'guarantee' && stopped) && (
                      <FormField
                        control={form.control}
                        name="amount"
                        rules={{
                          validate: value =>
                            (Number.isSafeInteger(value) &&
                              value !== undefined &&
                              value >= (target.kind === 'guarantee' ? 0 : 1)) ||
                            '金額を整数の円で入力してください',
                        }}
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>
                              {target.kind === 'guarantee' ? '日額（円）' : '付与額（円）'}
                            </FormLabel>
                            <FormControl>
                              <Input
                                type="number"
                                min={target.kind === 'guarantee' ? 0 : 1}
                                step="1"
                                required
                                {...field}
                                value={field.value ?? ''}
                                onChange={event =>
                                  field.onChange(
                                    Number.isNaN(event.target.valueAsNumber)
                                      ? undefined
                                      : event.target.valueAsNumber
                                  )
                                }
                              />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    )}
                  </>
                )}
                <FormField
                  control={form.control}
                  name="reason"
                  rules={{
                    required: '理由を入力してください',
                    maxLength: { value: 500, message: '理由は500文字以内にしてください' },
                    validate: value => !!value.trim() || '理由を入力してください',
                  }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{target.cancel ? '取消理由' : '理由・説明'}</FormLabel>
                      <FormControl>
                        <Input required maxLength={500} {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                {correcting && !target.cancel && (
                  <FormField
                    control={form.control}
                    name="correction_reason"
                    rules={{
                      required: '訂正理由を入力してください',
                      maxLength: { value: 500, message: '訂正理由は500文字以内にしてください' },
                      validate: value => !!value.trim() || '訂正理由を入力してください',
                    }}
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>訂正理由（内部記録）</FormLabel>
                        <FormControl>
                          <Input required maxLength={500} {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                )}
                {target.kind === 'guarantee' && (
                  <p className="text-sm text-muted-foreground">
                    適用開始日から次の条件まで有効です。過去条件の訂正・取消は原月の明細にも反映されます。
                  </p>
                )}
              </fieldset>
              <div className="flex flex-wrap gap-3">
                <Button type="button" variant="outline" disabled={sending} onClick={onClose}>
                  閉じる
                </Button>
                {outstanding ? (
                  <Button type="button" disabled={sending} onClick={() => void save()}>
                    {sending ? '送信中...' : '元の内容で結果を確認'}
                  </Button>
                ) : (
                  <Button type="submit" disabled={form.formState.isSubmitting}>
                    {target.cancel ? '取消内容を確認' : '保存する'}
                  </Button>
                )}
              </div>
            </form>
          </Form>
        </DialogContent>
      </Dialog>
      <ConfirmDialog
        open={confirm}
        title="記録を取り消しますか？"
        description={
          target.kind === 'guarantee'
            ? 'この条件を除いて再計算します。直前の条件が次の条件まで延長される場合があります。'
            : '付与額は集計から除外され、取消の履歴は残ります。'
        }
        confirmLabel="取り消す"
        onClose={() => setConfirm(false)}
        onConfirm={() => {
          if (pending) void save(pending);
        }}
      />
    </>
  );
}
