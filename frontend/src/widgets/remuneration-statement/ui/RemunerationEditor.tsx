'use client';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { remunerationApi, type Bonus, type Guarantee } from '../api';
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

export type EditTarget =
  | { kind: 'guarantee'; item?: Guarantee; version: number; cancel?: boolean }
  | { kind: 'bonus'; item?: Bonus; cancel?: boolean };
interface Values {
  date: string;
  amount: number | undefined;
  stopped: boolean;
  reason: string;
  correction_reason: string;
}
export function RemunerationEditor({
  target,
  open,
  personId,
  onClose,
  onSaved,
}: {
  target: EditTarget;
  open: boolean;
  personId: number;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [confirm, setConfirm] = useState(false);
  const [pending, setPending] = useState<Values | null>(null);
  const [request, setRequest] = useState<{ fingerprint: string; id: string } | null>(null);
  const correcting = !!target.item;
  const form = useForm<Values>({
    defaultValues: {
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
  const save = async (values: Values) => {
    const fingerprint = JSON.stringify(values);
    const attempt =
      request?.fingerprint === fingerprint ? request : { fingerprint, id: createRequestId() };
    setRequest(attempt);
    const requestId = attempt.id;
    try {
      if (target.cancel && target.item) {
        const body = {
          reason: values.reason.trim(),
          request_id: requestId,
          expected_version: target.kind === 'guarantee' ? target.version : target.item.version,
        };
        if (target.kind === 'guarantee')
          await remunerationApi.cancelGuarantee(target.item.id, body);
        else await remunerationApi.cancelBonus(target.item.id, body);
      } else if (target.kind === 'guarantee') {
        const body = {
          effective_from: values.date,
          state: values.stopped ? ('STOPPED' as const) : ('ACTIVE' as const),
          daily_amount: values.stopped ? null : (values.amount ?? 0),
          reason: values.reason.trim(),
          request_id: requestId,
          expected_version: target.version,
        };
        if (target.item)
          await remunerationApi.correctGuarantee(target.item.id, {
            ...body,
            correction_reason: values.correction_reason.trim(),
          });
        else await remunerationApi.createGuarantee(personId, body);
      } else {
        const body = {
          award_date: values.date,
          amount: values.amount ?? 0,
          reason: values.reason.trim(),
          request_id: requestId,
        };
        if (target.item)
          await remunerationApi.correctBonus(target.item.id, {
            ...body,
            expected_version: target.item.version,
            correction_reason: values.correction_reason.trim(),
          });
        else await remunerationApi.createBonus(personId, body);
      }
      notify.success(target.cancel ? '記録を取り消しました' : '記録を保存しました');
      onSaved();
      onClose();
    } catch (error) {
      notify.error(
        getApiErrorMessage(error, '保存できませんでした。競合時は閉じて再照会してください。')
      );
    }
  };
  const submit = form.handleSubmit(async values => {
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
          if (!open) onClose();
        }}
      >
        <DialogContent className="max-h-[calc(100vh-2rem)] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{title}</DialogTitle>
          </DialogHeader>
          <Form {...form}>
            <form
              noValidate
              onSubmit={event => {
                event.stopPropagation();
                void submit(event);
              }}
              className="space-y-6"
            >
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
              <div className="flex gap-3">
                <Button type="button" variant="outline" onClick={onClose}>
                  閉じる
                </Button>
                <Button type="submit" disabled={form.formState.isSubmitting}>
                  {target.cancel ? '取消内容を確認' : '保存する'}
                </Button>
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

function createRequestId(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
