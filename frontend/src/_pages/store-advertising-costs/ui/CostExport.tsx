import { advertisingApi, advertisingExports, type ExportKind } from '../api/advertising';
import { useBlobDownload } from '@/shared/lib';
import { Button, RegionError } from '@/shared/ui';
export function CostExport({
  store,
  month,
  kind = 'costs',
}: {
  store: string;
  month: string;
  kind?: ExportKind;
}) {
  const download = useBlobDownload();
  const run = (format: 'csv' | 'xlsx') =>
    void download.run(
      signal => advertisingApi.download(month, format, signal, kind),
      `${advertisingExports[kind].resource}-${store}-${month}.${format}`
    );
  return (
    <section aria-label={advertisingExports[kind].label} className="space-y-3">
      <div className="flex gap-3">
        <Button variant="outline" disabled={download.busy} onClick={() => run('csv')}>
          CSV 全件出力
        </Button>
        <Button variant="outline" disabled={download.busy} onClick={() => run('xlsx')}>
          Excel 全件出力
        </Button>
      </div>
      {download.error && (
        <RegionError message={download.error} onRetry={() => void download.retry()} />
      )}
    </section>
  );
}
