import { apiClient } from '@/shared/api';
import { getApiErrorMessage } from '@/shared/lib';
import { applicantAttachmentApi } from '../attachments';

jest.mock('@/shared/api', () => ({ apiClient: { get: jest.fn() } }));
const get = jest.mocked(apiClient.get);
afterEach(() => jest.clearAllMocks());

it('ダウンロード失敗のJSON Blobから利用者向け文言とHTTP状態を保持する', async () => {
  const blob = new Blob([], { type: 'application/json' });
  Object.defineProperty(blob, 'text', {
    value: async () => '{"error":"画像処理が混み合っています"}',
  });
  get.mockRejectedValue({ response: { data: blob, status: 503 } });
  const failure = await applicantAttachmentApi.download('a', 'image').catch(error => error);
  expect(getApiErrorMessage(failure, '添付画像を取得できませんでした')).toBe(
    '画像処理が混み合っています'
  );
  expect(failure.response.status).toBe(503);
});

it.each(['{', 'null', '{"error":42}'])(
  '不正なJSONエラー本文 %s は安全な代替文言を保持する',
  async body => {
    const blob = new Blob([], { type: 'application/json' });
    Object.defineProperty(blob, 'text', { value: async () => body });
    const original = { response: { data: blob, status: 503 } };
    get.mockRejectedValue(original);
    const failure = await applicantAttachmentApi.download('a', 'image').catch(error => error);
    expect(failure).toBe(original);
    expect(getApiErrorMessage(failure, '添付画像を取得できませんでした')).toBe(
      '添付画像を取得できませんでした'
    );
  }
);

it('中止したダウンロードではエラーBlobを読み取らない', async () => {
  const controller = new AbortController();
  controller.abort();
  const blob = new Blob([], { type: 'application/json' });
  const text = jest.fn();
  Object.defineProperty(blob, 'text', { value: text });
  const failure = { response: { data: blob } };
  get.mockRejectedValue(failure);
  await expect(applicantAttachmentApi.download('a', 'image', controller.signal)).rejects.toBe(
    failure
  );
  expect(text).not.toHaveBeenCalled();
});

it('成功した画像Blobとキャンセル用signalを保持する', async () => {
  const blob = new Blob(['png'], { type: 'image/png' });
  const signal = new AbortController().signal;
  get.mockResolvedValue({ data: blob });
  expect(await applicantAttachmentApi.download('a', 'image', signal)).toBe(blob);
  expect(get).toHaveBeenCalledWith('/store/applicants/a/attachments/image/content', {
    responseType: 'blob',
    signal,
    timeout: 60_000,
  });
});
