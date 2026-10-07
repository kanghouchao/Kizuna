'use client';

import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { useParams } from 'next/navigation';
import { useForm } from 'react-hook-form';
import {
  Delivery,
  DeliveryInput,
  DeliveryStatus,
  notificationApi,
} from '@/entities/notification-delivery';
import {
  getApiErrorMessage,
  isBadRequest,
  isForbidden,
  isNotFound,
  isConflict,
  hasPermission,
  readTokenClaims,
  useCursorList,
  useResource,
} from '@/shared/lib';
import { notify } from '@/shared/notify';
import {
  Badge,
  Button,
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  Textarea,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
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
};
function Status({ status }: { status: DeliveryStatus }) {
  return (
    <Badge
      variant="outline"
      className={`border-transparent ${status === 'SENT' ? 'bg-success/10 text-success-strong' : ['FAILED', 'BLOCKED', 'UNKNOWN'].includes(status) ? 'bg-warning/10 text-warning-strong' : 'bg-muted text-foreground'}`}
    >
      {statuses[status]}
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

function History({ id }: { id: string }) {
  const history = useCursorList(cursor => notificationApi.history(id, cursor));
  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold">送信試行</h2>
      {history.failed ? (
        <RegionError message="送信履歴を取得できませんでした" onRetry={history.reload} />
      ) : history.isLoading ? (
        <p>読み込み中...</p>
      ) : history.rows.length === 0 ? (
        <p>送信試行はありません</p>
      ) : (
        <ol className="space-y-3">
          {history.rows.map(row => (
            <li key={row.id} className="rounded-lg border p-4 space-y-2">
              <p>
                第{row.attempt_number}回 <Status status={row.status} />
              </p>
              <p className="text-sm">
                {time(row.started_at)} / 実行 #{row.task_execution_id}
              </p>
              <p className="whitespace-pre-wrap wrap-anywhere">{row.reason}</p>
              {row.failure_code && (
                <p>{failureLabels[row.failure_code] ?? '送信を完了できませんでした'}</p>
              )}
            </li>
          ))}
        </ol>
      )}
      {history.hasMore && !history.failed && (
        <Button variant="outline" disabled={history.isLoading} onClick={history.loadMore}>
          履歴をさらに表示
        </Button>
      )}
    </section>
  );
}
function Detail({
  id,
  canSend,
  onChanged,
  onMissing,
}: {
  id: string;
  canSend: boolean;
  onChanged: () => void;
  onMissing: () => void;
}) {
  const detail = useResource(() => notificationApi.get(id), [id]);
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
      const result = await (row.status === 'DRAFT' ? notificationApi.queue : notificationApi.retry)(
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
      <div role="alert">
        <p>通知が見つかりません</p>
        <Button variant="outline" onClick={onMissing}>
          一覧へ戻る
        </Button>
      </div>
    );
  if (detail.failure !== null || !detail.data)
    return (
      <RegionError message="通知を取得できませんでした" onRetry={() => void detail.reload()} />
    );
  const row = detail.data;
  return (
    <div className="space-y-6">
      <dl className="grid grid-cols-[auto_1fr] gap-3 text-sm">
        <dt>状態</dt>
        <dd>
          <Status status={row.status} />
        </dd>
        <dt>起点</dt>
        <dd className="wrap-anywhere">
          {row.source_type === 'ORDER' ? '受注' : 'ゲスト申請'} #{row.source_id}
        </dd>
        <dt>送信予定</dt>
        <dd>{time(row.scheduled_at)}</dd>
        <dt>件名</dt>
        <dd className="wrap-anywhere">{row.subject}</dd>
        <dt>本文</dt>
        <dd className="whitespace-pre-wrap wrap-anywhere">{row.body}</dd>
        <dt>連絡可否</dt>
        <dd>{contactLabels[row.contact_decision]}</dd>
      </dl>
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
          送信結果が不明です。重複送信を防ぐため再試行できません。メール提供元の記録を確認してください。
        </p>
      )}
      <Button
        variant="outline"
        disabled={form.formState.isSubmitting}
        onClick={() => {
          void detail.reload();
          setHistoryKey(n => n + 1);
        }}
      >
        最新の状態を取得
      </Button>
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
      <History key={historyKey} id={id} />
    </div>
  );
}

type Draft = Omit<DeliveryInput, 'channel' | 'purpose' | 'dedupe_key'>;
function Create({
  onCreated,
  pending,
  onPending,
}: {
  onCreated: (row: Delivery) => void;
  pending: DeliveryInput | null;
  onPending: (input: DeliveryInput | null) => void;
}) {
  const form = useForm<Draft>({
    defaultValues: pending
      ? { ...pending, scheduled_at: localInputTime(pending.scheduled_at) }
      : { source_type: 'ORDER', source_id: '', subject: '', body: '', scheduled_at: '' },
  });
  const key = useRef<string | null>(pending?.dedupe_key ?? null);
  const submitted = useRef<DeliveryInput | null>(pending);
  const active = useActive();
  const [uncertain, setUncertain] = useState(pending !== null);
  const busy = useRef(false);
  const save = async (values: Draft) => {
    if (busy.current) return;
    busy.current = true;
    if (!key.current)
      key.current = Array.from(crypto.getRandomValues(new Uint8Array(16)), value =>
        value.toString(16).padStart(2, '0')
      ).join('');
    const input = submitted.current ?? {
      ...values,
      scheduled_at: new Date(values.scheduled_at).toISOString(),
      channel: 'EMAIL' as const,
      purpose: 'BUSINESS' as const,
      dedupe_key: key.current,
    };
    submitted.current = input;
    onPending(input);
    try {
      const row = await notificationApi.create(input);
      if (active.current) {
        onPending(null);
        onCreated(row);
        notify.success('通知の下書きを作成しました');
      }
    } catch (error) {
      if (active.current) {
        const rejected =
          !uncertain &&
          (isBadRequest(error) || isForbidden(error) || isNotFound(error) || isConflict(error));
        if (rejected) {
          submitted.current = null;
          key.current = null;
          onPending(null);
        }
        setUncertain(!rejected);
        notify.error(
          getApiErrorMessage(error, '作成結果を確認できませんでした。同じ要求で確認してください')
        );
      }
    } finally {
      busy.current = false;
    }
  };
  return (
    <Form {...form}>
      <form
        noValidate
        onSubmit={event => {
          if (submitted.current) {
            event.preventDefault();
            void save(submitted.current);
          } else {
            void form.handleSubmit(save)(event);
          }
        }}
        className="space-y-4"
      >
        <p className="text-sm text-muted-foreground">
          受注・ゲスト申請のIDを指定します。宛先は保存済みの許可から送信直前に解決します。
        </p>
        <fieldset disabled={uncertain || form.formState.isSubmitting} className="space-y-4">
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
              pattern: { value: /^[0-9]{1,32}$/, message: '32桁以内の数字で入力してください' },
            }}
            render={({ field }) => (
              <FormItem>
                <FormLabel>受注・ゲスト申請ID</FormLabel>
                <FormControl>
                  <Input {...field} required inputMode="numeric" />
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
        <Button type="submit" disabled={form.formState.isSubmitting}>
          {uncertain ? '同じ要求で結果を確認' : '下書きを作成'}
        </Button>
      </form>
    </Form>
  );
}
function Workspace({ canManage, canSend }: { canManage: boolean; canSend: boolean }) {
  const list = useCursorList(notificationApi.list);
  const [selected, setSelected] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [pending, setPending] = useState<DeliveryInput | null>(null);
  return (
    <>
      <ListPage
        title="業務通知"
        description="受注・ゲスト申請の業務メールと送信履歴"
        state={list}
        emptyMessage="業務通知はありません"
        errorMessage="通知一覧を取得できませんでした"
        onRetry={list.reload}
        actions={
          <>
            <Button variant="outline" onClick={list.reload}>
              再読み込み
            </Button>
            {canManage && <Button onClick={() => setCreating(true)}>通知を作成</Button>}
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
                  {row.source_type === 'ORDER' ? '受注' : 'ゲスト申請'}
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
      {list.hasMore && !list.failed && (
        <Button
          className="mt-4"
          variant="outline"
          disabled={list.isLoading}
          onClick={list.loadMore}
        >
          さらに表示
        </Button>
      )}
      <Dialog open={creating} onOpenChange={setCreating}>
        <DialogContent className="max-h-[calc(100vh-2rem)] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>業務通知を作成</DialogTitle>
            <DialogDescription>
              下書きの作成では送信されません。内容は作成後に変更できません。
            </DialogDescription>
          </DialogHeader>
          <Create
            pending={pending}
            onPending={setPending}
            onCreated={row => {
              setCreating(false);
              setSelected(row.id);
              setOpen(true);
              list.reload();
            }}
          />
        </DialogContent>
      </Dialog>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-h-[calc(100vh-2rem)] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>通知の内容・送信履歴</DialogTitle>
            <DialogDescription>現在の連絡可否と送信状態を確認してください。</DialogDescription>
          </DialogHeader>
          {selected && (
            <Detail
              key={selected}
              id={selected}
              canSend={canSend}
              onChanged={list.reload}
              onMissing={() => {
                setOpen(false);
                list.reload();
              }}
            />
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
export function NotificationDeliveriesPage() {
  const params = useParams();
  const hydrated = useSyncExternalStore(
    () => () => {},
    () => true,
    () => false
  );
  const claims = hydrated ? readTokenClaims() : null;
  if (!hydrated) return <p>読み込み中...</p>;
  if (claims?.userType !== 'STAFF' || !hasPermission(claims, 'NOTIFICATION_VIEW'))
    return <p role="alert">業務通知の閲覧権限がありません</p>;
  const manage = hasPermission(claims, 'NOTIFICATION_MANAGE'),
    send = hasPermission(claims, 'NOTIFICATION_SEND');
  return (
    <Workspace key={`${params.storeId}:${manage}:${send}`} canManage={manage} canSend={send} />
  );
}
