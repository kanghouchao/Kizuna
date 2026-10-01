'use client';

import { useEffect, useRef, useState } from 'react';
import { DownloadIcon, PrinterIcon } from 'lucide-react';
import { fetchMonthlyPdf, type MonthlyPdfCriteria } from '@/entities/order';
import { Button, RegionError } from '@/shared/ui';

export function MonthlyPdfActions({ criteria }: { criteria: MonthlyPdfCriteria }) {
  const identity = JSON.stringify(criteria);
  return <Actions key={identity} criteria={criteria} />;
}

function Actions({ criteria }: { criteria: MonthlyPdfCriteria }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [url, setUrl] = useState<string | null>(null);
  const request = useRef<AbortController | null>(null);
  const objectUrl = useRef<string | null>(null);
  const popup = useRef<Window | null>(null);
  const lastMode = useRef<'download' | 'print'>('download');
  useEffect(
    () => () => {
      request.current?.abort();
      popup.current?.close();
      if (objectUrl.current) URL.revokeObjectURL(objectUrl.current);
    },
    []
  );
  const cancel = () => {
    request.current?.abort();
    request.current = null;
    popup.current?.close();
    popup.current = null;
    setBusy(false);
  };
  const generate = async (mode: 'download' | 'print') => {
    cancel();
    lastMode.current = mode;
    const controller = new AbortController();
    request.current = controller;
    setBusy(true);
    setError(null);
    if (mode === 'print') {
      popup.current = window.open('', '_blank');
      if (popup.current) {
        popup.current.opener = null;
        popup.current.document.title = '月次給与明細 PDF';
        popup.current.document.body.textContent = 'PDF を生成しています...';
      }
    }
    try {
      const blob = await fetchMonthlyPdf(criteria, controller.signal);
      if (controller.signal.aborted) return;
      if (objectUrl.current) URL.revokeObjectURL(objectUrl.current);
      const nextUrl = URL.createObjectURL(blob);
      objectUrl.current = nextUrl;
      setUrl(nextUrl);
      if (mode === 'download') {
        const link = document.createElement('a');
        link.href = nextUrl;
        link.download = `monthly-remuneration-${criteria.month}.pdf`;
        link.click();
      } else if (popup.current && !popup.current.closed) {
        popup.current.location.replace(nextUrl);
        popup.current = null;
      }
    } catch (failure) {
      if (controller.signal.aborted) return;
      popup.current?.close();
      popup.current = null;
      if (objectUrl.current) URL.revokeObjectURL(objectUrl.current);
      objectUrl.current = null;
      setUrl(null);
      setError(failure instanceof Error ? failure.message : 'PDF を取得できませんでした。');
    } finally {
      if (!controller.signal.aborted) {
        setBusy(false);
        request.current = null;
      }
    }
  };
  return (
    <section aria-label="月次明細の PDF" className="space-y-3">
      <div className="flex flex-wrap gap-3">
        <Button
          type="button"
          variant="outline"
          disabled={busy}
          onClick={() => void generate('download')}
        >
          <DownloadIcon aria-hidden="true" />
          PDF ダウンロード
        </Button>
        <Button
          type="button"
          variant="outline"
          disabled={busy}
          onClick={() => void generate('print')}
        >
          <PrinterIcon aria-hidden="true" />
          PDFを開いて印刷
        </Button>
        {busy && (
          <Button type="button" variant="outline" onClick={cancel}>
            取得を中止
          </Button>
        )}
      </div>
      {busy && <p role="status">全月の PDF を生成しています...</p>}
      {error && <RegionError message={error} onRetry={() => void generate(lastMode.current)} />}
      {url && (
        <p className="text-sm">
          <a
            className="text-primary-strong underline"
            href={url}
            target="_blank"
            rel="noopener noreferrer"
          >
            生成した PDF を開く
          </a>
          {' / '}
          <a
            className="text-primary-strong underline"
            href={url}
            download={`monthly-remuneration-${criteria.month}.pdf`}
          >
            保存する
          </a>
          <span className="block text-muted-foreground">
            PDF 閲覧画面の印刷機能をご利用ください。
          </span>
        </p>
      )}
    </section>
  );
}
