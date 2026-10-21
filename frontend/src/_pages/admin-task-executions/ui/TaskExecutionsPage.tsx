'use client';

import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { taskExecutionApi, ExecutionSummary } from '@/entities/task-execution';
import { getApiErrorMessage, useCursorList, useResource } from '@/shared/lib';
import { notify } from '@/shared/notify';
import {
  Badge,
  Button,
  Card,
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
  Label,
  RegionError,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Table,
  TableBody,
  TableCard,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  Textarea,
} from '@/shared/ui';
import { PageHeader } from '@/widgets/page-header';

const statuses = {
  RUNNING: '実行中',
  SUCCEEDED: '成功',
  FAILED: '失敗',
  INTERRUPTED: '中断確認済み',
};
const statusColors = {
  RUNNING: 'bg-warning/10 text-warning-strong',
  SUCCEEDED: 'bg-success/10 text-success-strong',
  FAILED: 'bg-destructive/10 text-destructive-strong',
  INTERRUPTED: 'bg-muted text-foreground',
};
function Status({ row }: { row: ExecutionSummary }) {
  return (
    <Badge variant="outline" className={`border-transparent ${statusColors[row.status]}`}>
      {statuses[row.status]}
    </Badge>
  );
}
function time(value: string | null) {
  return value ? new Date(value).toLocaleString('ja-JP') : '—';
}

function ExecutionDetail({ id, onChanged }: { id: number; onChanged: (id: number) => void }) {
  const detail = useResource(() => taskExecutionApi.get(id), [id]);
  const form = useForm<{ reason: string }>({ defaultValues: { reason: '' } });
  const [busy, setBusy] = useState(false);
  const mutate = async (kind: 'retry' | 'interrupt', reason: string) => {
    setBusy(true);
    try {
      const result = await taskExecutionApi[kind](id, reason);
      detail.setData(result);
      form.reset();
      onChanged(result.execution.id);
      notify.success(kind === 'retry' ? '再試行の結果を記録しました' : '中断を記録しました');
    } catch (error) {
      notify.error(getApiErrorMessage(error, '操作に失敗しました'));
    } finally {
      setBusy(false);
    }
  };
  if (detail.isLoading) return <p>読み込み中...</p>;
  if (detail.failure || !detail.data)
    return (
      <RegionError
        message="実行の詳細を取得できませんでした"
        onRetry={() => void detail.reload()}
      />
    );
  const data = detail.data;
  const row = data.execution;
  return (
    <div className="space-y-6">
      <dl className="grid grid-cols-2 gap-3 text-sm">
        <dt>状態</dt>
        <dd>
          <Status row={row} />
        </dd>
        <dt>処理</dt>
        <dd className="wrap-anywhere">{row.task_name}</dd>
        <dt>実行主体</dt>
        <dd>{row.service_name}</dd>
        <dt>対象店舗</dt>
        <dd>{row.store_name ?? '全体'}</dd>
        <dt>対象期間</dt>
        <dd>
          {row.period_start} ～ {row.period_end}
        </dd>
        <dt>試行</dt>
        <dd>
          #{row.id}（{row.attempt_number} 回目）
        </dd>
        <dt>元の試行</dt>
        <dd>{data.retry_of ? `#${data.retry_of}` : '—'}</dd>
        <dt>開始 / 終了</dt>
        <dd>
          {time(row.started_at)} / {time(row.finished_at)}
        </dd>
        <dt>処理件数</dt>
        <dd>{row.processed_count ?? '—'}</dd>
        <dt>失敗区分</dt>
        <dd>
          {row.failure_code === 'AUTHORIZATION_DENIED'
            ? '実行主体の権限不足'
            : row.failure_code
              ? '処理失敗'
              : '—'}
        </dd>
        <dt>実行キー</dt>
        <dd className="wrap-anywhere">{data.logical_key}</dd>
        <dt>理由</dt>
        <dd className="whitespace-pre-wrap wrap-anywhere">{data.reason}</dd>
      </dl>
      <Button variant="outline" disabled={busy} onClick={() => void detail.reload()}>
        最新の状態を取得
      </Button>
      {row.id === id && row.status !== 'SUCCEEDED' && (
        <Form {...form}>
          <form
            noValidate
            className="space-y-6"
            onSubmit={form.handleSubmit(values =>
              mutate(row.status === 'RUNNING' ? 'interrupt' : 'retry', values.reason)
            )}
          >
            <FormField
              control={form.control}
              name="reason"
              rules={{
                required: '理由を入力してください',
                validate: value => value.trim().length > 0 || '理由を入力してください',
                maxLength: { value: 500, message: '500文字以内で入力してください' },
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>操作の理由</FormLabel>
                  <FormControl>
                    <Textarea {...field} maxLength={500} required />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            {row.status === 'RUNNING' && (
              <p className="text-sm text-muted-foreground">
                実際に動いている処理は中断できません。開始から所定の猶予が経過し、処理が動いていない場合だけ中断として記録できます。
              </p>
            )}
            <Button type="submit" variant="outline" disabled={busy}>
              {busy ? '処理中...' : row.status === 'RUNNING' ? '中断を確認して記録' : '再試行する'}
            </Button>
          </form>
        </Form>
      )}
    </div>
  );
}

function taskLabel(name: string) {
  return (
    (
      {
        SERVICE_IDENTITY_CHECK: 'サービスIDの実行確認',
        POINT_EXPIRY: '期限切れポイントの記帳',
        NOTIFICATION_DELIVER: '業務通知の送信',
      } as Record<string, string>
    )[name] ?? name
  );
}
export default function TaskExecutionsPage() {
  const list = useCursorList(cursor => taskExecutionApi.list(cursor));
  const [taskName, setTaskName] = useState('SERVICE_IDENTITY_CHECK');
  const catalog = useResource(taskExecutionApi.taskTypes);
  const taskType = catalog.data?.find(task => task.name === taskName);
  const [storeId, setStoreId] = useState('');
  const [storePage, setStorePage] = useState(0);
  const stores = useResource(
    taskType?.scope === 'STORE' ? () => taskExecutionApi.stores(storePage) : null,
    [taskType?.scope, storePage]
  );
  const [candidatePage, setCandidatePage] = useState(0);
  const candidates = useResource(
    taskType && (taskType.scope === 'PLATFORM' || storeId)
      ? () =>
          taskExecutionApi.candidates(
            candidatePage,
            taskName,
            taskType.scope === 'STORE' ? storeId : undefined
          )
      : null,
    [candidatePage, taskName, storeId, taskType?.scope]
  );
  const [selected, setSelected] = useState<number | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const form = useForm<{ service: string; date: string }>({
    defaultValues: { service: '', date: '' },
  });
  const pending = useRef<{ signature: string; key: string } | null>(null);
  const generation = useRef(0);
  useEffect(
    () => () => {
      generation.current++;
    },
    []
  );
  const submit = async (values: { service: string; date: string }) => {
    if (!taskType?.manual_allowed || (taskType.scope === 'STORE' && !storeId)) return;
    const currentGeneration = generation.current;
    const signature = JSON.stringify({ ...values, taskName, storeId });
    if (pending.current?.signature !== signature)
      pending.current = {
        signature,
        key: Array.from(crypto.getRandomValues(new Uint8Array(16)), value =>
          value.toString(16).padStart(2, '0')
        ).join(''),
      };
    try {
      const result = await taskExecutionApi.create({
        task_name: taskName,
        logical_key: pending.current.key,
        service_user_id: Number(values.service),
        store_id: taskType.scope === 'STORE' ? storeId : null,
        period_start: values.date,
        period_end: values.date,
      });
      if (generation.current !== currentGeneration) return;
      pending.current = null;
      setSelected(result.execution.id);
      setDetailOpen(true);
      list.reload();
    } catch (error) {
      if (generation.current === currentGeneration)
        notify.error(getApiErrorMessage(error, '実行に失敗しました'));
    }
  };
  return (
    <div className="space-y-6">
      <PageHeader
        title="処理の実行履歴"
        description="サービスIDによる処理結果を確認し、失敗した処理を理由付きで再試行できます。"
      />
      <Card className="p-6 space-y-6">
        <h2 className="text-lg font-medium">処理を実行</h2>
        <div className="max-w-lg space-y-2">
          <Label htmlFor="task-name">処理</Label>
          <Select
            value={taskName}
            disabled={form.formState.isSubmitting || catalog.isLoading}
            items={(catalog.data ?? []).map(task => ({
              value: task.name,
              label: taskLabel(task.name),
            }))}
            onValueChange={value => {
              if (!value) return;
              form.setValue('service', '');
              setCandidatePage(0);
              candidates.setData(null);
              pending.current = null;
              generation.current++;
              setStoreId('');
              setStorePage(0);
              stores.setData(null);
              setTaskName(value);
            }}
          >
            <SelectTrigger id="task-name" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {(catalog.data ?? []).map(task => (
                <SelectItem key={task.name} value={task.name} disabled={!task.manual_allowed}>
                  {taskLabel(task.name)}
                  {!task.manual_allowed && '（実行権限なし）'}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        {catalog.failure && (
          <RegionError
            message="処理の一覧を取得できませんでした"
            onRetry={() => void catalog.reload()}
          />
        )}
        {taskType && !taskType.manual_allowed && (
          <p role="status">この処理の手動実行権限がありません。</p>
        )}
        {taskType?.scope === 'STORE' && (
          <div className="max-w-lg space-y-3">
            <Label htmlFor="task-store">対象店舗</Label>
            {stores.failure ? (
              <RegionError
                message="店舗の候補を取得できませんでした"
                onRetry={() => void stores.reload()}
              />
            ) : stores.isLoading ? (
              <p>店舗を読み込み中...</p>
            ) : (
              <>
                <Select
                  value={storeId}
                  disabled={form.formState.isSubmitting}
                  items={(stores.data?.rows ?? []).map(store => ({
                    value: store.id,
                    label: store.name,
                  }))}
                  onValueChange={value => {
                    generation.current++;
                    setStoreId(value ?? '');
                    form.setValue('service', '');
                    setCandidatePage(0);
                    candidates.setData(null);
                    pending.current = null;
                  }}
                >
                  <SelectTrigger id="task-store">
                    <SelectValue placeholder="店舗を選択" />
                  </SelectTrigger>
                  <SelectContent>
                    {stores.data?.rows.map(store => (
                      <SelectItem key={store.id} value={store.id}>
                        {store.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {(stores.data?.pageCount ?? 0) > 1 && (
                  <div className="flex gap-3">
                    <Button
                      variant="outline"
                      disabled={storePage === 0 || form.formState.isSubmitting}
                      onClick={() => {
                        generation.current++;
                        setStorePage(p => p - 1);
                        setStoreId('');
                        form.setValue('service', '');
                        candidates.setData(null);
                        pending.current = null;
                      }}
                    >
                      前の店舗
                    </Button>
                    <Button
                      variant="outline"
                      disabled={
                        storePage + 1 >= (stores.data?.pageCount ?? 0) ||
                        form.formState.isSubmitting
                      }
                      onClick={() => {
                        generation.current++;
                        setStorePage(p => p + 1);
                        setStoreId('');
                        form.setValue('service', '');
                        candidates.setData(null);
                        pending.current = null;
                      }}
                    >
                      次の店舗
                    </Button>
                  </div>
                )}
              </>
            )}
          </div>
        )}
        <p className="text-sm text-muted-foreground">
          {taskName === 'POINT_EXPIRY'
            ? '対象日より前に期限を過ぎた未消費ポイントを記帳します。利用可能な残高は変わりません。本日以前の日付を指定してください。'
            : taskName === 'NOTIFICATION_DELIVER'
              ? '指定店舗の送信予定を過ぎた通知を最大100件、送信器へ引き渡します。対象日は実行記録の日付です。送信結果は店舗の業務通知で確認してください。'
              : '選択したサービスIDの現在の権限を確認し、実行履歴に記録します。対象日は確認記録の対象日です。'}
        </p>
        {taskType?.scope === 'STORE' && !storeId ? (
          <p>対象店舗を選択してください。</p>
        ) : catalog.isLoading || candidates.isLoading ? (
          <p>読み込み中...</p>
        ) : candidates.failure ? (
          <RegionError
            message="実行主体の候補を取得できませんでした"
            onRetry={() => void candidates.reload()}
          />
        ) : (
          <Form {...form}>
            <form
              noValidate
              className="max-w-lg space-y-6"
              onSubmit={event => void form.handleSubmit(submit)(event)}
            >
              <FormField
                control={form.control}
                name="service"
                rules={{ required: '実行主体を選択してください' }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>実行主体</FormLabel>
                    <Select
                      items={(candidates.data?.rows ?? []).map(item => ({
                        value: String(item.id),
                        label: item.display_name,
                      }))}
                      value={field.value}
                      onValueChange={value => field.onChange(value ?? '')}
                    >
                      <FormControl>
                        <SelectTrigger className="w-full" ref={field.ref}>
                          <SelectValue placeholder="サービスIDを選択" />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {candidates.data?.rows.map(item => (
                          <SelectItem key={item.id} value={String(item.id)}>
                            {item.display_name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
              {candidates.data?.total === 0 && (
                <p className="text-sm text-muted-foreground">
                  選択した処理の権限と実行権限、対象店舗の範囲を持つ、有効なサービスIDがありません。
                </p>
              )}
              {(candidates.data?.pageCount ?? 0) > 1 && (
                <div className="flex items-center gap-3">
                  <Button
                    type="button"
                    variant="outline"
                    disabled={candidatePage === 0}
                    onClick={() => {
                      form.setValue('service', '');
                      setCandidatePage(page => page - 1);
                    }}
                  >
                    前の候補
                  </Button>
                  <span>
                    {candidatePage + 1} / {candidates.data?.pageCount}
                  </span>
                  <Button
                    type="button"
                    variant="outline"
                    disabled={candidatePage + 1 >= (candidates.data?.pageCount ?? 0)}
                    onClick={() => {
                      form.setValue('service', '');
                      setCandidatePage(page => page + 1);
                    }}
                  >
                    次の候補
                  </Button>
                </div>
              )}
              <FormField
                control={form.control}
                name="date"
                rules={{ required: '対象日を入力してください' }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>対象日</FormLabel>
                    <FormControl>
                      <Input type="date" required {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <Button
                type="submit"
                disabled={
                  form.formState.isSubmitting ||
                  !candidates.data?.rows.length ||
                  !taskType?.manual_allowed ||
                  catalog.failure !== null
                }
              >
                {form.formState.isSubmitting
                  ? '実行中...'
                  : taskName === 'POINT_EXPIRY'
                    ? '期限切れポイントを記帳'
                    : taskName === 'NOTIFICATION_DELIVER'
                      ? '通知を送信器へ引き渡す'
                      : '実行確認を記録'}
              </Button>
            </form>
          </Form>
        )}
      </Card>
      <div className="flex justify-end">
        <Button variant="outline" onClick={list.reload} disabled={list.isLoading}>
          履歴を更新
        </Button>
      </div>
      <TableCard>
        {list.isLoading ? (
          <p className="p-6">読み込み中...</p>
        ) : list.failed ? (
          <RegionError message="実行履歴を取得できませんでした" onRetry={list.reload} />
        ) : list.rows.length === 0 ? (
          <p className="p-6 text-muted-foreground">実行履歴がありません</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>処理 / 実行主体</TableHead>
                <TableHead>対象期間</TableHead>
                <TableHead>開始</TableHead>
                <TableHead>状態</TableHead>
                <TableHead>操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {list.rows.map(row => (
                <TableRow key={row.id}>
                  <TableCell>
                    <span className="block max-w-64 wrap-anywhere">
                      {row.task_name === 'SERVICE_IDENTITY_CHECK'
                        ? 'サービスIDの実行確認'
                        : row.task_name === 'POINT_EXPIRY'
                          ? '期限切れポイントの記帳'
                          : row.task_name}
                    </span>
                    <span className="text-muted-foreground">{row.service_name}</span>
                  </TableCell>
                  <TableCell>
                    {row.period_start} ～ {row.period_end}
                  </TableCell>
                  <TableCell>{time(row.started_at)}</TableCell>
                  <TableCell>
                    <Status row={row} />
                  </TableCell>
                  <TableCell>
                    <Button
                      variant="ghost"
                      onClick={() => {
                        setSelected(row.id);
                        setDetailOpen(true);
                      }}
                    >
                      詳細
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        {list.hasMore && (
          <div className="p-4 text-center">
            <Button variant="outline" disabled={list.isLoading} onClick={list.loadMore}>
              さらに読み込む
            </Button>
          </div>
        )}
      </TableCard>
      <Dialog open={detailOpen} onOpenChange={setDetailOpen}>
        <DialogContent className="max-h-[calc(100vh-2rem)] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>実行の詳細</DialogTitle>
            <DialogDescription>履歴と現在の状態を確認します。</DialogDescription>
          </DialogHeader>
          {selected !== null && (
            <ExecutionDetail
              key={selected}
              id={selected}
              onChanged={id => {
                setSelected(id);
                list.reload();
              }}
            />
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
