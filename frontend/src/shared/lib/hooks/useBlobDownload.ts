'use client';

import { useEffect, useRef, useState } from 'react';

type BlobFetcher = (signal: AbortSignal) => Promise<Blob>;

/**
 * ファイル取得の中止と object URL の寿命を持つ。失敗は取得できなかった内容として呼び出し側が
 * その領域に RegionError で示す（DESIGN.md の優先 1）。新しい取得は進行中の取得を中止して置き換える。
 * object URL は次の取得か unmount まで解放しない。click 直後の解放はブラウザによって保存を取り消し、
 * 生成した PDF を開くリンクのように表示中の URL もあるため、時限ではなく置換時に解放する。
 */
export function useBlobDownload() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [url, setUrl] = useState<string | null>(null);
  const request = useRef<AbortController | null>(null);
  const objectUrl = useRef<string | null>(null);
  const last = useRef<{ fetcher: BlobFetcher; fileName?: string } | null>(null);
  const release = () => {
    if (objectUrl.current) URL.revokeObjectURL(objectUrl.current);
    objectUrl.current = null;
  };
  useEffect(
    () => () => {
      request.current?.abort();
      release();
    },
    []
  );

  /** fileName を渡すと保存させる。渡さなければ URL だけを返し、開き方は呼び出し側が決める。 */
  async function run(fetcher: BlobFetcher, fileName?: string): Promise<string | null> {
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    last.current = { fetcher, fileName };
    setBusy(true);
    setError(null);
    try {
      const blob = await fetcher(controller.signal);
      if (controller.signal.aborted) return null;
      release();
      const next = URL.createObjectURL(blob);
      objectUrl.current = next;
      setUrl(next);
      if (fileName) save(next, fileName);
      return next;
    } catch (failure) {
      if (controller.signal.aborted) return null;
      release();
      setUrl(null);
      setError(failure instanceof Error ? failure.message : 'ファイルを取得できませんでした。');
      return null;
    } finally {
      if (request.current === controller) {
        request.current = null;
        setBusy(false);
      }
    }
  }

  return {
    busy,
    error,
    /** 最後に取得できたファイルの URL。次の取得か unmount まで有効。 */
    url,
    run,
    retry: () => (last.current ? run(last.current.fetcher, last.current.fileName) : null),
    cancel: () => {
      request.current?.abort();
      request.current = null;
      setBusy(false);
    },
  };
}

function save(url: string, fileName: string) {
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
}
