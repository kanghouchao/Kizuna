import { useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { advertisingApi, previousMonth, type Mutation } from '../api/advertising';
import { useResource } from '@/shared/lib';
import {
  Button,
  RegionError,
  Form,
  FormField,
  FormItem,
  FormLabel,
  FormControl,
  FormMessage,
  Textarea,
  TableCard,
} from '@/shared/ui';
import { CostTable } from './CostTable';
export function CopyPreview({
  month,
  disabled,
  onSubmit,
}: {
  month: string;
  disabled: boolean;
  onSubmit: (m: Mutation) => Promise<void>;
}) {
  const source = previousMonth(month);
  const [page, setPage] = useState(0);
  const expected = useRef<number | null>(null);
  const preview = useResource(
    source
      ? async () => {
          const before = await advertisingApi.month(source);
          const [rows, target] = await Promise.all([
            advertisingApi.list(source, page),
            advertisingApi.month(month),
          ]);
          const after = await advertisingApi.month(source);
          if (
            before.version !== after.version ||
            (expected.current !== null && expected.current !== after.version)
          )
            throw new Error('前月が更新されています。再確認してください。');
          expected.current = after.version;
          return { source: after, target, rows };
        }
      : null,
    [source, month, page]
  );
  const form = useForm<{ reason: string }>({ defaultValues: { reason: '' } });
  const data = preview.data;
  if (!source) return <p role="alert">この月には前月がありません。</p>;
  return (
    <div className="space-y-4">
      <p>
        {source} → {month}
        。区分・媒体・広告会社・プラン・金額を全件コピーします。問い合わせ人数は未計測に戻します。
      </p>
      {preview.isLoading ? (
        <p>コピー内容を読み込み中...</p>
      ) : preview.failure ? (
        <RegionError
          message="前月が更新されたか、取得に失敗しました。先頭から再確認してください。"
          onRetry={() => {
            expected.current = null;
            if (page === 0) void preview.reload();
            else setPage(0);
          }}
        />
      ) : (
        data && (
          <>
            <p>
              前月：{data.source.entry_count}行 ／{' '}
              {data.source.recorded_total_amount.toLocaleString()}円
            </p>
            <TableCard>
              <CostTable rows={data.rows.content} />
            </TableCard>
            <div className="flex items-center gap-3">
              <Button variant="outline" disabled={page === 0} onClick={() => setPage(p => p - 1)}>
                前のページ
              </Button>
              <span>
                {page + 1} / {Math.max(1, data.rows.total_pages)}
              </span>
              <Button
                variant="outline"
                disabled={page + 1 >= data.rows.total_pages}
                onClick={() => setPage(p => p + 1)}
              >
                次のページ
              </Button>
            </div>
            {data.target.entry_count > 0 ? (
              <p role="alert">対象月に広告費があります。空の月にだけコピーできます。</p>
            ) : !data.source.entry_count ? (
              <p>前月にコピーできる広告費はありません。</p>
            ) : (
              <Form {...form}>
                <form
                  noValidate
                  onSubmit={form.handleSubmit(async v => {
                    await onSubmit({
                      kind: 'copy',
                      source_version: data.source.version,
                      target_version: data.target.version,
                      reason: v.reason.trim(),
                    });
                  })}
                  className="space-y-3"
                >
                  <FormField
                    control={form.control}
                    name="reason"
                    rules={{
                      validate: v => !!v.trim() || '理由を入力してください',
                      maxLength: { value: 500, message: '500文字以内で入力してください' },
                    }}
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>コピー理由</FormLabel>
                        <FormControl>
                          <Textarea {...field} maxLength={500} required disabled={disabled} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <Button type="submit" disabled={disabled || form.formState.isSubmitting}>
                    金額を確認してコピー
                  </Button>
                </form>
              </Form>
            )}
          </>
        )
      )}
    </div>
  );
}
