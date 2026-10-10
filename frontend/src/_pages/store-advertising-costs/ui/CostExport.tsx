import { useEffect, useRef, useState } from 'react';
import { advertisingApi, advertisingExports, type ExportKind } from '../api/advertising';
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
  const [busy, setBusy] = useState(false),
    [error, setError] = useState<string | null>(null);
  const pending = useRef<AbortController | null>(null);
  const last = useRef<'csv' | 'xlsx'>('csv');
  useEffect(() => () => pending.current?.abort(), []);
  async function run(format: 'csv' | 'xlsx') {
    if (pending.current && !pending.current.signal.aborted) return;
    const controller = new AbortController();
    pending.current = controller;
    last.current = format;
    setBusy(true);
    setError(null);
    try {
      const blob = await advertisingApi.download(month, format, controller.signal, kind);
      if (controller.signal.aborted) return;
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${advertisingExports[kind].resource}-${store}-${month}.${format}`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      if (!controller.signal.aborted)
        setError(e instanceof Error ? e.message : '出力できませんでした。');
    } finally {
      if (!controller.signal.aborted) {
        pending.current = null;
        setBusy(false);
      }
    }
  }
  return (
    <section aria-label={advertisingExports[kind].label} className="space-y-3">
      <div className="flex gap-3">
        <Button variant="outline" disabled={busy} onClick={() => void run('csv')}>
          CSV 全件出力
        </Button>
        <Button variant="outline" disabled={busy} onClick={() => void run('xlsx')}>
          Excel 全件出力
        </Button>
      </div>
      {error && <RegionError message={error} onRetry={() => void run(last.current)} />}
    </section>
  );
}
