'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useForm } from 'react-hook-form';
import { selfMonthlyRemunerationApi, SelfMonthlyRemunerationStore } from '@/entities/order';
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
}
interface Query {
  storeId: number;
  month: string;
  page: number;
}

export function CastMonthlyRemunerationsPage() {
  const form = useForm<Criteria>({
    defaultValues: {
      store: null,
      month: new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tokyo' })
        .format(new Date())
        .slice(0, 7),
    },
  });
  const [query, setQuery] = useState<Query | null>(null);
  const [orderId, setOrderId] = useState<string | null>(null);
  const statement = useResource(
    query ? () => selfMonthlyRemunerationApi.monthly(query.storeId, query.month, query.page) : null,
    [query]
  );
  const result = !statement.isLoading && statement.failure === null ? statement.data : null;
  const pdfResult =
    statement.failure === null &&
    statement.data?.store_id === query?.storeId &&
    statement.data?.month === query?.month
      ? statement.data
      : null;
  const paging = result
    ? fromSpringPage(result.orders)
    : { rows: [], page: 0, pageCount: 0, total: 0 };
  const submit = form.handleSubmit(values => {
    if (values.store) setQuery({ storeId: values.store.store_id, month: values.month, page: 0 });
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
            title="月次給与明細"
            description="原営業日の自然月ごとに発生済み固定報酬を確認します。"
            actions={
              <Button variant="outline" render={<Link href="/cast/remunerations" />}>
                報酬一覧
              </Button>
            }
            search={{
              onSearch: () => void submit(),
              content: (
                <div className="w-full space-y-6">
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
                              onChange={field.onChange}
                              triggerRef={field.ref}
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={form.control}
                      name="month"
                      rules={{
                        required: '対象月を入力してください',
                        validate: value =>
                          /^(?!0000)[0-9]{4}-(0[1-9]|1[0-2])$/.test(value) ||
                          '対象月は YYYY-MM 形式で入力してください',
                      }}
                      render={({ field }) => (
                        <FormItem className="w-44">
                          <FormLabel>対象月</FormLabel>
                          <FormControl>
                            <Input {...field} aria-required="true" placeholder="YYYY-MM" />
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
                        {result.store_name} / {result.month}
                      </p>
                      <p className="text-sm text-muted-foreground">月次報酬合計</p>
                      <p aria-label="月次報酬合計" className="text-3xl font-bold">
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
                ? page => setQuery(current => current && { ...current, page })
                : undefined,
            }}
            emptyMessage={
              statement.failure === 'notFound' ? (
                <RegionError
                  message="対象の店舗が見つからないか、閲覧範囲外です。"
                  fallback={{ href: '/cast/remunerations', label: '報酬一覧へ戻る' }}
                />
              ) : query ? (
                '対象月の完了受注はありません'
              ) : (
                '店舗と対象月を選択してください'
              )
            }
            errorMessage="月次給与明細を取得できませんでした。"
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
        </Form>
      )}
    </div>
  );
}
