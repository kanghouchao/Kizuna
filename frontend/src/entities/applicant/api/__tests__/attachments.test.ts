import { apiClient, fetchBlob } from '@/shared/api';
import { applicantAttachmentApi } from '../attachments';

jest.mock('@/shared/api', () => ({ apiClient: { get: jest.fn() }, fetchBlob: jest.fn() }));
afterEach(() => jest.clearAllMocks());

it('添付画像は画像の媒体型だけを受け取り、中止信号と時間制限を渡す', async () => {
  const signal = new AbortController().signal;
  await applicantAttachmentApi.download('a', 'image', signal);
  expect(fetchBlob).toHaveBeenCalledWith('/applicants/a/attachments/image/content', {
    signal,
    types: ['image/png', 'image/jpeg'],
    fallback: '添付画像を取得できませんでした',
    timeout: 60_000,
  });
});

test('操作結果の照会はWRITE専用資源を使い404を成功に置き換えない', async () => {
  const key = '00000000-0000-4000-8000-000000000001';
  (apiClient.get as jest.Mock).mockResolvedValueOnce({
    data: { id: 'u', idempotency_key: key, status: 'READY' },
  });
  expect(await applicantAttachmentApi.operation('a', key)).toEqual({
    id: 'u',
    idempotency_key: key,
    status: 'READY',
  });
  expect(apiClient.get).toHaveBeenCalledWith(`/applicants/a/attachment-operations/${key}`);
  const missing = { response: { status: 404 } };
  (apiClient.get as jest.Mock).mockRejectedValueOnce(missing);
  await expect(applicantAttachmentApi.operation('a', key)).rejects.toBe(missing);
});
