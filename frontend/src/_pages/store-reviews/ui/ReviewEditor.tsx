import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Review, ReviewCommand, ReceivedVia, Basis } from '@/entities/review';
import {
  Button,
  ConfirmDialog,
  Form,
  FormField,
  FormItem,
  FormLabel,
  FormControl,
  FormMessage,
  Input,
  Textarea,
  Select,
  SelectTrigger,
  SelectContent,
  SelectItem,
  SelectValue,
} from '@/shared/ui';
export type Editor =
  | { action: 'CREATE'; row?: undefined }
  | { action: 'APPROVE' | 'REJECT' | 'GRANT' | 'REVOKE' | 'WITHDRAW' | 'CORRECT'; row: Review };
export function editorTitle(editor: Editor) {
  return {
    CREATE: '口コミを受付',
    APPROVE: '内部承認',
    REJECT: '却下',
    GRANT: '公開許可を記録',
    REVOKE: '公開許可を撤回',
    WITHDRAW: '口コミを取り下げ',
    CORRECT: '訂正再受付',
  }[editor.action];
}
export const dateRule = (value: string) => {
  const date = new Date(value).getTime();
  return (
    (Number.isFinite(date) && date >= Date.parse('2000-01-01T00:00:00Z') && date <= Date.now()) ||
    '2000年以降、現在までの日時を入力してください'
  );
};
export function requestKey() {
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), x =>
    x.toString(16).padStart(2, '0')
  ).join('');
}
export function ReviewEditor({
  editor,
  busy,
  canOrder,
  onSubmit,
}: {
  editor: Editor;
  busy: boolean;
  canOrder: boolean;
  onSubmit: (command: ReviewCommand) => Promise<void>;
}) {
  const seed = editor.action === 'CORRECT' ? editor.row : undefined;
  const received = seed ? new Date(seed.received_at) : null;
  const localReceived = received
    ? new Date(received.getTime() - received.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
    : '';
  const form = useForm({
    defaultValues: {
      reason: '',
      body: seed?.body ?? '',
      display_name: seed?.display_name ?? '',
      received_via: seed?.received_via ?? ('PAPER' as ReceivedVia),
      received_at: localReceived,
      origin_order_id: canOrder ? (seed?.origin_order_id ?? '') : '',
      basis_type: 'WRITTEN' as Basis,
      event_at: '',
      evidence_note: '',
    },
  });
  const create = editor.action === 'CREATE';
  const intake = create || editor.action === 'CORRECT';
  const prepare = form.handleSubmit(values => {
    if (editor.action === 'CREATE' || editor.action === 'CORRECT') {
      const input = {
        body: values.body,
        display_name: values.display_name === '' ? null : values.display_name,
        received_via: values.received_via,
        received_at: new Date(values.received_at).toISOString(),
        origin_order_id: canOrder && values.origin_order_id !== '' ? values.origin_order_id : null,
        dedupe_key: requestKey(),
      };
      if (editor.action === 'CREATE') void onSubmit({ kind: 'CREATE', input });
      else
        setConfirmation({
          kind: 'CORRECT',
          id: editor.row.id,
          input: { ...input, version: editor.row.version, reason: values.reason },
        });
    } else {
      const base = { version: editor.row.version, reason: values.reason, dedupe_key: requestKey() };
      if (editor.action === 'GRANT')
        setConfirmation({
          kind: 'GRANT',
          id: editor.row.id,
          input: {
            version: base.version,
            dedupe_key: base.dedupe_key,
            basis_type: values.basis_type,
            granted_at: new Date(values.event_at).toISOString(),
            evidence_note: values.evidence_note,
          },
        });
      else if (editor.action === 'REVOKE')
        setConfirmation({
          kind: 'REVOKE',
          id: editor.row.id,
          input: { ...base, withdrawal_received_at: new Date(values.event_at).toISOString() },
        });
      else if (editor.action === 'WITHDRAW')
        setConfirmation({ kind: 'WITHDRAW', id: editor.row.id, input: base });
      else
        setConfirmation({
          kind: 'DECIDE',
          id: editor.row.id,
          input: { ...base, decision: editor.action },
        });
    }
  });
  const [confirmation, setConfirmation] = useState<ReviewCommand | null>(null);
  return (
    <>
      {create ? (
        <p>スタッフが受け取った内容を記録します。公開許可は別の操作で記録してください。</p>
      ) : editor.action === 'CORRECT' ? (
        <p>
          旧記録を取り下げ、新しい受付として再審査します。公開許可は引き継ぎません。関連受注は新しい入力に指定した場合だけ確認します。
        </p>
      ) : editor.action === 'GRANT' ? (
        <p>
          この本文・表示名を自店舗ウェブサイトに掲載する許可を記録します。連絡先や不要な個人情報を根拠に転記しないでください。
        </p>
      ) : editor.action === 'REVOKE' ? (
        <p>公開許可を撤回します。内部承認の記録は保持します。再取得は訂正再受付から行います。</p>
      ) : editor.action === 'WITHDRAW' ? (
        <p>この口コミを取り下げ、公開候補から除外します。元に戻すことはできません。</p>
      ) : (
        <p>内部承認のみ。公開には別途許可と公開設定が必要です。</p>
      )}
      <Form {...form}>
        <form className="space-y-6" noValidate onSubmit={prepare}>
          {intake && (
            <>
              <FormField
                control={form.control}
                name="body"
                rules={{
                  validate: value =>
                    (value.trim().length > 0 &&
                      value.replace(/\r\n/g, '\n').length <= 5000 &&
                      !value.includes('\0')) ||
                    '本文を1〜5000文字で入力してください',
                }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>口コミ本文</FormLabel>
                    <FormControl>
                      <Textarea {...field} required disabled={busy} rows={6} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="display_name"
                rules={{
                  validate: value =>
                    value === '' ||
                    (value.trim().length > 0 &&
                      value.trim().length <= 60 &&
                      !value.includes('\0')) ||
                    '表示名を1〜60文字で入力してください（空欄は匿名）',
                }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>表示名（空欄は匿名）</FormLabel>
                    <FormControl>
                      <Input {...field} disabled={busy} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="received_via"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>受付経路</FormLabel>
                    <Select
                      required
                      value={field.value}
                      onValueChange={field.onChange}
                      items={{ PAPER: '書面', VERBAL: '口頭', ELECTRONIC: '電子記録' }}
                      disabled={busy}
                    >
                      <FormControl>
                        <SelectTrigger ref={field.ref}>
                          <SelectValue />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        <SelectItem value="PAPER">書面</SelectItem>
                        <SelectItem value="VERBAL">口頭</SelectItem>
                        <SelectItem value="ELECTRONIC">電子記録</SelectItem>
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="received_at"
                rules={{ validate: dateRule }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>受け取った日時（この端末の時刻）</FormLabel>
                    <FormControl>
                      <Input {...field} type="datetime-local" required disabled={busy} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              {canOrder && (
                <FormField
                  control={form.control}
                  name="origin_order_id"
                  rules={{
                    validate: value =>
                      value === '' ||
                      /^[0-9]{1,32}$/.test(value) ||
                      '関連受注IDを1〜32桁の数字で入力してください',
                  }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>関連受注ID（任意）</FormLabel>
                      <FormControl>
                        <Input {...field} disabled={busy} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              )}
            </>
          )}
          {(editor.action === 'GRANT' || editor.action === 'REVOKE') && (
            <FormField
              control={form.control}
              name="event_at"
              rules={{ validate: dateRule }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    {editor.action === 'GRANT'
                      ? '許可を受けた日時（この端末の時刻）'
                      : '撤回を受けた日時（この端末の時刻）'}
                  </FormLabel>
                  <FormControl>
                    <Input {...field} type="datetime-local" required disabled={busy} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          )}
          {editor.action === 'GRANT' && (
            <>
              <FormField
                control={form.control}
                name="basis_type"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>許可の取得方法</FormLabel>
                    <Select
                      required
                      value={field.value}
                      onValueChange={field.onChange}
                      items={{ WRITTEN: '書面', VERBAL: '口頭', ELECTRONIC_RECORD: '電子記録' }}
                      disabled={busy}
                    >
                      <FormControl>
                        <SelectTrigger ref={field.ref}>
                          <SelectValue />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        <SelectItem value="WRITTEN">書面</SelectItem>
                        <SelectItem value="VERBAL">口頭</SelectItem>
                        <SelectItem value="ELECTRONIC_RECORD">電子記録</SelectItem>
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="evidence_note"
                rules={{
                  validate: value =>
                    (value.trim().length > 0 &&
                      value.trim().length <= 500 &&
                      !value.includes('\0')) ||
                    '根拠を1〜500文字で入力してください',
                }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>許可を確認した根拠</FormLabel>
                    <FormControl>
                      <Textarea {...field} required disabled={busy} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </>
          )}
          {!create && editor.action !== 'GRANT' && (
            <FormField
              control={form.control}
              name="reason"
              rules={{
                validate: value =>
                  (value.trim().length > 0 &&
                    value.trim().length <= 500 &&
                    !value.includes('\0')) ||
                  '理由を1〜500文字で入力してください',
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>判断・変更の理由</FormLabel>
                  <FormControl>
                    <Textarea {...field} required disabled={busy} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          )}
          <Button type="submit" disabled={busy}>
            {create ? '受付を記録' : '確認へ'}
          </Button>
        </form>
      </Form>
      <ConfirmDialog
        open={confirmation !== null}
        title={`${editorTitle(editor)}を確定`}
        description={
          editor.action === 'CORRECT'
            ? '旧記録を取り下げ、新規受付を作ります。新しい承認と公開許可が必要です。'
            : editor.action === 'GRANT'
              ? '表示中の本文・表示名に対する公開許可を記録します。公開連携は未設定です。'
              : editor.action === 'REVOKE'
                ? '記録後は公開候補から除外します。撤回を取り消すことはできません。'
                : editor.action === 'WITHDRAW'
                  ? '口コミを取り下げます。再度扱う場合は訂正再受付が必要です。'
                  : 'この判断を記録します。本文や公開許可は変更しません。'
        }
        confirmLabel="確定する"
        onClose={() => setConfirmation(null)}
        onConfirm={() => {
          if (confirmation) void onSubmit(confirmation);
        }}
      />
    </>
  );
}
