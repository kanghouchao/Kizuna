'use client';
import { MePending, hasPermission, useCurrentStoreId, useMe } from '@/entities/user';
import { useEffect, useRef, useState } from 'react';
import { advertisingApi, validMonth, type Cost, type Mutation } from '../api/advertising';
import Link from 'next/link';
import { getApiErrorMessage, storePath, useListPage, useResource } from '@/shared/lib';
import { fromSpringPage, getSessionUserId } from '@/shared/api';
import { notify } from '@/shared/notify';
import {
  Button,
  Input,
  Dialog,
  DialogFrame,
  RegionError,
  Tabs,
  TabsList,
  TabsTrigger,
  TabsContent,
} from '@/shared/ui';
import { ListPage } from '@/widgets/list-page';
import { useAdvertisingOperation, useReloadAfterOperation } from '../model/operation';
import { CostEditor } from './CostEditor';
import { CostTable } from './CostTable';
import { CostExport } from './CostExport';
import { CostHistory } from './CostHistory';
import { CopyPreview } from './CopyPreview';
import { MediaSummaryPage } from './MediaSummaryPage';
import { OrderCostPage } from './OrderCostPage';
import { RecruitmentEffectPage } from './RecruitmentEffectPage';
import { DeniedPage } from '@/widgets/admin-shell';
export default function AdvertisingCostsPage() {
  const storeId = useCurrentStoreId();
  const { me } = useMe();
  if (me === null) return <MePending />;
  // 未確認の操作は操作者（このタブのトークンの利用者 ID）と店舗ごとにタブ内へ預ける。
  const userId = getSessionUserId();
  if (userId === undefined || !hasPermission(me, 'ADVERTISING_COST_VIEW')) return <DeniedPage />;
  return (
    <StorePage
      key={JSON.stringify([userId, storeId])}
      store={storeId}
      manage={hasPermission(me, 'ADVERTISING_COST_MANAGE')}
      canExport={hasPermission(me, 'ADVERTISING_COST_EXPORT')}
      canReadOrders={hasPermission(me, 'ORDER_MANAGE')}
      canReadRecruitment={hasPermission(me, 'RECRUITMENT_VIEW')}
    />
  );
}
function StorePage({
  store,
  manage,
  canExport,
  canReadOrders,
  canReadRecruitment,
}: {
  store: string;
  manage: boolean;
  canExport: boolean;
  canReadOrders: boolean;
  canReadRecruitment: boolean;
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
      <div className="flex flex-wrap items-end justify-between gap-3">
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
        <Button
          variant="outline"
          render={<Link href={storePath(store, '/advertising-costs/media')} />}
        >
          媒体を管理
        </Button>
      </div>
      <Tabs value={view} onValueChange={value => setView(String(value))}>
        <TabsList aria-label="広告費の表示">
          <TabsTrigger value="costs">費用記録</TabsTrigger>
          <TabsTrigger value="media">媒体別集計</TabsTrigger>
          {canReadOrders && <TabsTrigger value="orders">受注あたり記録広告費</TabsTrigger>}
          {canReadRecruitment && <TabsTrigger value="recruitment">採用効果</TabsTrigger>}
        </TabsList>
        <TabsContent value="costs">
          {view === 'costs' && <MonthPage key={month} {...{ store, manage, canExport, month }} />}
        </TabsContent>
        <TabsContent value="media">
          {view === 'media' && <MediaSummaryPage key={month} {...{ store, month, canExport }} />}
        </TabsContent>
        {canReadOrders && (
          <TabsContent value="orders">
            {view === 'orders' && <OrderCostPage key={month} {...{ store, month, canExport }} />}
          </TabsContent>
        )}
        {canReadRecruitment && (
          <TabsContent value="recruitment">
            {view === 'recruitment' && (
              <RecruitmentEffectPage key={month} store={store} month={month} />
            )}
          </TabsContent>
        )}
      </Tabs>
    </div>
  );
}
type Target = { kind: 'create' } | { kind: 'replace' | 'delete'; cost: Cost } | { kind: 'copy' };
function MonthPage({
  store,
  manage,
  canExport,
  month,
}: {
  store: string;
  manage: boolean;
  canExport: boolean;
  month: string;
}) {
  const operation = useAdvertisingOperation(store, month);
  const { pending, unreadable: storageError } = operation;
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
  useReloadAfterOperation(store, month, () => reload.current());
  function edit(t: Target) {
    setMissing(false);
    setTarget(t);
    setEditorKey(v => v + 1);
    setOpen(true);
  }
  async function execute(mutation: Mutation) {
    if (pending || storageError) return;
    try {
      const done = await operation.start(mutation);
      if (done && live.current) {
        setOpen(false);
        notify.success('広告費を保存しました。');
      }
    } catch (e) {
      if (live.current) {
        if ((e as { response?: { status?: number } }).response?.status === 404) setMissing(true);
        notify.error(message(e));
      }
    }
  }
  async function replay() {
    if (pending?.phase !== 'unknown') return;
    try {
      const done = await operation.replay();
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
            {pending.payload.kind === 'copy'
              ? '前月コピー'
              : pending.payload.kind === 'delete'
                ? '削除'
                : pending.payload.kind === 'replace'
                  ? '変更'
                  : '登録'}
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
        <DialogFrame
          size={target?.kind === 'copy' ? 'lg' : 'sm'}
          title={
            target?.kind === 'copy'
              ? '前月コピーの確認'
              : target?.kind === 'delete'
                ? '広告費の削除'
                : target?.kind === 'replace'
                  ? '広告費の編集'
                  : '広告費の登録'
          }
          description={`${month}の広告費を操作します。変更内容は履歴に残ります。`}
          footer={
            <Button variant="outline" onClick={() => setOpen(false)}>
              閉じる
            </Button>
          }
        >
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
        </DialogFrame>
      </Dialog>
    </div>
  );
}
function message(e: unknown) {
  return getApiErrorMessage(e, '保存できませんでした。通信状態と対象店舗を確認してください。');
}
