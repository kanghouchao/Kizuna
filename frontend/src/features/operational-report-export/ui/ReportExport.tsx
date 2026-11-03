'use client';
import {
  downloadReport,
  type ReportCriteria,
  type ReportScope,
} from '@/entities/operational-report';
import { useBlobDownload } from '@/shared/lib';
import { Button, RegionError } from '@/shared/ui';

export function ReportExport({
  scope,
  criteria,
}: {
  scope: ReportScope;
  criteria: ReportCriteria;
}) {
  return <Export key={JSON.stringify([scope, criteria])} scope={scope} criteria={criteria} />;
}
function Export({ scope, criteria }: { scope: ReportScope; criteria: ReportCriteria }) {
  const download = useBlobDownload();
  const generate = (format: 'csv' | 'xlsx') =>
    void download.run(
      signal => downloadReport(scope, criteria, format, signal),
      `operational-report-${criteria.from}-${criteria.to}.${format}`
    );
  return (
    <section aria-label="帳票出力" className="space-y-3">
      <div className="flex gap-3">
        <Button
          type="button"
          variant="outline"
          disabled={download.busy}
          onClick={() => generate('csv')}
        >
          CSV 全件出力
        </Button>
        <Button
          type="button"
          variant="outline"
          disabled={download.busy}
          onClick={() => generate('xlsx')}
        >
          Excel 全件出力
        </Button>
      </div>
      <p className="text-sm text-muted-foreground">
        照会済み条件の全店舗・全集計行・受注明細を出力します。
        {criteria.include_remuneration &&
          '保証不足分・ボーナスと最小限の根拠明細を含み、未確定額は空欄と確認状況で示します。'}
        {criteria.include_advertising &&
          '広告費の登録額・状態と最小限の根拠明細を含み、対象外の金額は空欄で示します。登録なしは実費零や入力完了を意味しません。'}
        訂正後の再出力では最新額が反映されます。型と先頭零の保持には Excel をご利用ください。
      </p>
      {download.busy && <p role="status">帳票を生成しています...</p>}
      {download.error && (
        <RegionError message={download.error} onRetry={() => void download.retry()} />
      )}
    </section>
  );
}
