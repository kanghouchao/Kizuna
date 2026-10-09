'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import {
  MonthlyRemunerationCast,
  monthlyRemunerationApi,
  dailyRemunerationApi,
  validateRemunerationPeriod,
} from '@/entities/order';
import { RemunerationStatementPanel } from '@/widgets/remuneration-statement';
import { MonthlyPdfActions } from '@/features/monthly-remuneration-pdf';
import { fromSpringPage } from '@/shared/api';
import { storePath, useResource } from '@/shared/lib';
import {
  Button,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  RegionError,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';
import { ListPage } from '@/widgets/list-page';
import { OrderCorrectionHistoryModal } from '@/widgets/order-correction-history';
import { PersonPicker } from './PersonPicker';

interface Criteria {
  person: MonthlyRemunerationCast | null;
  month: string;
  businessDate: string;
}
interface Query {
  personId: number;
  period: string;
  mode: 'month' | 'day';
  page: number;
}

export default function MonthlyRemunerationsPage() {
  const { storeId } = useParams<{ storeId: string }>();
  return <MonthlyStatement key={storeId} storeId={storeId} />;
}

function MonthlyStatement({ storeId }: { storeId: string }) {
  const form = useForm<Criteria>({
    defaultValues: {
      person: null,
      businessDate: '',
      month: new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tokyo' })
        .format(new Date())
        .slice(0, 7),
    },
  });
  const [history, setHistory] = useState<{ orderId: string; open: boolean } | null>(null);
  const [query, setQuery] = useState<Query | null>(null);
  const [mode, setMode] = useState<'month' | 'day'>('month');
  const clearResult = () => {
    setQuery(null);
    setHistory(null);
  };
  const statement = useResource(
    query
      ? async () => ({
          query,
          result:
            query.mode === 'day'
              ? await dailyRemunerationApi.store(query.personId, query.period, query.page)
              : await monthlyRemunerationApi.monthly(query.personId, query.period, query.page),
        })
      : null,
    [query]
  );
  const result =
    query && statement.data?.query === query && !statement.isLoading && statement.failure === null
      ? statement.data.result
      : null;
  const previous = statement.data?.result;
  const pdfResult =
    query?.mode === 'month' &&
    statement.failure === null &&
    previous &&
    'month' in previous &&
    previous.month === query.period &&
    previous.person_id === query.personId
      ? previous
      : null;
  const periodLabel = mode === 'day' ? '営業日' : '対象月';
  const title = mode === 'day' ? '日別給与明細' : '月次給与明細';
  const totalLabel = mode === 'day' ? '日別報酬合計' : '月次報酬合計';
  const paging = result
    ? fromSpringPage(result.orders)
    : { rows: [], page: 0, pageCount: 0, total: 0 };
  const submit = form.handleSubmit(values => {
    if (values.person)
      setQuery({
        personId: values.person.person_id,
        mode,
        period: mode === 'day' ? values.businessDate : values.month,
        page: 0,
      });
  });
  return (
    <Form {...form}>
      <ListPage
        title={title}
        description="原営業日の日別・月別に発生済み固定報酬を確認します。"
        actions={
          <Button variant="outline" render={<Link href={storePath(storeId, '/orders')} />}>
            受注一覧
          </Button>
        }
        search={{
          onSearch: () => void submit(),
          content: (
            <div className="w-full space-y-6">
              <div role="group" aria-label="集計単位" className="flex gap-3">
                {(['month', 'day'] as const).map(value => (
                  <Button
                    key={value}
                    type="button"
                    variant={mode === value ? 'default' : 'outline'}
                    aria-pressed={mode === value}
                    onClick={() => {
                      clearResult();
                      setMode(value);
                    }}
                  >
                    {value === 'day' ? '日別' : '月別'}
                  </Button>
                ))}
              </div>
              <div className="flex flex-wrap items-start gap-6">
                <FormField
                  control={form.control}
                  name="person"
                  rules={{ required: 'キャスト本人を選択してください' }}
                  render={({ field }) => (
                    <FormItem className="w-72 min-w-0">
                      <FormLabel>キャスト本人</FormLabel>
                      <FormControl>
                        <PersonPicker
                          value={field.value}
                          onChange={value => {
                            clearResult();
                            field.onChange(value);
                          }}
                          triggerRef={field.ref}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  key={mode}
                  name={mode === 'day' ? 'businessDate' : 'month'}
                  rules={{
                    required: `${periodLabel}を入力してください`,
                    validate: value => validateRemunerationPeriod(mode, value),
                  }}
                  render={({ field }) => (
                    <FormItem className="w-44">
                      <FormLabel>{periodLabel}</FormLabel>
                      <FormControl>
                        <Input
                          {...field}
                          onChange={event => {
                            clearResult();
                            field.onChange(event);
                          }}
                          aria-required="true"
                          placeholder={mode === 'day' ? 'YYYY-MM-DD' : 'YYYY-MM'}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <Button type="submit" className="mt-7">
                  照会
                </Button>
              </div>
              <p className="text-sm text-muted-foreground">
                退店・再入店を含め同じ本人の本店の報酬を集計します。支払済み額ではありません。実際の入出金・返金・給与支払いは管理対象外です。
              </p>

              {pdfResult && (
                <MonthlyPdfActions
                  criteria={{
                    scope: 'store',
                    personId: pdfResult.person_id,
                    month: pdfResult.month,
                  }}
                />
              )}
              {result && (
                <section className="space-y-2" aria-label="集計結果">
                  <p className="text-sm break-words">
                    {result.name} / {'month' in result ? result.month : result.business_date}
                  </p>
                  <p className="text-sm text-muted-foreground">{totalLabel}</p>
                  <p aria-label={totalLabel} className="text-3xl font-bold">
                    ¥{result.total_remuneration.toLocaleString('ja-JP')}
                  </p>
                  <p className="text-sm text-muted-foreground">
                    全 {result.orders.total_elements}{' '}
                    件の合計。完了・訂正・無効化後は再照会すると反映されます。
                  </p>
                </section>
              )}
            </div>
          ),
        }}
        state={{
          ...paging,
          isLoading: statement.isLoading,
          failed: statement.failure !== null && statement.failure !== 'notFound',
          onPageChange: result
            ? page => {
                setHistory(null);
                setQuery(current => current && { ...current, page });
              }
            : undefined,
        }}
        emptyMessage={
          statement.failure === 'notFound' ? (
            <RegionError
              message="キャスト本人が見つからないか、閲覧範囲外です。"
              fallback={{ href: storePath(storeId, '/orders'), label: '受注一覧へ戻る' }}
            />
          ) : query ? (
            `${periodLabel}の完了受注はありません`
          ) : (
            `キャスト本人と${periodLabel}を選択してください`
          )
        }
        errorMessage={`${title}を取得できませんでした。`}
        onRetry={statement.reload}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>営業日</TableHead>
              <TableHead>受注番号</TableHead>
              <TableHead>サービス概要</TableHead>
              <TableHead className="text-right">発生済み報酬</TableHead>
              <TableHead>履歴</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {paging.rows.map(order => (
              <TableRow key={order.order_id}>
                <TableCell className="whitespace-nowrap">{order.business_date}</TableCell>
                <TableCell>
                  <Link
                    className="text-primary-strong underline"
                    href={storePath(storeId, `/orders/${order.order_id}/edit`)}
                  >
                    {order.order_id}
                  </Link>
                </TableCell>
                <TableCell className="max-w-80 whitespace-normal break-words">
                  {order.service_summary}
                  {order.completion_invalidated && (
                    <p className="font-medium">無効化済み（有効報酬 0 円）</p>
                  )}
                </TableCell>
                <TableCell className="text-right whitespace-nowrap">
                  ¥{order.accrued_remuneration.toLocaleString('ja-JP')}
                </TableCell>
                <TableCell>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => setHistory({ orderId: order.order_id, open: true })}
                  >
                    訂正履歴
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>
      {query?.mode === 'month' && (
        <RemunerationStatementPanel
          scope={{ scope: 'store', personId: query.personId }}
          month={query.period}
        />
      )}
      {history && (
        <OrderCorrectionHistoryModal
          orderId={history.orderId}
          scope="store"
          open={history.open}
          onOpenChange={open => setHistory(current => current && { ...current, open })}
        />
      )}
    </Form>
  );
}
