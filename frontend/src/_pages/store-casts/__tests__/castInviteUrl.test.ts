import { castInviteUrl } from '../lib/castInviteUrl';

describe('castInviteUrl', () => {
  it('現在のオリジンの受諾画面を指し、トークンをフラグメントに置く', () => {
    expect(castInviteUrl('raw-invitation-token')).toBe(
      'http://localhost/platform/invite#raw-invitation-token'
    );
  });

  it('サーバへ送られる部分にトークンを載せない', () => {
    // パスも問い合わせ文字列もリクエストターゲットとして送られ、アクセスログへ 72 時間有効の
    // 生値が残る。招待が開かれるたびにログへクレデンシャルを書くことになる
    const [requestTarget] = castInviteUrl('raw-invitation-token').split('#');

    expect(requestTarget).toBe('http://localhost/platform/invite');
    expect(requestTarget).not.toContain('raw-invitation-token');
  });

  it('末尾にスラッシュを付けない', () => {
    // 付けると 308 で落とされる URL を招待相手へ配ることになる
    expect(castInviteUrl('raw-invitation-token').split('#')[0].endsWith('/')).toBe(false);
  });

  it('URL で意味を持つ文字を含むトークンも安全に載せる', () => {
    expect(castInviteUrl('a/b?c#d')).toBe('http://localhost/platform/invite#a%2Fb%3Fc%23d');
  });
});
