import apiClient from '../client';
import { fetchBlob } from '../blob';

jest.mock('../client', () => ({ __esModule: true, default: { get: jest.fn() } }));
const get = jest.mocked(apiClient.get);
afterEach(() => jest.clearAllMocks());

const request = { types: ['text/csv'], fallback: '取得できませんでした' };
function jsonBlob(body: string) {
  const blob = new Blob([], { type: 'application/json' });
  const text = jest.fn(async () => body);
  Object.defineProperty(blob, 'text', { value: text });
  return { blob, text };
}

it('期待した媒体型の Blob を返し、条件・中止信号・時間制限をそのまま渡す', async () => {
  const csv = new Blob(['a'], { type: 'text/csv;charset=UTF-8' });
  const signal = new AbortController().signal;
  get.mockResolvedValue({ data: csv });
  expect(
    await fetchBlob('/x/exports', { ...request, params: { month: '2026-09' }, signal, timeout: 5 })
  ).toBe(csv);
  expect(get).toHaveBeenCalledWith('/x/exports', {
    params: { month: '2026-09' },
    responseType: 'blob',
    signal,
    timeout: 5,
  });
});

it('期待外の媒体型は保存させず既定文言で失敗する', async () => {
  get.mockResolvedValue({ data: new Blob(['<html>'], { type: 'text/html' }) });
  await expect(fetchBlob('/x', request)).rejects.toThrow('取得できませんでした');
});

it('JSON の失敗本文からサーバの文言を取り出す', async () => {
  const { blob } = jsonBlob('{"error":"対象が大きすぎます"}');
  get.mockRejectedValue({ response: { data: blob, status: 422 } });
  await expect(fetchBlob('/x', request)).rejects.toThrow(new Error('対象が大きすぎます'));
});

it.each(['{', 'null', '{"error":42}'])('解読できない JSON 本文 %s は既定文言にする', async body => {
  get.mockRejectedValue({ response: { data: jsonBlob(body).blob } });
  await expect(fetchBlob('/x', request)).rejects.toThrow(new Error('取得できませんでした'));
});

it.each([undefined, new Blob(['Bad gateway'], { type: 'text/html' })])(
  '通信失敗と JSON でない代理応答は既定文言にする',
  async data => {
    get.mockRejectedValue({ response: { data } });
    await expect(fetchBlob('/x', request)).rejects.toThrow(new Error('取得できませんでした'));
  }
);

it('中止された要求は本文を読まずに元の失敗を投げる', async () => {
  const controller = new AbortController();
  controller.abort();
  const { blob, text } = jsonBlob('{"error":"x"}');
  const failure = { response: { data: blob } };
  get.mockRejectedValue(failure);
  await expect(fetchBlob('/x', { ...request, signal: controller.signal })).rejects.toBe(failure);
  expect(text).not.toHaveBeenCalled();
});
