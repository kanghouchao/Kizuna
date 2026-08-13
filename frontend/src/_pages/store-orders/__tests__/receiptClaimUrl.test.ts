import { receiptClaimUrl } from '../lib/receiptClaimUrl';

describe('receiptClaimUrl', () => {
  it('平台ドメインの申領画面を指し、トークンをフラグメントに置く', () => {
    // 会員ポータルは平台ドメイン側。店舗コンソールのドメインで組み立てると、店舗ドメインで
    // 配信された瞬間に客の QR が行き先を失う
    expect(receiptClaimUrl('raw-receipt-token')).toBe(
      'http://kizuna.test/member/receipts#raw-receipt-token'
    );
  });

  it('サーバへ送られる部分にトークンを載せない', () => {
    // パスも問い合わせ文字列もリクエストターゲットとして送られ、アクセスログへ 90 日有効の
    // 生値が残る。読み取られるたびにログへクレデンシャルを書くことになる
    const [requestTarget] = receiptClaimUrl('raw-receipt-token').split('#');

    expect(requestTarget).toBe('http://kizuna.test/member/receipts');
    expect(requestTarget).not.toContain('raw-receipt-token');
  });

  it('末尾にスラッシュを付けない', () => {
    // 付けると 308 で落とされる URL を客の手元に配ることになる
    expect(receiptClaimUrl('raw-receipt-token').split('#')[0].endsWith('/')).toBe(false);
  });

  it('URL で意味を持つ文字を含むトークンも安全に載せる', () => {
    expect(receiptClaimUrl('a/b?c#d')).toBe('http://kizuna.test/member/receipts#a%2Fb%3Fc%23d');
  });
});
