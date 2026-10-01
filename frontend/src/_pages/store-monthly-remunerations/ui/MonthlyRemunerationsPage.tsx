'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { MonthlyRemunerationCast, monthlyRemunerationApi } from '@/entities/order';
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
}
interface Query {
  personId: number;
  month: string;
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
      month: new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tokyo' })
        .format(new Date())
        .slice(0, 7),
    },
  });
  const [history, setHistory] = useState<{ orderId: string; open: boolean } | null>(null);
  const [query, setQuery] = useState<Query | null>(null);
  const statement = useResource(
    query ? () => monthlyRemunerationApi.monthly(query.personId, query.month, query.page) : null,
    [query]
  );
  const result = !statement.isLoading && statement.failure === null ? statement.data : null;
  const paging = result
    ? fromSpringPage(result.orders)
    : { rows: [], page: 0, pageCount: 0, total: 0 };
  const submit = form.handleSubmit(values => {
    if (values.person)
      setQuery({ personId: values.person.person_id, month: values.month, page: 0 });
  });
  return (
    <Form {...form}>
      <ListPage
        title="月次給与明細"
        description="原営業日の自然月に属する発生済み固定報酬を確認します。"
        actions={
          <Button variant="outline" render={<Link href={storePath(storeId, '/orders')} />}>
            受注一覧
          </Button>
        }
        search={{
          onSearch: () => void submit(),
          content: (
            <div className="w-full space-y-6">
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
                <Button type="submit" className="mt-7">
                  照会
                </Button>
              </div>
              <p className="text-sm text-muted-foreground">
                退店・再入店を含め同じ本人の本店の報酬を集計します。支払済み額ではありません。実際の入出金・返金・給与支払いは管理対象外です。
              </p>
              {result && (
                <MonthlyPdfActions
                  criteria={{ scope: 'store', personId: result.person_id, month: result.month }}
                />
              )}
              {result && (
                <section className="space-y-2" aria-label="集計結果">
                  <p className="text-sm break-words">
                    {result.name} / {result.month}
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
              message="キャスト本人が見つからないか、閲覧範囲外です。"
              fallback={{ href: storePath(storeId, '/orders'), label: '受注一覧へ戻る' }}
            />
          ) : query ? (
            '対象月の完了受注はありません'
          ) : (
            'キャスト本人と対象月を選択してください'
          )
        }
        errorMessage="月次給与明細を取得できませんでした。"
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
