import { appOrigin } from '../app-domain';

describe('appOrigin', () => {
  it('既定のポートではポートを付けない', () => {
    expect(appOrigin('app.example', { protocol: 'https:', port: '' })).toBe('https://app.example');
  });

  it('既定以外のポートで配信されているときは同じポートを引き継ぐ', () => {
    // ポートを落とすと、同じホスト名の別のスタック（80 番）へ出てしまう
    expect(appOrigin('app.example', { protocol: 'http:', port: '18081' })).toBe(
      'http://app.example:18081'
    );
  });

  it('省略すると今のページのプロトコルとポートを使う', () => {
    expect(appOrigin('app.example')).toBe('http://app.example');
  });
});
