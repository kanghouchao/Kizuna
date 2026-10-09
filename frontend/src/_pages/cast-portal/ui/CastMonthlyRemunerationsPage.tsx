'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useForm } from 'react-hook-form';
import {
  selfMonthlyRemunerationApi,
  SelfMonthlyRemunerationStore,
  dailyRemunerationApi,
  validateRemunerationPeriod,
} from '@/entities/order';
import { RemunerationStatementPanel } from '@/widgets/remuneration-statement';
import { MonthlyPdfActions } from '@/features/monthly-remuneration-pdf';
import { fromSpringPage } from '@/shared/api';
import { useResource } from '@/shared/lib';
import {
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
} from '@/shared/ui';
import { ListPage } from '@/widgets/list-page';
import { RemunerationDetail } from './RemunerationDetail';
import { RemunerationStorePicker } from './RemunerationStorePicker';

interface Criteria {
  store: SelfMonthlyRemunerationStore | null;
  month: string;
  businessDate: string;
}
interface Query {
  storeId: number;
  period: string;
  mode: 'month' | 'day';
  page: number;
}

export function CastMonthlyRemunerationsPage() {
  const form = useForm<Criteria>({
    defaultValues: {
      store: null,
      businessDate: '',
      month: new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tokyo' })
        .format(new Date())
        .slice(0, 7),
    },
  });
  const [query, setQuery] = useState<Query | null>(null);
  const [orderId, setOrderId] = useState<string | null>(null);
  const [mode, setMode] = useState<'month' | 'day'>('month');
  const clearResult = () => {
    setQuery(null);
    setOrderId(null);
  };
  const statement = useResource(
    query
      ? async () => ({
          query,
          result:
            query.mode === 'day'
              ? await dailyRemunerationApi.self(query.storeId, query.period, query.page)
              : await selfMonthlyRemunerationApi.monthly(query.storeId, query.period, query.page),
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
    previous.store_id === query.storeId
      ? previous
      : null;
  const periodLabel = mode === 'day' ? '営業日' : '対象月';
  const title = mode === 'day' ? '日別給与明細' : '月次給与明細';
  const totalLabel = mode === 'day' ? '日別報酬合計' : '月次報酬合計';
  const paging = result
    ? fromSpringPage(result.orders)
    : { rows: [], page: 0, pageCount: 0, total: 0 };
  const submit = form.handleSubmit(values => {
    if (values.store)
      setQuery({
        storeId: values.store.store_id,
        mode,
        period: mode === 'day' ? values.businessDate : values.month,
        page: 0,
      });
  });
  return (
    <div className="mx-auto max-w-3xl space-y-4 p-4">
      {orderId ? (
        <>
          <h1 className="text-2xl font-bold">報酬明細</h1>
          <RemunerationDetail key={orderId} id={orderId} onBack={() => setOrderId(null)} />
        </>
      ) : (
        <Form {...form}>
          <ListPage
            title={title}
            description="原営業日の日別・月別に発生済み固定報酬を確認します。"
            actions={
              <Button variant="outline" render={<Link href="/cast/remunerations" />}>
                報酬一覧
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
                      name="store"
                      rules={{ required: '店舗を選択してください' }}
                      render={({ field }) => (
                        <FormItem className="w-full min-w-0 sm:w-72">
                          <FormLabel>店舗</FormLabel>
                          <FormControl>
                            <RemunerationStorePicker
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
                    <Button type="submit" className="sm:mt-7">
                      照会
                    </Button>
                  </div>
                  <p className="text-sm text-muted-foreground">
                    退店・再入店を含む同じ店舗の報酬をまとめます。支払済み額ではありません。
                  </p>

                  {pdfResult && (
                    <MonthlyPdfActions
                      criteria={{
                        scope: 'self',
                        storeId: pdfResult.store_id,
                        month: pdfResult.month,
                      }}
                    />
                  )}
                  {result && (
                    <section aria-label="集計結果" className="space-y-2">
                      <p className="break-words">
                        {result.store_name} /{' '}
                        {'month' in result ? result.month : result.business_date}
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
                    setOrderId(null);
                    setQuery(current => current && { ...current, page });
                  }
                : undefined,
            }}
            emptyMessage={
              statement.failure === 'notFound' ? (
                <RegionError
                  message="対象の店舗が見つからないか、閲覧範囲外です。"
                  fallback={{ href: '/cast/remunerations', label: '報酬一覧へ戻る' }}
                />
              ) : query ? (
                `${periodLabel}の完了受注はありません`
              ) : (
                `店舗と${periodLabel}を選択してください`
              )
            }
            errorMessage={`${title}を取得できませんでした。`}
            onRetry={statement.reload}
          >
            <div className="space-y-3">
              {paging.rows.map(row => (
                <Card key={row.order_id}>
                  <CardContent className="space-y-3 break-words p-4">
                    <h2 className="font-semibold">{row.business_date}</h2>
                    <p className="text-sm">受注番号: {row.order_id}</p>
                    <p>{row.service_summary}</p>
                    {row.completion_invalidated && <p>無効化済み（有効報酬 0 円）</p>}
                    <p>発生済み報酬: ¥{row.accrued_remuneration.toLocaleString('ja-JP')}</p>
                    <Button variant="outline" onClick={() => setOrderId(row.order_id)}>
                      詳細・変更履歴を確認
                    </Button>
                  </CardContent>
                </Card>
              ))}
            </div>
          </ListPage>
          {query?.mode === 'month' && (
            <RemunerationStatementPanel
              scope={{ scope: 'self', storeId: query.storeId }}
              month={query.period}
            />
          )}
        </Form>
      )}
    </div>
  );
}
