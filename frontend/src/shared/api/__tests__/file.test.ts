import type { AxiosAdapter } from 'axios';
import { apiClient, fileApi } from '@/shared/api';

// apiClient の既定 Content-Type は application/json のため、上書きが無いと axios の
// transformRequest が FormData を JSON 文字列へ変換し、multipart として送られない。
// アダプタは transformRequest の後に走るので、ここで config.data の型を検証すれば
// 変換の有無をそのまま確認できる（境界付きヘッダは実 xhr アダプタでしか付かないため検証しない）。
describe('fileApi.upload', () => {
  it('multipart のまま送信する（FormData が JSON へ変換されない）', async () => {
    let capturedData: unknown;
    const original = apiClient.defaults.adapter;
    const adapter: AxiosAdapter = async config => {
      capturedData = config.data;
      return {
        data: { url: '/uploads/a.png', original_name: 'a.png', size: 1 },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      };
    };
    apiClient.defaults.adapter = adapter;

    try {
      await fileApi.upload(new File(['x'], 'a.png', { type: 'image/png' }), 'public');
    } finally {
      apiClient.defaults.adapter = original;
    }

    expect(capturedData).toBeInstanceOf(FormData);
  });
});
