'use client';

import { useEffect, useId, useRef, useState } from 'react';
import { MePending, hasPermission, useMe, useStoreContext } from '@/entities/user';
import { useForm } from 'react-hook-form';
import {
  Delivery,
  DeliveryInput,
  DeliveryResolution,
  DeliveryStatus,
  NotificationApi,
  memberNotificationApi,
  notificationApi,
} from '@/entities/notification-delivery';
import {
  getApiErrorMessage,
  isNotFound,
  memoryOperationStore,
  useListPage,
  useOperation,
  useResource,
  useVersionedWrite,
  GENERATED_ID_PATTERN,
} from '@/shared/lib';
import { notify } from '@/shared/notify';
import {
  Badge,
  Button,
  Dialog,
  DialogFrame,
  DialogFrameActions,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  KeyValueList,
  KeyValueRow,
  Label,
  RadioGroup,
  RadioGroupItem,
  Textarea,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Pager,
  RegionError,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';
import { ListPage } from '@/widgets/list-page';

const statuses: Record<DeliveryStatus, string> = {
  DRAFT: '下書き',
  QUEUED: '送信待ち',
  DISPATCHED: '引き渡し済み',
  SENDING: '送信中',
  SENT: 'メール提供元受理',
  FAILED: '未送信',
  BLOCKED: '送信不可',
  UNKNOWN: '結果不明',
};
const contactLabels = {
  ALLOWED: '業務連絡が許可されています',
  STORE_DENIED: '店舗の連絡拒否があります',
  NOT_ALLOWED: '業務連絡の許可が確認できません',
};
const failureLabels: Record<string, string> = {
  UNAVAILABLE: '送信設定が利用できません',
  FAILED: 'メール提供元が送信を拒否しました',
  UNKNOWN: 'メール提供元の受理を確認できません',
  INTERRUPTED: '送信処理の中断により結果が不明です',
  CONTACT_NOT_ALLOWED: '最新の連絡可否で送信できません',
  AUTHORIZATION_DENIED: '実行主体の権限がありません',
  SOURCE_UNAVAILABLE: '業務起点を確認できません',
  MANUAL_RESOLUTION: '職員がメール提供元の記録を確認して判定しました',
};
const resolutionLabels: Partial<Record<DeliveryStatus, string>> = {
  SENT: '送達済みと判定',
  FAILED: '未送達と判定',
};
const sourceLabels: Record<Delivery['source_type'], string> = {
  ORDER: '受注',
  APPLICATION: 'ゲスト申請',
  MEMBER: '会員',
};
function Status({ status, label }: { status: DeliveryStatus; label?: string }) {
  return (
    <Badge
      variant="outline"
      className={`border-transparent ${status === 'SENT' ? 'bg-success/10 text-success-strong' : ['FAILED', 'BLOCKED', 'UNKNOWN'].includes(status) ? 'bg-warning/10 text-warning-strong' : 'bg-muted text-foreground'}`}
    >
      {label ?? statuses[status]}
    </Badge>
  );
}
function time(value: string) {
  return new Date(value).toLocaleString('ja-JP');
}
function localInputTime(value: string) {
  const date = new Date(value);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}
function useActive() {
  const active = useRef(true);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  return active;
}

function History({ api, id }: { api: NotificationApi; id: string }) {
  const history = useListPage(page => api.history(id, page));
  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold">送信試行</h2>
      {history.failed ? (
        <RegionError
          message="送信履歴を取得できませんでした"
          onRetry={() => void history.reload()}
        />
      ) : history.isLoading ? (
        <p>読み込み中...</p>
      ) : history.rows.length === 0 ? (
        <p>送信試行はありません</p>
      ) : (
        <ol className="space-y-3">
          {history.rows.map(row => {
            const manual = row.failure_code === 'MANUAL_RESOLUTION';
            return (
              <li key={row.id} className="rounded-lg border p-4 space-y-2">
                <p>
                  第{row.attempt_number}回{' '}
                  <Status
                    status={row.status}
                    label={manual ? resolutionLabels[row.status] : undefined}
                  />
                </p>
                <p className="text-sm">
                  {time(row.started_at)} /{' '}
                  {manual ? '職員の判定' : `実行 #${row.task_execution_id}`}
                </p>
                <p className="whitespace-pre-wrap wrap-anywhere">{row.reason}</p>
                {row.failure_code && (
                  <p>{failureLabels[row.failure_code] ?? '送信を完了できませんでした'}</p>
                )}
              </li>
            );
          })}
        </ol>
      )}
      {!history.failed && (
        <Pager
          page={history.page}
          pageCount={history.pageCount}
          total={history.total}
          rowCount={history.rows.length}
          disabled={history.isLoading}
          onPageChange={page => void history.onPageChange(page)}
        />
      )}
    </section>
  );
}
type ResolutionDraft = { outcome: DeliveryResolution | ''; reason: string };
function Resolution({
  api,
  row,
  write,
  onResolved,
}: {
  api: NotificationApi;
  row: Delivery;
  write: ReturnType<typeof useVersionedWrite>;
  onResolved: (result: Delivery) => void;
}) {
  const form = useForm<ResolutionDraft>({ defaultValues: { outcome: '', reason: '' } });
  const labelId = useId();
  const resolve = async ({ outcome, reason }: ResolutionDraft) => {
    if (outcome === '') return;
    await write.write(() => api.resolve(row.id, row.version, outcome, reason), {
      success: '判定を記録しました',
      failure: '判定を記録できませんでした',
      onSuccess: onResolved,
    });
  };
  return (
    <Form {...form}>
      <form
        noValidate
        onSubmit={event => void form.handleSubmit(resolve)(event)}
        className="space-y-4 rounded-lg border p-4"
      >
        <p className="text-sm">
          メール提供元の管理画面などで配信の記録を確認し、結果を判定してください。判定は送信試行の履歴に職員の判定として残ります。
        </p>
        {write.conflict && (
          <div role="alert" className="space-y-2">
            <p className="text-destructive-strong">
              通知の状態が変わりました。最新の状態を取得してから判定してください。
            </p>
            <Button type="button" variant="outline" onClick={() => void write.reload()}>
              最新の状態を取得して判定をやり直す
            </Button>
          </div>
        )}
        <FormField
          control={form.control}
          name="outcome"
          rules={{ required: '判定を選択してください' }}
          render={({ field }) => (
            <FormItem>
              <FormLabel id={labelId}>判定</FormLabel>
              <FormControl>
                <RadioGroup
                  aria-labelledby={labelId}
                  value={field.value}
                  onValueChange={value => field.onChange(value as DeliveryResolution)}
                  required
                >
                  <Label className="flex items-center gap-2 font-normal">
                    <RadioGroupItem value="FAILED" ref={field.ref} />
                    未送達（再試行できるようにする）
                  </Label>
                  <Label className="flex items-center gap-2 font-normal">
                    <RadioGroupItem value="SENT" />
                    送達済み（再試行しない）
                  </Label>
                </RadioGroup>
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name="reason"
          rules={{
            required: '判定の理由を入力してください',
            maxLength: { value: 500, message: '500文字以内で入力してください' },
            validate: v => v.trim().length > 0 || '判定の理由を入力してください',
          }}
          render={({ field }) => (
            <FormItem>
              <FormLabel>確認した内容・判定の理由</FormLabel>
              <FormControl>
                <Textarea {...field} required maxLength={500} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <Button type="submit" disabled={write.busy || form.formState.isSubmitting}>
          判定を記録する
        </Button>
      </form>
    </Form>
  );
}
function Detail({
  api,
  id,
  canSend,
  onChanged,
  onMissing,
}: {
  api: NotificationApi;
  id: string;
  canSend: boolean;
  onChanged: () => void;
  onMissing: () => void;
}) {
  const detail = useResource(() => api.get(id), [id]);
  const resolution = useVersionedWrite(detail.reload);
  const form = useForm<{ reason: string }>({ defaultValues: { reason: '' } });
  const active = useActive();
  const busy = useRef(false);
  const [historyKey, setHistoryKey] = useState(0);
  const [missing, setMissing] = useState(false);
  useEffect(() => {
    if (missing || detail.failure === 'notFound') onChanged();
  }, [missing, detail.failure, onChanged]);
  const act = async ({ reason }: { reason: string }) => {
    if (busy.current || !detail.data) return;
    busy.current = true;
    const row = detail.data;
    try {
      const result = await (row.status === 'DRAFT' ? api.queue : api.retry)(
        id,
        row.version,
        reason
      );
      if (!active.current) return;
      detail.setData(result);
      form.reset();
      setHistoryKey(n => n + 1);
      onChanged();
      notify.success('通知を送信待ちに登録しました');
    } catch (error) {
      if (active.current) {
        if (isNotFound(error)) setMissing(true);
        else notify.error(getApiErrorMessage(error, '送信待ちに登録できませんでした'));
      }
    } finally {
      busy.current = false;
    }
  };
  if (detail.isLoading) return <p>読み込み中...</p>;
  if (missing || detail.failure === 'notFound')
    return (
      <>
        <p role="alert">通知が見つかりません</p>
        <DialogFrameActions>
          <Button variant="outline" onClick={onMissing}>
            一覧へ戻る
          </Button>
        </DialogFrameActions>
      </>
    );
  if (detail.failure !== null || !detail.data)
    return (
      <RegionError message="通知を取得できませんでした" onRetry={() => void detail.reload()} />
    );
  const row = detail.data;
  return (
    <div className="space-y-6">
      <KeyValueList>
        <KeyValueRow label="状態">
          <Status status={row.status} />
        </KeyValueRow>
        <KeyValueRow label="起点">
          {sourceLabels[row.source_type]} #{row.source_id}
        </KeyValueRow>
        <KeyValueRow label="送信予定">{time(row.scheduled_at)}</KeyValueRow>
        <KeyValueRow label="件名" long>
          {row.subject}
        </KeyValueRow>
        <KeyValueRow label="本文" long>
          {row.body}
        </KeyValueRow>
        <KeyValueRow label="連絡可否">{contactLabels[row.contact_decision]}</KeyValueRow>
      </KeyValueList>
      <p className="text-sm text-muted-foreground">
        送信直前に連絡可否を再確認します。メール提供元受理は受信・閲覧を意味しません。
      </p>
      {row.transport_availability === 'UNAVAILABLE' && (
        <p role="status" className="text-warning-strong">
          メールの送信設定が利用できません。未設定のまま送信に成功することはありません。
        </p>
      )}
      <p className="text-sm">
        自動実行は未設定です。送信予定時刻を過ぎた通知は、権限を持つ管理者による処理実行を待ちます。
      </p>
      {row.status === 'UNKNOWN' && (
        <p role="status" className="text-warning-strong">
          送信結果が不明です。重複送信を防ぐため、このままでは再試行できません。未送達と判定した通知だけを再試行できます。
        </p>
      )}
      <Button
        variant="outline"
        disabled={form.formState.isSubmitting}
        onClick={() => {
          void resolution.reload();
          setHistoryKey(n => n + 1);
        }}
      >
        最新の状態を取得
      </Button>
      {canSend && row.status === 'UNKNOWN' && (
        <Resolution
          api={api}
          key={row.version}
          row={row}
          write={resolution}
          onResolved={result => {
            if (!active.current) return;
            detail.setData(result);
            setHistoryKey(n => n + 1);
            onChanged();
          }}
        />
      )}
      {canSend && ['DRAFT', 'FAILED', 'BLOCKED'].includes(row.status) && (
        <Form {...form}>
          <form
            noValidate
            onSubmit={event => void form.handleSubmit(act)(event)}
            className="space-y-4"
          >
            <FormField
              control={form.control}
              name="reason"
              rules={{
                required: '確認・再試行の理由を入力してください',
                maxLength: { value: 500, message: '500文字以内で入力してください' },
                validate: v => v.trim().length > 0 || '理由を入力してください',
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>内容を確認した理由・再試行の理由</FormLabel>
                  <FormControl>
                    <Textarea {...field} required maxLength={500} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <Button type="submit" disabled={form.formState.isSubmitting}>
              {row.status === 'DRAFT' ? '確認して送信待ちにする' : '明示的に再試行する'}
            </Button>
          </form>
        </Form>
      )}
      <History key={historyKey} api={api} id={id} />
    </div>
  );
}

type Draft = Omit<DeliveryInput, 'channel' | 'purpose'>;
function Create({ onCreated }: { onCreated: (row: Delivery) => void }) {
  const formId = useId();
  const operation = useOperation('notification-deliveries', {
    storeId: useStoreContext().currentStoreId,
    store: memoryOperationStore,
    send: notificationApi.create,
  });
  const pending = operation.pending;
  const uncertain = pending?.phase === 'unknown';
  const form = useForm<Draft>({
    defaultValues: pending
      ? { ...pending.payload, scheduled_at: localInputTime(pending.payload.scheduled_at) }
      : { source_type: 'ORDER', source_id: '', subject: '', body: '', scheduled_at: '' },
  });
  const active = useActive();
  const save = async (values?: Draft) => {
    try {
      const done = values
        ? await operation.start({
            ...values,
            scheduled_at: new Date(values.scheduled_at).toISOString(),
            channel: 'EMAIL',
            purpose: 'BUSINESS',
          })
        : await operation.replay();
      if (done && active.current) {
        onCreated(done.value);
        notify.success('通知の下書きを作成しました');
      }
    } catch (error) {
      if (active.current)
        notify.error(
          getApiErrorMessage(error, '作成結果を確認できませんでした。同じ要求で確認してください')
        );
    }
  };
  return (
    <Form {...form}>
      <form
        id={formId}
        noValidate
        onSubmit={event => {
          if (pending) {
            event.preventDefault();
            void save();
          } else {
            void form.handleSubmit(save)(event);
          }
        }}
        className="space-y-4"
      >
        <p className="text-sm text-muted-foreground">
          受注・ゲスト申請のIDを指定します。宛先は保存済みの許可から送信直前に解決します。
        </p>
        <fieldset disabled={pending !== null || form.formState.isSubmitting} className="space-y-4">
          <FormField
            control={form.control}
            name="source_type"
            render={({ field }) => (
              <FormItem>
                <FormLabel>起点</FormLabel>
                <Select
                  value={field.value}
                  onValueChange={field.onChange}
                  items={{ ORDER: '受注', APPLICATION: 'ゲスト申請' }}
                >
                  <FormControl>
                    <SelectTrigger ref={field.ref}>
                      <SelectValue />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    <SelectItem value="ORDER">受注</SelectItem>
                    <SelectItem value="APPLICATION">ゲスト申請</SelectItem>
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="source_id"
            rules={{
              required: '起点のIDを入力してください',
              pattern: { value: GENERATED_ID_PATTERN, message: 'IDの形式が正しくありません' },
            }}
            render={({ field }) => (
              <FormItem>
                <FormLabel>受注・ゲスト申請ID</FormLabel>
                <FormControl>
                  <Input {...field} required />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="subject"
            rules={{
              required: '件名を入力してください',
              maxLength: { value: 200, message: '200文字以内で入力してください' },
              validate: v => v.trim().length > 0 || '件名を入力してください',
            }}
            render={({ field }) => (
              <FormItem>
                <FormLabel>件名</FormLabel>
                <FormControl>
                  <Input {...field} required maxLength={200} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="body"
            rules={{
              required: '本文を入力してください',
              maxLength: { value: 10000, message: '10000文字以内で入力してください' },
              validate: v => v.trim().length > 0 || '本文を入力してください',
            }}
            render={({ field }) => (
              <FormItem>
                <FormLabel>本文</FormLabel>
                <FormControl>
                  <Textarea {...field} required maxLength={10000} rows={6} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="scheduled_at"
            rules={{
              required: '送信予定を入力してください',
              validate: v => {
                const t = Date.parse(v);
                return (
                  (Number.isFinite(t) &&
                    t >= Date.parse('2000-01-01T00:00:00Z') &&
                    t <= Date.now() + 366 * 86400000) ||
                  '2000年以降、現在から366日以内で指定してください'
                );
              },
            }}
            render={({ field }) => (
              <FormItem>
                <FormLabel>送信予定（この端末の時刻）</FormLabel>
                <FormControl>
                  <Input {...field} type="datetime-local" required />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </fieldset>
        {uncertain && (
          <p role="status">
            作成結果が確認できません。同じ内容・要求キーで再確認します。入力をやり直す場合は一覧で重複がないことを確認してください。
          </p>
        )}
      </form>
      <DialogFrameActions>
        <Button
          type="submit"
          form={formId}
          disabled={pending?.phase === 'submitting' || form.formState.isSubmitting}
        >
          {uncertain ? '同じ要求で結果を確認' : '下書きを作成'}
        </Button>
      </DialogFrameActions>
    </Form>
  );
}
// グループの画面は会員宛（店舗を持たない）の行を扱う。会員宛の起票は業務の処理が担うので作成口を出さない。
function Workspace({
  group,
  canManage,
  canSend,
}: {
  group: boolean;
  canManage: boolean;
  canSend: boolean;
}) {
  const api = group ? memberNotificationApi : notificationApi;
  const list = useListPage(page => api.list(page));
  const [selected, setSelected] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  return (
    <>
      <ListPage
        title="業務通知"
        description={
          group ? '会員宛の業務メールと送信履歴' : '受注・ゲスト申請の業務メールと送信履歴'
        }
        state={list}
        emptyMessage="業務通知はありません"
        errorMessage="通知一覧を取得できませんでした"
        onRetry={() => void list.reload()}
        actions={
          <>
            <Button variant="outline" onClick={() => void list.reload()}>
              再読み込み
            </Button>
            {canManage && !group && <Button onClick={() => setCreating(true)}>通知を作成</Button>}
          </>
        }
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>起点</TableHead>
              <TableHead>送信予定</TableHead>
              <TableHead>状態</TableHead>
              <TableHead>試行</TableHead>
              <TableHead>操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {list.rows.map(row => (
              <TableRow key={row.id}>
                <TableCell>
                  {sourceLabels[row.source_type]}
                  <br />
                  {row.source_id}
                </TableCell>
                <TableCell>{time(row.scheduled_at)}</TableCell>
                <TableCell>
                  <Status status={row.status} />
                </TableCell>
                <TableCell>{row.attempt_count}</TableCell>
                <TableCell>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => {
                      setSelected(row.id);
                      setOpen(true);
                    }}
                  >
                    内容・履歴
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>
      <Dialog open={creating} onOpenChange={setCreating}>
        <DialogFrame
          size="sm"
          title="業務通知を作成"
          description="下書きの作成では送信されません。内容は作成後に変更できません。"
        >
          <Create
            onCreated={row => {
              setCreating(false);
              setSelected(row.id);
              setOpen(true);
              void list.reload();
            }}
          />
        </DialogFrame>
      </Dialog>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogFrame
          size="md"
          title="通知の内容・送信履歴"
          description="現在の連絡可否と送信状態を確認してください。"
        >
          {selected && (
            <Detail
              api={api}
              key={selected}
              id={selected}
              canSend={canSend}
              onChanged={list.reload}
              onMissing={() => {
                setOpen(false);
                void list.reload();
              }}
            />
          )}
        </DialogFrame>
      </Dialog>
    </>
  );
}
function NotificationDeliveriesPage({ group }: { group: boolean }) {
  const storeId = useStoreContext().currentStoreId;
  const { me } = useMe();
  if (me === null) return <MePending />;
  const permissions = group
    ? ({
        view: 'MEMBER_NOTIFICATION_VIEW',
        manage: 'MEMBER_NOTIFICATION_MANAGE',
        send: 'MEMBER_NOTIFICATION_SEND',
      } as const)
    : ({
        view: 'NOTIFICATION_VIEW',
        manage: 'NOTIFICATION_MANAGE',
        send: 'NOTIFICATION_SEND',
      } as const);
  if (me.user_type !== 'STAFF' || !hasPermission(me, permissions.view))
    return <p role="alert">業務通知の閲覧権限がありません</p>;
  const manage = hasPermission(me, permissions.manage),
    send = hasPermission(me, permissions.send);
  return (
    <Workspace
      key={`${group ? 'group' : storeId}:${manage}:${send}`}
      group={group}
      canManage={manage}
      canSend={send}
    />
  );
}
export function StoreNotificationDeliveriesPage() {
  return <NotificationDeliveriesPage group={false} />;
}
export function GroupNotificationDeliveriesPage() {
  return <NotificationDeliveriesPage group />;
}
