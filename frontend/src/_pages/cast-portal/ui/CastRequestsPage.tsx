'use client';

import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
import {
  CastShiftRequestItem,
  ShiftRequestStatus,
  ShiftRequestCreateRequest,
  shiftApi,
} from '@/entities/shift';
import { useCursorList, useResource } from '@/shared/lib';
import {
  Badge,
  Button,
  Card,
  CardContent,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  RegionError,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Textarea,
} from '@/shared/ui';
import { earliestRequestableDate, toDateStr } from '../lib/week';

interface RequestFormValues {
  store_id: string;
  work_date: string;
  start_time: string;
  end_time: string;
  note: string;
}

const STATUS_LABELS: Record<ShiftRequestStatus, string> = {
  PENDING: '受付済み',
  APPROVED: '確定済み',
  DECLINED: '却下',
};

// 変更申請（type=CHANGE）は結果の意味が異なるため専用の状態文言を使う。
const CHANGE_STATUS_LABELS: Record<ShiftRequestStatus, string> = {
  PENDING: '変更申請中',
  APPROVED: '変更承認済み',
  DECLINED: '謝絶',
};

const STATUS_PILL_CLASS: Record<ShiftRequestStatus, string> = {
  PENDING: 'border-transparent bg-warning/10 text-warning-strong',
  APPROVED: 'border-transparent bg-success/10 text-success-strong',
  DECLINED: 'border-transparent bg-destructive/10 text-destructive-strong',
};

function statusLabel(item: CastShiftRequestItem): string | undefined {
  if (!item.status) return undefined;
  return item.type === 'CHANGE' ? CHANGE_STATUS_LABELS[item.status] : STATUS_LABELS[item.status];
}

/** 明日の 'yyyy-MM-dd' を返す（提出フォームの初期日付に使う）。 */
function tomorrowStr(): string {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  return toDateStr(d);
}

function defaultValues(storeId: string): RequestFormValues {
  return {
    store_id: storeId,
    work_date: tomorrowStr(),
    start_time: '18:00',
    end_time: '23:00',
    note: '',
  };
}

/** 出勤希望の提出フォームと提出履歴。所属店を跨いで全量・新しい順に表示する。 */
export function CastRequestsPage() {
  // 所属店と履歴は別々の読み口。まとめて 1 つの失敗にすると、店舗が読めなかったときに
  // 「履歴の取得に失敗しました」と名乗ることになる
  const {
    data: storesData,
    isLoading: storesLoading,
    failure: storesFailure,
    reload: reloadStores,
  } = useResource(() => shiftApi.myStores());
  const stores = storesData ?? [];
  const {
    rows: history,
    isLoading: historyLoading,
    failed: historyFailed,
    hasMore: hasMoreHistory,
    reload: reloadHistory,
    loadMore: loadMoreHistory,
  } = useCursorList(cursor => shiftApi.myShiftRequests({ cursor }));

  // 引き金に出る文言は候補一覧から引かれるので、選べる値はここ一箇所に持つ。
  const storeOptions = stores.map(s => ({
    value: String(s.store_id),
    label: s.store_name ?? '',
  }));

  const form = useForm<RequestFormValues>({ defaultValues: defaultValues('') });
  const {
    handleSubmit,
    reset,
    setValue,
    control,
    formState: { isSubmitting },
  } = form;

  // 初期店舗の流し込みは選択肢が描画され終えた次のコミットで行う。Select は
  // form 内で隠し input を併走させており、選択肢が未登録のまま値だけ変わると
  // その隠し要素が値を保持できず change を打ち返して選択を空へ巻き戻すため。
  useEffect(() => {
    const first = storesData?.[0];
    if (first) setValue('store_id', String(first.store_id));
  }, [storesData, setValue]);

  const submit = async (values: RequestFormValues) => {
    const payload: ShiftRequestCreateRequest = {
      store_id: Number(values.store_id),
      work_date: values.work_date,
      start_time: `${values.start_time}:00`,
      end_time: `${values.end_time}:00`,
      note: values.note || undefined,
    };
    try {
      await shiftApi.submitShiftRequest(payload);
      notify.success('出勤希望を提出しました');
      reset(defaultValues(values.store_id));
      reloadHistory();
    } catch {
      notify.error('出勤希望の提出に失敗しました');
    }
  };

  return (
    <div className="space-y-6 p-4">
      <div>
        <h1 className="text-lg font-bold text-foreground">希望提出</h1>
        <p className="mt-1 text-xs text-muted-foreground">出勤したい店舗・日時を提出できます。</p>
      </div>

      <Form {...form}>
        {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
            我々の文言は永久に描かれない。執行は各 rules が担う */}
        <form onSubmit={handleSubmit(submit)} noValidate>
          <Card>
            <CardContent className="space-y-4">
              <FormField
                control={control}
                name="store_id"
                rules={{ required: '店舗を選択してください' }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>店舗</FormLabel>
                    <Select
                      items={storeOptions}
                      value={field.value || null}
                      onValueChange={field.onChange}
                      required
                    >
                      <FormControl>
                        {/* handleSubmit の焦点移動は登録された ref を叩く。ref が trigger へ
                            届かないと、文言だけ出て焦点が動かない。 */}
                        <SelectTrigger className="w-full" ref={field.ref}>
                          {/* 所属店舗が無いときは選択肢自体が無く、値は空のままなので placeholder が出る。 */}
                          <SelectValue placeholder="所属店舗がありません" />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {storeOptions.map(o => (
                          <SelectItem key={o.value} value={o.value}>
                            {o.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    {/* placeholder の「所属店舗がありません」は取得中も失敗時も同じ顔をする。
                        どちらなのかを欄の傍で述べる */}
                    {storesLoading ? (
                      <p className="text-sm text-muted-foreground">読み込み中...</p>
                    ) : (
                      storesFailure !== null && (
                        <RegionError
                          message="所属店舗の取得に失敗しました"
                          onRetry={() => void reloadStores()}
                        />
                      )
                    )}
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={control}
                name="work_date"
                rules={{
                  required: '希望する日付を指定してください',
                  validate: v => v >= earliestRequestableDate() || '過去の日付は指定できません',
                }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>日付</FormLabel>
                    <FormControl>
                      <Input type="date" required {...field} />
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
                        <Input type="time" required {...field} />
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
                        <Input type="time" required {...field} />
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
              <Button
                type="submit"
                className="w-full"
                disabled={isSubmitting || stores.length === 0}
              >
                {isSubmitting ? '提出中...' : '提出する'}
              </Button>
            </CardContent>
          </Card>
        </form>
      </Form>

      <div>
        <h2 className="mb-2 text-sm font-semibold text-foreground">提出履歴</h2>
        {historyLoading ? (
          <p className="text-sm text-muted-foreground">読み込み中...</p>
        ) : historyFailed ? (
          // 読めなかった履歴を空に見せると、提出済みの希望が消えたと読める
          <RegionError message="履歴の取得に失敗しました" onRetry={() => reloadHistory()} />
        ) : history.length === 0 ? (
          <p className="text-sm text-muted-foreground">提出履歴はありません</p>
        ) : (
          <ul className="space-y-2">
            {history.map(item => (
              <li key={item.id}>
                <Card className="py-3">
                  <CardContent className="px-3">
                    <div className="flex items-center justify-between">
                      <span className="flex items-center gap-2">
                        <span className="text-sm font-medium text-foreground">
                          {item.store_name}
                        </span>
                        {item.type === 'CHANGE' && (
                          <Badge
                            variant="outline"
                            className="border-transparent bg-primary/10 text-primary-strong"
                          >
                            変更申請
                          </Badge>
                        )}
                      </span>
                      <Badge
                        variant="outline"
                        className={item.status && STATUS_PILL_CLASS[item.status]}
                      >
                        {statusLabel(item)}
                      </Badge>
                    </div>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {item.work_date} {item.start_time?.slice(0, 5)}–{item.end_time?.slice(0, 5)}
                    </p>
                    {item.note && <p className="mt-1 text-xs text-muted-foreground">{item.note}</p>}
                  </CardContent>
                </Card>
              </li>
            ))}
          </ul>
        )}

        {hasMoreHistory && (
          <div className="mt-3 flex justify-center">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => loadMoreHistory()}
              disabled={historyLoading}
            >
              さらに読み込む
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}
