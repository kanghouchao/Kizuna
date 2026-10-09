'use client';
import { useParams } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { advertisingApi, validMonth, type Cost, type Mutation } from '../api/advertising';
import {
  getApiErrorMessage,
  hasPermission,
  readTokenClaims,
  useListPage,
  useResource,
} from '@/shared/lib';
import { fromSpringPage } from '@/shared/api';
import { notify } from '@/shared/notify';
import {
  Button,
  Input,
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  RegionError,
  Tabs,
  TabsList,
  TabsTrigger,
  TabsContent,
} from '@/shared/ui';
import { ListPage } from '@/widgets/list-page';
import {
  operationKey,
  readPending,
  requestId,
  submitOperation,
  type Pending,
} from '../model/operation';
import { CostEditor } from './CostEditor';
import { CostTable } from './CostTable';
import { CostExport } from './CostExport';
import { CostHistory } from './CostHistory';
import { CopyPreview } from './CopyPreview';
import { MediaSummaryPage } from './MediaSummaryPage';
export default function AdvertisingCostsPage() {
  const { storeId } = useParams<{ storeId: string }>();
  const [claims, setClaims] = useState<ReturnType<typeof readTokenClaims>>(null);
  const [ready, setReady] = useState(false);
  useEffect(() => {
    setClaims(readTokenClaims());
    setReady(true);
  }, [storeId]);
  if (!ready) return <p>読み込み中...</p>;
  if (!claims?.subject || !hasPermission(claims, 'ADVERTISING_COST_VIEW'))
    return <p role="alert">広告費を閲覧する権限がありません。</p>;
  return (
    <StorePage
      key={JSON.stringify([claims.subject, storeId])}
      store={storeId}
      subject={claims.subject}
      manage={hasPermission(claims, 'ADVERTISING_COST_MANAGE')}
      canExport={hasPermission(claims, 'ADVERTISING_COST_EXPORT')}
    />
  );
}
function StorePage({
  store,
  subject,
  manage,
  canExport,
}: {
  store: string;
  subject: string;
  manage: boolean;
  canExport: boolean;
}) {
  const [month, setMonth] = useState(() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  });
  const [input, setInput] = useState(month);
  const [view, setView] = useState('costs');
  const [error, setError] = useState(false);
  return (
    <div className="space-y-6">
      <form
        className="flex items-end gap-3"
        noValidate
        onSubmit={e => {
          e.preventDefault();
          if (!validMonth(input)) {
            setError(true);
            return;
          }
          setError(false);
          setMonth(input);
        }}
      >
        <div className="space-y-2">
          <label htmlFor="advertising-month">対象月</label>
          <Input
            id="advertising-month"
            type="month"
            value={input}
            required
            aria-invalid={error}
            aria-describedby={error ? 'advertising-month-error' : undefined}
            onChange={e => setInput(e.target.value)}
          />
          {error && (
            <p id="advertising-month-error" className="text-destructive-strong">
              年月を入力してください。
            </p>
          )}
        </div>
        <Button type="submit">表示</Button>
      </form>
      <Tabs value={view} onValueChange={value => setView(String(value))}>
        <TabsList aria-label="広告費の表示">
          <TabsTrigger value="costs">費用記録</TabsTrigger>
          <TabsTrigger value="media">媒体別集計</TabsTrigger>
        </TabsList>
        <TabsContent value="costs">
          {view === 'costs' && (
            <MonthPage key={month} {...{ store, subject, manage, canExport, month }} />
          )}
        </TabsContent>
        <TabsContent value="media">
          {view === 'media' && <MediaSummaryPage key={month} {...{ store, month, canExport }} />}
        </TabsContent>
      </Tabs>
    </div>
  );
}
type Target = { kind: 'create' } | { kind: 'replace' | 'delete'; cost: Cost } | { kind: 'copy' };
function MonthPage({
  store,
  subject,
  manage,
  canExport,
  month,
}: {
  store: string;
  subject: string;
  manage: boolean;
  canExport: boolean;
  month: string;
}) {
  const key = operationKey(subject, store, month);
  const [initial] = useState(() => {
    try {
      return { pending: readPending(key), error: false };
    } catch {
      return { pending: null, error: true };
    }
  });
  const [pending, setPending] = useState<Pending | null>(initial.pending);
  const [storageError, setStorageError] = useState(initial.error);
  const [target, setTarget] = useState<Target | null>(null),
    [open, setOpen] = useState(false),
    [missing, setMissing] = useState(false),
    [history, setHistory] = useState(false),
    [revision, setRevision] = useState(0),
    [editorKey, setEditorKey] = useState(0);
  const live = useRef(true);
  useEffect(() => {
    live.current = true;
    return () => {
      live.current = false;
    };
  }, []);
  const list = useListPage<Cost, void>(
    async page => fromSpringPage(await advertisingApi.list(month, page)),
    undefined
  );
  const totals = useResource(() => advertisingApi.month(month), [month, revision]);
  function refresh() {
    list.reload();
    setRevision(v => v + 1);
  }
  const reload = useRef(refresh);
  useEffect(() => {
    reload.current = refresh;
  });
  useEffect(() => {
    const sync = (event: Event) => {
      try {
        setPending(readPending(key));
        const result = (event as CustomEvent<{ key: string; completed: boolean }>).detail;
        if (result?.key === key && result.completed) reload.current();
      } catch {
        setStorageError(true);
      }
    };
    window.addEventListener('advertising-operation', sync);
    return () => window.removeEventListener('advertising-operation', sync);
  }, [key]);
  function edit(t: Target) {
    setMissing(false);
    setTarget(t);
    setEditorKey(v => v + 1);
    setOpen(true);
  }
  async function execute(mutation: Mutation) {
    if (pending || storageError) return;
    const op = { subject, store, month, request_id: requestId(), mutation };
    try {
      const done = await submitOperation(op, false, p => {
        if (live.current) setPending(p);
      });
      if (done && live.current) {
        setOpen(false);
        notify.success('広告費を保存しました。');
      }
    } catch (e) {
      if (live.current) {
        if ((e as { response?: { status?: number } }).response?.status === 404) setMissing(true);
        notify.error(message(e));
        refresh();
      }
    }
  }
  async function replay() {
    if (!pending || pending.phase !== 'unknown') return;
    try {
      const done = await submitOperation(pending.operation, true, p => {
        if (live.current) setPending(p);
      });
      if (done && live.current) {
        setOpen(false);
        notify.success('操作結果を確認しました。');
      }
    } catch (e) {
      if (live.current) notify.error(message(e));
    }
  }
  const blocked = !!pending || storageError;
  return (
    <div className="space-y-6">
      {storageError && (
        <p role="alert" className="text-destructive-strong">
          保存した操作を読み取れません。ブラウザの保存設定を確認してください。安全のため新しい変更を停止しています。
        </p>
      )}
      {pending && (
        <section role="status" className="rounded-lg border p-4 space-y-3">
          <p>
            {pending.phase === 'submitting'
              ? '送信中です。画面を閉じてもサーバの処理は取り消されません。'
              : '操作結果をまだ確認できません。同じ内容で結果を確認してから、次の操作を行ってください。'}
          </p>
          <p>
            対象：{month} ／{' '}
            {pending.operation.mutation.kind === 'copy'
              ? '前月コピー'
              : pending.operation.mutation.kind === 'delete'
                ? '削除'
                : pending.operation.mutation.values.media_name}
          </p>
          {pending.phase === 'unknown' && (
            <Button disabled={!manage} onClick={() => void replay()}>
              同じ要求で結果を確認
            </Button>
          )}
        </section>
      )}
      <ListPage
        title="広告費管理"
        description={`${month} の登録済み費用。問い合わせ人数は手入力の記録です。月次ロックは行いません。`}
        actions={
          <>
            {manage && (
              <>
                <Button variant="outline" disabled={blocked} onClick={() => edit({ kind: 'copy' })}>
                  前月からコピー
                </Button>
                <Button disabled={blocked} onClick={() => edit({ kind: 'create' })}>
                  広告費を登録
                </Button>
              </>
            )}
            <Button
              variant="outline"
              onClick={() => {
                setHistory(v => !v);
              }}
            >
              変更履歴
            </Button>
          </>
        }
        state={{ ...list, onPageChange: list.onPageChange }}
        emptyMessage="この月の広告費は登録されていません"
        errorMessage="広告費一覧を取得できませんでした。"
        onRetry={list.reload}
      >
        <CostTable
          rows={list.rows}
          disabled={blocked}
          onEdit={manage ? c => edit({ kind: 'replace', cost: c }) : undefined}
          onDelete={manage ? c => edit({ kind: 'delete', cost: c }) : undefined}
        />
      </ListPage>
      {totals.isLoading ? (
        <p>月合計を読み込み中...</p>
      ) : totals.failure ? (
        <RegionError
          message="月合計を取得できませんでした。"
          onRetry={() => void totals.reload()}
        />
      ) : (
        totals.data && (
          <section aria-label="月合計" className="rounded-lg border p-4 space-y-2">
            <div className="flex flex-wrap gap-8">
              <p>
                営業広告：<strong>{totals.data.sales_amount.toLocaleString()}円</strong>
              </p>
              <p>
                採用広告：<strong>{totals.data.recruitment_amount.toLocaleString()}円</strong>
              </p>
              <p>
                登録額合計：
                <strong aria-label="登録額合計">
                  {totals.data.recorded_total_amount.toLocaleString()}円
                </strong>
              </p>
            </div>
            <p className="text-sm text-muted-foreground">
              {totals.data.entry_count}行。登録なしは実際の費用がないことを意味しません。
            </p>
          </section>
        )
      )}
      {canExport && <CostExport store={store} month={month} />}
      {history && <CostHistory key={revision} month={month} />}
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-4xl max-h-[85vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {target?.kind === 'copy'
                ? '前月コピーの確認'
                : target?.kind === 'delete'
                  ? '広告費の削除'
                  : target?.kind === 'replace'
                    ? '広告費の編集'
                    : '広告費の登録'}
            </DialogTitle>
            <DialogDescription>
              {month}の広告費を操作します。変更内容は履歴に残ります。
            </DialogDescription>
          </DialogHeader>
          {missing ? (
            <p role="alert">広告費が見つかりません。一覧を更新しました。閉じて確認してください。</p>
          ) : target?.kind === 'copy' ? (
            <CopyPreview key={editorKey} month={month} disabled={blocked} onSubmit={execute} />
          ) : (
            target && (
              <CostEditor
                key={editorKey}
                cost={'cost' in target ? target.cost : undefined}
                remove={target.kind === 'delete'}
                disabled={blocked}
                onSubmit={execute}
              />
            )
          )}
          <Button variant="outline" onClick={() => setOpen(false)}>
            閉じる
          </Button>
        </DialogContent>
      </Dialog>
    </div>
  );
}
function message(e: unknown) {
  return getApiErrorMessage(e, '保存できませんでした。通信状態と対象店舗を確認してください。');
}
