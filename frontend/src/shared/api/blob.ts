import type { AxiosRequestConfig } from 'axios';
import apiClient from './client';

/** 表形式の出力の形式ごとの媒体型。 */
export const spreadsheetTypes = {
  csv: 'text/csv',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
} as const;

export interface BlobRequest {
  params?: AxiosRequestConfig['params'];
  signal?: AbortSignal;
  /** 受け取ってよい媒体型の接頭辞。どれにも合わない応答は保存させず失敗にする。 */
  types: readonly string[];
  /** サーバが文言を返さなかったときに利用者へ出す一文。 */
  fallback: string;
  timeout?: number;
}

/**
 * Blob を取得する。失敗は常にサーバの `error` 文言か fallback を持つ Error になる。
 * responseType が blob のため失敗本文の JSON も Blob で届き、ここで読まないと文言が消える。
 * 中止時は本文を読まずに元の失敗を投げ、呼び出し側が中止と失敗を区別できるようにする。
 */
export async function fetchBlob(
  path: string,
  { params, signal, types, fallback, timeout }: BlobRequest
): Promise<Blob> {
  let blob: Blob;
  try {
    blob = (await apiClient.get<Blob>(path, { params, responseType: 'blob', signal, timeout }))
      .data;
  } catch (error) {
    if (signal?.aborted) throw error;
    throw new Error((await serverMessage(error)) ?? fallback);
  }
  if (!types.some(type => blob.type.startsWith(type))) throw new Error(fallback);
  return blob;
}

async function serverMessage(error: unknown): Promise<string | null> {
  const data = (error as { response?: { data?: unknown } } | null)?.response?.data;
  if (!(data instanceof Blob) || !data.type.includes('application/json')) return null;
  try {
    const message: unknown = JSON.parse(await data.text())?.error;
    return typeof message === 'string' ? message : null;
  } catch {
    return null;
  }
}
