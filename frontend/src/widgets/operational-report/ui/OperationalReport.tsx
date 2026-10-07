'use client';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { fetchReport, validReportPeriod, type ReportCriteria } from '@/entities/operational-report';
import { ReportExport } from '@/features/operational-report-export';
import { fromSpringPage } from '@/shared/api';
import { hasPermission, readTokenClaims, useKeyedResource } from '@/shared/lib';
import {
  Button,
  FormField,
  FormItem,
  FormLabel,
  FormControl,
  FormMessage,
  Input,
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
  Table,
  TableHeader,
  TableRow,
  TableHead,
  TableBody,
  TableCell,
} from '@/shared/ui';

interface Fields {
  from: string;
  to: string;
  group_by: ReportCriteria['group_by'];
  store: string;
}
export function useOperationalReportPage(scope: 'store' | 'platform') {
  const [access, setAccess] = useState({ ready: false, view: false, output: false });
  useEffect(() => {
    const claims = readTokenClaims();
    setAccess({
      ready: true,
      view:
        hasPermission(claims, 'OPERATIONAL_REPORT_VIEW') &&
        hasPermission(claims, scope === 'store' ? 'ORDER_MANAGE' : 'ORDER_SET_MANAGE'),
      output: hasPermission(claims, 'OPERATIONAL_REPORT_EXPORT'),
    });
  }, [scope]);
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tokyo' }).format(new Date());
  const form = useForm<Fields>({
    defaultValues: { from: `${today.slice(0, 7)}-01`, to: today, group_by: 'day', store: '' },
  });
  const [query, setQuery] = useState<{
    criteria: ReportCriteria;
    page: number;
    revision: number;
  } | null>(null);
  const report = useKeyedResource(
    [JSON.stringify(query)],
    query && access.view ? () => fetchReport(scope, query.criteria, query.page) : null
  );
  const result = !report.isLoading && report.failure === null ? report.data : null;
  const paging = result
    ? fromSpringPage(result.rows)
    : { rows: [], page: 0, pageCount: 0, total: 0 };
  const submit = form.handleSubmit(values =>
    setQuery(current => ({
      criteria: {
        from: values.from,
        to: values.to,
        group_by: values.group_by,
        ...(scope === 'platform' && values.store ? { store_id: Number(values.store) } : {}),
      },
      page: 0,
      revision: (current?.revision ?? 0) + 1,
    }))
  );
  return { scope, form, access, query, report, result, paging, submit, setQuery };
}

type ReportModel = ReturnType<typeof useOperationalReportPage>;

export function ReportSearch({ model }: { model: ReportModel }) {
  const { scope, form, result, query, access } = model;
  return (
    <div className="w-full space-y-6">
      <div className="flex flex-wrap items-start gap-6">
        {(['from', 'to'] as const).map(name => (
          <FormField
            key={name}
            control={form.control}
            name={name}
            rules={{
              required: '営業日を入力してください',
              validate: () =>
                validReportPeriod(form.getValues('from'), form.getValues('to')) ||
                '正しい営業日を開始から終了の順で366日以内に入力してください',
            }}
            render={({ field }) => (
              <FormItem className="w-44">
                <FormLabel>{name === 'from' ? '開始営業日' : '終了営業日'}</FormLabel>
                <FormControl>
                  <Input {...field} aria-required="true" placeholder="YYYY-MM-DD" />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        ))}
        <FormField
          control={form.control}
          name="group_by"
          render={({ field }) => (
            <FormItem className="w-36">
              <FormLabel>集計単位</FormLabel>
              <Select
                value={field.value}
                onValueChange={field.onChange}
                items={{ day: '日別', month: '月別', store: '店舗別' }}
              >
                <FormControl>
                  <SelectTrigger ref={field.ref}>
                    <SelectValue />
                  </SelectTrigger>
                </FormControl>
                <SelectContent>
                  <SelectItem value="day">日別</SelectItem>
                  <SelectItem value="month">月別</SelectItem>
                  <SelectItem value="store">店舗別</SelectItem>
                </SelectContent>
              </Select>
              <FormMessage />
            </FormItem>
          )}
        />
        {scope === 'platform' && (
          <FormField
            control={form.control}
            name="store"
            rules={{
              validate: value =>
                !value ||
                (/^[1-9][0-9]*$/.test(value) && Number.isSafeInteger(Number(value))) ||
                '正の店舗IDを入力してください',
            }}
            render={({ field }) => (
              <FormItem className="w-44">
                <FormLabel>店舗ID（空欄は授権全店）</FormLabel>
                <FormControl>
                  <Input {...field} inputMode="numeric" />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        )}
        <Button type="submit" className="mt-7">
          照会
        </Button>
      </div>
      <p className="text-sm text-muted-foreground">
        営業日は保存済みの帰属日です。実収・未収・実際の給与支払、期間保証・賞与・広告費は含みません。
      </p>
      {result && (
        <section aria-label="集計結果" className="space-y-3">
          <p className="text-sm">
            対象: {result.from} 〜 {result.to} / 生成: {result.generated_at}
          </p>
          <p className="text-sm break-words">
            対象店舗:{' '}
            {result.stores.map(store => `${store.store_name} (${store.store_id})`).join('、') ||
              'なし'}
          </p>
          <div className="flex flex-wrap gap-6">
            {[
              ['有効完了件数', result.total_order_count],
              ['無効化件数', result.invalidated_order_count],
              ['請求額（円）', result.total_fee],
              ['発生済み固定報酬（円）', result.total_remuneration],
            ].map(([label, value]) => (
              <div key={label}>
                <p className="text-sm text-muted-foreground">{label}</p>
                <p aria-label={String(label)} className="text-3xl font-bold">
                  {Number(value).toLocaleString('ja-JP')}
                </p>
              </div>
            ))}
          </div>
          {access.output && query && <ReportExport scope={scope} criteria={query.criteria} />}
        </section>
      )}
    </div>
  );
}

export function ReportTable({ model }: { model: ReportModel }) {
  const { paging } = model;
  return (
    <Table>
      <TableHeader>
        <TableRow>
          {['店舗', '期間', '有効完了', '無効化', '請求額（円）', '固定報酬（円）'].map(label => (
            <TableHead key={label}>{label}</TableHead>
          ))}
        </TableRow>
      </TableHeader>
      <TableBody>
        {paging.rows.map(row => (
          <TableRow key={`${row.store_id}-${row.period}`}>
            <TableCell className="max-w-64 whitespace-normal break-words">
              {row.store_name}
            </TableCell>
            <TableCell>{row.period || '期間全体'}</TableCell>
            <TableCell>{row.order_count}</TableCell>
            <TableCell>{row.invalidated_order_count}</TableCell>
            <TableCell>{row.total_fee.toLocaleString('ja-JP')}</TableCell>
            <TableCell>{row.total_remuneration.toLocaleString('ja-JP')}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
