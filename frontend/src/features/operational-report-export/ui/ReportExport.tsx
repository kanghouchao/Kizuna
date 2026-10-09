'use client';
import { useEffect, useRef, useState } from 'react';
import {
  downloadReport,
  type ReportCriteria,
  type ReportScope,
} from '@/entities/operational-report';
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
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const request = useRef<AbortController | null>(null);
  const url = useRef<string | null>(null);
  const lastFormat = useRef<'csv' | 'xlsx'>('csv');
  useEffect(
    () => () => {
      request.current?.abort();
      if (url.current) URL.revokeObjectURL(url.current);
    },
    []
  );
  async function generate(format: 'csv' | 'xlsx') {
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    lastFormat.current = format;
    setBusy(true);
    setError(null);
    try {
      const blob = await downloadReport(scope, criteria, format, controller.signal);
      if (controller.signal.aborted) return;
      if (url.current) URL.revokeObjectURL(url.current);
      url.current = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url.current;
      link.download = `operational-report-${criteria.from}-${criteria.to}.${format}`;
      link.click();
    } catch (failure) {
      if (!controller.signal.aborted)
        setError(failure instanceof Error ? failure.message : '帳票を取得できませんでした。');
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }
  return (
    <section aria-label="帳票出力" className="space-y-3">
      <div className="flex gap-3">
        <Button
          type="button"
          variant="outline"
          disabled={busy}
          onClick={() => void generate('csv')}
        >
          CSV 全件出力
        </Button>
        <Button
          type="button"
          variant="outline"
          disabled={busy}
          onClick={() => void generate('xlsx')}
        >
          Excel 全件出力
        </Button>
      </div>
      <p className="text-sm text-muted-foreground">
        照会済み条件の全店舗・全集計行・受注明細を出力します。
        {criteria.include_remuneration &&
          '保証不足分・ボーナスと最小限の根拠明細を含み、未確定額は空欄と確認状況で示します。'}
        訂正後の再出力では最新額が反映されます。型と先頭零の保持には Excel をご利用ください。
      </p>
      {busy && <p role="status">帳票を生成しています...</p>}
      {error && <RegionError message={error} onRetry={() => void generate(lastFormat.current)} />}
    </section>
  );
}
