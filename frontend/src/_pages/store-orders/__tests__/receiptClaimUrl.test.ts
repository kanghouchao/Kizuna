import { receiptClaimUrl } from '../lib/receiptClaimUrl';

describe('receiptClaimUrl', () => {
  it('平台ドメインの申領画面を指し、トークンをパスに置く', () => {
    // 会員ポータルは平台ドメイン側。店舗コンソールのドメインで組み立てると、店舗ドメインで
    // 配信された瞬間に客の QR が行き先を失う
    expect(receiptClaimUrl('raw-receipt-token')).toBe(
      'http://kizuna.test/member/receipts/raw-receipt-token'
    );
  });

  it('トークンを問い合わせ文字列に載せない', () => {
    // 所持そのものが証明になる値なので、Referer やアクセスログへ素通りする位置には置かない
    expect(receiptClaimUrl('raw-receipt-token')).not.toContain('?');
  });

  it('末尾にスラッシュを付けない', () => {
    // 付けると 308 で落とされる URL を客の手元に配ることになる
    expect(receiptClaimUrl('raw-receipt-token').endsWith('/')).toBe(false);
  });

  it('URL で意味を持つ文字を含むトークンも安全に載せる', () => {
    expect(receiptClaimUrl('a/b?c#d')).toBe('http://kizuna.test/member/receipts/a%2Fb%3Fc%23d');
  });
});
