'use client';

import { useEffect, useRef } from 'react';
import { DownloadIcon, PrinterIcon } from 'lucide-react';
import { fetchMonthlyPdf, type MonthlyPdfCriteria } from '@/entities/order';
import { useBlobDownload } from '@/shared/lib';
import { Button, RegionError } from '@/shared/ui';

export function MonthlyPdfActions({ criteria }: { criteria: MonthlyPdfCriteria }) {
  const identity = JSON.stringify(criteria);
  return <Actions key={identity} criteria={criteria} />;
}

function Actions({ criteria }: { criteria: MonthlyPdfCriteria }) {
  const download = useBlobDownload();
  const popup = useRef<Window | null>(null);
  const lastMode = useRef<'download' | 'print'>('download');
  useEffect(() => () => popup.current?.close(), []);
  const closePopup = () => {
    popup.current?.close();
    popup.current = null;
  };
  const cancel = () => {
    download.cancel();
    closePopup();
  };
  // 印刷用の窓はクリックの同期区間で開かないとポップアップとして遮断されるため、取得前に開く。
  const generate = async (mode: 'download' | 'print') => {
    closePopup();
    lastMode.current = mode;
    if (mode === 'print') {
      popup.current = window.open('', '_blank');
      if (popup.current) {
        popup.current.opener = null;
        popup.current.document.title = '月次給与明細 PDF';
        popup.current.document.body.textContent = 'PDF を生成しています...';
      }
    }
    const opened = popup.current;
    const url = await download.run(
      signal => fetchMonthlyPdf(criteria, signal),
      mode === 'download' ? fileName(criteria) : undefined
    );
    // 取消や次の取得に置き換えられた窓は、その時点で閉じられている。
    if (opened === null || opened !== popup.current) return;
    popup.current = null;
    if (url && !opened.closed) opened.location.replace(url);
    else opened.close();
  };
  return (
    <section aria-label="月次明細の PDF" className="space-y-3">
      <div className="flex flex-wrap gap-3">
        <Button
          type="button"
          variant="outline"
          disabled={download.busy}
          onClick={() => void generate('download')}
        >
          <DownloadIcon aria-hidden="true" />
          PDF ダウンロード
        </Button>
        <Button
          type="button"
          variant="outline"
          disabled={download.busy}
          onClick={() => void generate('print')}
        >
          <PrinterIcon aria-hidden="true" />
          PDFを開いて印刷
        </Button>
        {download.busy && (
          <Button type="button" variant="outline" onClick={cancel}>
            取得を中止
          </Button>
        )}
      </div>
      {download.busy && <p role="status">全月の PDF を生成しています...</p>}
      {download.error && (
        <RegionError message={download.error} onRetry={() => void generate(lastMode.current)} />
      )}
      {download.url && (
        <p className="text-sm">
          <a
            className="text-primary-strong underline"
            href={download.url}
            target="_blank"
            rel="noopener noreferrer"
          >
            生成した PDF を開く
          </a>
          {' / '}
          <a
            className="text-primary-strong underline"
            href={download.url}
            download={fileName(criteria)}
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

function fileName(criteria: MonthlyPdfCriteria) {
  return `monthly-remuneration-${criteria.month}.pdf`;
}
