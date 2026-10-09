'use client';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import {
  fetchReport,
  validReportPeriod,
  type ReportAdvertising,
  type ReportCriteria,
  type ReportRemuneration,
} from '@/entities/operational-report';
import { ReportExport } from '@/features/operational-report-export';
import { fromSpringPage } from '@/shared/api';
import { hasPermission, readTokenClaims, useKeyedResource } from '@/shared/lib';
import {
  Button,
  Checkbox,
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
  include_remuneration: boolean;
  include_advertising: boolean;
}
export function useOperationalReportPage(scope: 'store' | 'platform') {
  const [access, setAccess] = useState({
    ready: false,
    view: false,
    output: false,
    remuneration: false,
    advertising: false,
    advertisingOutput: false,
  });
  useEffect(() => {
    const claims = readTokenClaims();
    setAccess({
      ready: true,
      view:
        hasPermission(claims, 'OPERATIONAL_REPORT_VIEW') &&
        hasPermission(claims, scope === 'store' ? 'ORDER_MANAGE' : 'ORDER_SET_MANAGE'),
      output: hasPermission(claims, 'OPERATIONAL_REPORT_EXPORT'),
      remuneration: hasPermission(claims, 'REMUNERATION_VIEW'),
      advertising: hasPermission(
        claims,
        scope === 'store' ? 'ADVERTISING_COST_VIEW' : 'ADVERTISING_COST_SET_VIEW'
      ),
      advertisingOutput: hasPermission(
        claims,
        scope === 'store' ? 'ADVERTISING_COST_EXPORT' : 'ADVERTISING_COST_SET_EXPORT'
      ),
    });
  }, [scope]);
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tokyo' }).format(new Date());
  const form = useForm<Fields>({
    defaultValues: {
      from: `${today.slice(0, 7)}-01`,
      to: today,
      group_by: 'day',
      store: '',
      include_remuneration: false,
      include_advertising: false,
    },
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
        ...(access.remuneration && values.include_remuneration
          ? { include_remuneration: true }
          : {}),
        ...(access.advertising && values.include_advertising ? { include_advertising: true } : {}),
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
        {access.remuneration && (
          <FormField
            control={form.control}
            name="include_remuneration"
            render={({ field }) => (
              <FormItem className="mt-7 flex items-center gap-2">
                <FormControl>
                  <Checkbox
                    ref={field.ref}
                    name={field.name}
                    checked={field.value}
                    onCheckedChange={field.onChange}
                    onBlur={field.onBlur}
                  />
                </FormControl>
                <FormLabel>保証不足分・ボーナスを含める</FormLabel>
              </FormItem>
            )}
          />
        )}
        {access.advertising && (
          <FormField
            control={form.control}
            name="include_advertising"
            render={({ field }) => (
              <FormItem className="mt-7 flex items-center gap-2">
                <FormControl>
                  <Checkbox
                    ref={field.ref}
                    name={field.name}
                    checked={field.value}
                    onCheckedChange={field.onChange}
                    onBlur={field.onBlur}
                  />
                </FormControl>
                <FormLabel>広告費を含める</FormLabel>
              </FormItem>
            )}
          />
        )}
        <Button type="submit" className="mt-7">
          照会
        </Button>
      </div>
      <p className="text-sm text-muted-foreground">
        営業日は保存済みの帰属日です。実収・未収・実際の給与支払は含みません。
        {access.remuneration
          ? '保証不足分・ボーナスは選択して照会した場合に含まれます。'
          : '保証不足分・ボーナスは含みません。'}
        {access.advertising
          ? '広告費は選択時に、月初から月末までの期間を月別・店舗別で照会した場合に含まれます。日割りは行いません。'
          : '広告費は含みません。'}
      </p>
      {result && (
        <section aria-label="集計結果" className="space-y-3">
          <p className="text-sm">
            対象: {result.from} 〜 {result.to} / 生成: {result.generated_at}
          </p>
          {access.remuneration && (
            <p className="text-sm">
              保証不足分・ボーナス: {result.remuneration ? '含む' : '含まない'}
            </p>
          )}
          {access.advertising && (
            <p className="text-sm">広告費: {result.advertising ? '選択済み' : '含まない'}</p>
          )}
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
              [
                result.remuneration ? '受注報酬（円）' : '発生済み固定報酬（円）',
                result.total_remuneration,
              ],
              ...(result.remuneration
                ? [
                    ['保証不足分（円）', result.remuneration.guarantee_total],
                    ['ボーナス（円）', result.remuneration.bonus_total],
                    ['報酬合計（円）', result.remuneration.total],
                  ]
                : []),
            ].map(([label, value]) => (
              <div key={label}>
                <p className="text-sm text-muted-foreground">{label}</p>
                <p aria-label={String(label)} className="text-3xl font-bold">
                  {value === null ? '未確定' : Number(value).toLocaleString('ja-JP')}
                </p>
              </div>
            ))}
          </div>
          {result.remuneration && <RemunerationStatus remuneration={result.remuneration} />}
          {result.advertising && (
            <section aria-label="広告費集計" className="space-y-3">
              <div className="flex flex-wrap gap-6">
                {advertisingAmounts(result.advertising).map(([label, value]) => (
                  <div key={label}>
                    <p className="text-sm text-muted-foreground">{label}</p>
                    <p aria-label={label} className="min-h-9 text-3xl font-bold">
                      {formatAdvertisingAmount(value)}
                    </p>
                  </div>
                ))}
              </div>
              <AdvertisingStatus advertising={result.advertising} />
              <p className="text-sm text-muted-foreground">
                広告費は現在の有効な登録額です。登録なしは実費が零、または入力完了を意味しません。
                請求額・報酬・広告費を差し引いた利益や実際の支払額は算出しません。
              </p>
            </section>
          )}
          {access.output &&
            query &&
            (!query.criteria.include_remuneration || access.remuneration) &&
            (!query.criteria.include_advertising ||
              (access.advertising && access.advertisingOutput)) && (
              <ReportExport scope={scope} criteria={query.criteria} />
            )}
          {access.output && query?.criteria.include_advertising && !access.advertisingOutput && (
            <p className="text-sm">広告費を含む帳票の出力には広告費の出力権限が必要です。</p>
          )}
        </section>
      )}
    </div>
  );
}

export function ReportTable({ model }: { model: ReportModel }) {
  const { paging, result } = model;
  return (
    <Table>
      <TableHeader>
        <TableRow>
          {[
            '店舗',
            '期間',
            '有効完了',
            '無効化',
            '請求額（円）',
            result?.remuneration ? '受注報酬（円）' : '固定報酬（円）',
          ].map(label => (
            <TableHead key={label}>{label}</TableHead>
          ))}
          {result?.remuneration &&
            ['保証不足分（円）', 'ボーナス（円）', '報酬合計（円）', '確認状況'].map(label => (
              <TableHead key={label}>{label}</TableHead>
            ))}
          {result?.advertising &&
            [
              '営業広告登録額（円）',
              '採用広告登録額（円）',
              '広告登録額合計（円）',
              '広告費状態',
            ].map(label => <TableHead key={label}>{label}</TableHead>)}
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
            {row.remuneration && (
              <>
                <TableCell>{formatAmount(row.remuneration.guarantee_total)}</TableCell>
                <TableCell>{formatAmount(row.remuneration.bonus_total)}</TableCell>
                <TableCell>{formatAmount(row.remuneration.total)}</TableCell>
                <TableCell className="min-w-48 whitespace-normal">
                  <RemunerationStatus remuneration={row.remuneration} />
                </TableCell>
              </>
            )}
            {row.advertising && (
              <>
                {advertisingAmounts(row.advertising).map(([label, value]) => (
                  <TableCell key={label}>{formatAdvertisingAmount(value)}</TableCell>
                ))}
                <TableCell className="min-w-48 whitespace-normal">
                  <AdvertisingStatus advertising={row.advertising} />
                </TableCell>
              </>
            )}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function formatAmount(value: number | null) {
  return value === null ? '未確定' : value.toLocaleString('ja-JP');
}

function RemunerationStatus({ remuneration }: { remuneration: ReportRemuneration }) {
  return (
    <div className="text-sm">
      <p>保証不足分の既知小計: {formatAmount(remuneration.known_guarantee_total)} 円</p>
      <p>
        出勤確認待ち: {remuneration.pending_attendance_days} 人日 / 日額未設定:{' '}
        {remuneration.not_configured_days} 人日
      </p>
    </div>
  );
}

function advertisingAmounts(advertising: ReportAdvertising): [string, number | null][] {
  return [
    ['営業広告登録額（円）', advertising.sales_amount],
    ['採用広告登録額（円）', advertising.recruitment_amount],
    ['広告登録額合計（円）', advertising.recorded_total_amount],
  ];
}

function formatAdvertisingAmount(value: number | null) {
  return value === null ? '' : value.toLocaleString('ja-JP');
}

function AdvertisingStatus({ advertising }: { advertising: ReportAdvertising }) {
  const labels: Record<ReportAdvertising['status'], string> = {
    RECORDED: `登録あり（${advertising.entry_count?.toLocaleString('ja-JP')} 件）`,
    NO_RECORDS: '登録なし',
    NOT_APPLICABLE_PARTIAL_MONTH: '対象外: 月初から月末までの完全な月を指定してください。',
    NOT_APPLICABLE_DAY_GROUPING:
      '対象外: 広告費は日別に配分しません。月別または店舗別で照会してください。',
  };
  return <p className="text-sm">{labels[advertising.status]}</p>;
}
