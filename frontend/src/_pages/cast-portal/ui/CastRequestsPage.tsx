'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
import { ShiftRequestStatus, ShiftRequestCreateRequest, shiftApi } from '@/entities/shift';
import {
  formatDate,
  formatTimeRange,
  getApiErrorMessage,
  tokyoDate,
  useListPage,
  useResource,
} from '@/shared/lib';
import {
  Badge,
  BottomSheet,
  BottomSheetClose,
  Button,
  EmptyState,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  ListRow,
  ListRows,
  ListRowsSkeleton,
  Pager,
  RegionError,
  StatusBadge,
  type StatusWord,
  StoreChips,
  Textarea,
} from '@/shared/ui';
import { toDateStr } from '../lib/week';

interface RequestFormValues {
  work_date: string;
  start_time: string;
  end_time: string;
  note: string;
}

// 状態の語は両ポータル共通の対応表（DESIGN.md）。変更申請も通常の申請と同じ語を使う。
const STATUS_WORDS: Record<ShiftRequestStatus, StatusWord> = {
  PENDING: '申請中',
  APPROVED: '承認',
  DECLINED: '却下',
};

const FORM_ID = 'shift-request-form';

/** 明日の 'yyyy-MM-dd' を返す（提出フォームの初期日付に使う）。 */
function tomorrowStr(): string {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  return toDateStr(d);
}

function defaultValues(): RequestFormValues {
  return { work_date: tomorrowStr(), start_time: '18:00', end_time: '23:00', note: '' };
}

/**
 * 出勤希望の提出（下部シート）と提出履歴。提出先は店舗チップで選び、履歴は所属店を跨いで
 * 新しい順に出す — 履歴 API は店舗で絞れず、ページ単位の結果を手元で絞るとページ送りが合わない。
 */
export function CastRequestsPage() {
  // 所属店と履歴は別々の読み口。まとめて 1 つの失敗にすると、店舗が読めなかったときに
  // 「履歴の取得に失敗しました」と名乗ることになる
  const {
    data: storesData,
    isLoading: storesLoading,
    failure: storesFailure,
    reload: reloadStores,
  } = useResource(() => shiftApi.myStores());
  const stores = (storesData ?? []).map(s => ({
    id: String(s.store_id),
    name: s.store_name ?? '',
  }));
  const {
    rows: history,
    page: historyPage,
    pageCount: historyPageCount,
    total: historyTotal,
    isLoading: historyLoading,
    failed: historyFailed,
    reload: reloadHistory,
    onPageChange: changeHistoryPage,
  } = useListPage(page => shiftApi.myShiftRequests(page));
  const today = tokyoDate(new Date());

  // 未選択の間は先頭の店舗を提出先とする。選んだ店舗は提出後も保つ。
  const [pickedStoreId, setPickedStoreId] = useState<string | null>(null);
  const store = stores.find(s => s.id === pickedStoreId) ?? stores[0] ?? null;
  const [sheetOpen, setSheetOpen] = useState(false);

  const form = useForm<RequestFormValues>({ defaultValues: defaultValues() });
  const {
    handleSubmit,
    reset,
    control,
    formState: { isSubmitting },
  } = form;

  const submit = async (values: RequestFormValues) => {
    if (!store) return;
    const payload: ShiftRequestCreateRequest = {
      store_id: Number(store.id),
      work_date: values.work_date,
      start_time: `${values.start_time}:00`,
      end_time: `${values.end_time}:00`,
      note: values.note || undefined,
    };
    try {
      await shiftApi.submitShiftRequest(payload);
      notify.success('出勤希望を提出しました');
      reset(defaultValues());
      setSheetOpen(false);
      // 提出した希望は先頭ページの最上段に載るので、先頭ページへ戻して見せる
      void changeHistoryPage(0);
    } catch (e) {
      // 受理できる日付の下限は営業日で決まり、その境界（日付変更時刻・業務のタイムゾーン）を知るのは
      // サーバだけ。client 側で暦日から下限を組むと深夜帯や時差でサーバと食い違うため、日付の可否は
      // サーバの文言をそのまま見せる。
      notify.error(getApiErrorMessage(e, '出勤希望の提出に失敗しました'));
    }
  };

  return (
    <div className="space-y-6">
      <section className="space-y-3">
        <StoreChips stores={stores} value={store?.id ?? null} onValueChange={setPickedStoreId} />
        {storesFailure !== null ? (
          <RegionError message="所属店舗の取得に失敗しました" onRetry={() => void reloadStores()} />
        ) : (
          !storesLoading &&
          store === null && (
            <p className="text-muted-foreground">所属店舗が無いため、出勤希望を出せません</p>
          )
        )}
        <Button
          className="min-h-11 w-full"
          disabled={store === null}
          onClick={() => setSheetOpen(true)}
        >
          出勤希望を出す
        </Button>
      </section>

      <BottomSheet
        open={sheetOpen}
        onOpenChange={setSheetOpen}
        title="出勤希望を出す"
        description={store ? `${store.name} に提出します` : undefined}
        footer={
          <>
            {/* 操作帯は本文の外に固定されるので、form 属性で本文のフォームへ結ぶ */}
            <Button type="submit" form={FORM_ID} disabled={isSubmitting}>
              {isSubmitting ? '提出中...' : '提出する'}
            </Button>
            <BottomSheetClose render={<Button variant="outline" />}>やめる</BottomSheetClose>
          </>
        }
      >
        <Form {...form}>
          {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
              我々の文言は永久に描かれない。執行は各 rules が担う */}
          <form id={FORM_ID} onSubmit={handleSubmit(submit)} noValidate className="space-y-4">
            <FormField
              control={control}
              name="work_date"
              rules={{ required: '希望する日付を指定してください' }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>日付</FormLabel>
                  <FormControl>
                    <Input type="date" required className="h-11" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <div className="grid grid-cols-2 gap-4">
              <FormField
                control={control}
                name="start_time"
                rules={{ required: '開始時刻を入力してください' }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>開始</FormLabel>
                    <FormControl>
                      <Input type="time" required className="h-11" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={control}
                name="end_time"
                rules={{ required: '終了時刻を入力してください' }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>終了</FormLabel>
                    <FormControl>
                      <Input type="time" required className="h-11" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
            <FormField
              control={control}
              name="note"
              rules={{
                maxLength: { value: 500, message: '備考は500文字以内で入力してください' },
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>備考</FormLabel>
                  <FormControl>
                    <Textarea rows={3} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </form>
        </Form>
      </BottomSheet>

      <section aria-labelledby="request-history-heading" className="space-y-3">
        <h2 id="request-history-heading" className="font-semibold">
          提出履歴
        </h2>
        {historyLoading ? (
          <ListRowsSkeleton />
        ) : historyFailed ? (
          // 読めなかった履歴を空に見せると、提出済みの希望が消えたと読める
          <RegionError message="履歴の取得に失敗しました" onRetry={() => void reloadHistory()} />
        ) : history.length === 0 ? (
          <EmptyState message="まだ提出した希望はありません" />
        ) : (
          <ListRows label="提出履歴">
            {history.map(item => (
              <ListRow
                key={item.id}
                title={
                  <span className="flex flex-wrap items-center gap-2">
                    <span className="font-medium">{item.store_name}</span>
                    {item.type === 'CHANGE' && (
                      <Badge
                        variant="outline"
                        className="border-transparent bg-primary/10 text-primary-strong"
                      >
                        変更申請
                      </Badge>
                    )}
                  </span>
                }
                meta={
                  <>
                    <span className="block">
                      {item.work_date && formatDate(item.work_date, today)}
                      {item.start_time &&
                        item.end_time &&
                        formatTimeRange(item.start_time, item.end_time)}
                    </span>
                    {item.note && <span className="block">{item.note}</span>}
                  </>
                }
                trailing={item.status && <StatusBadge status={STATUS_WORDS[item.status]} />}
              />
            ))}
          </ListRows>
        )}

        {!historyFailed && (
          <Pager
            page={historyPage}
            pageCount={historyPageCount}
            total={historyTotal}
            rowCount={history.length}
            disabled={historyLoading}
            onPageChange={page => void changeHistoryPage(page)}
          />
        )}
      </section>
    </div>
  );
}
