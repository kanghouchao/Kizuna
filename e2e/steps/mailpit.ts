import { expect, type APIRequestContext } from '@playwright/test';

// e2e スタックのメールキャッチャー。backend の既定の送信経路がここへ届く（docker-compose.e2e.yml）。
const MAILPIT_URL = process.env.MAILPIT_URL ?? 'http://mailpit:8025';

export type ReceivedMail = { subject: string; text: string; firstLink: string | null };

type Summary = { ID: string; To: { Address: string }[] };

// 宛先が完全一致するメールの通数。検索の to: は部分一致なので宛先で絞り直す。
export async function mailCountTo(request: APIRequestContext, recipient: string): Promise<number> {
  const address = recipient.toLowerCase();
  const response = await request.get(`${MAILPIT_URL}/api/v1/search`, { params: { query: `to:"${recipient}"` } });
  expect(response.status(), await response.text()).toBe(200);
  const messages: Summary[] = (await response.json()).messages;
  return messages.filter(message => message.To.some(to => to.Address.toLowerCase() === address)).length;
}

// 送信は定期処理の中で進み画面の応答より遅れるので、届くまで待ってから最新の 1 通を読む。
export async function latestMailTo(request: APIRequestContext, recipient: string): Promise<ReceivedMail> {
  const address = recipient.toLowerCase();
  let id = '';
  await expect.poll(async () => {
    const response = await request.get(`${MAILPIT_URL}/api/v1/search`, { params: { query: `to:"${recipient}"` } });
    expect(response.status(), await response.text()).toBe(200);
    // to: は部分一致なので宛先の完全一致で絞る。一覧は新しい順。
    const messages: Summary[] = (await response.json()).messages;
    id = messages.find(message => message.To.some(to => to.Address.toLowerCase() === address))?.ID ?? '';
    return id;
  }, { message: `${recipient} 宛のメールが Mailpit に届かない`, timeout: 15000 }).not.toBe('');
  const response = await request.get(`${MAILPIT_URL}/api/v1/message/${id}`);
  expect(response.status(), await response.text()).toBe(200);
  const message: { Subject: string; Text: string } = await response.json();
  // 文末に置かれたリンクの後ろの句読点・括弧はリンクに含めない。
  const link = message.Text.match(/https?:\/\/[!-~]+/)?.[0].replace(/[.,;:!?)\]]+$/, '') ?? null;
  return { subject: message.Subject, text: message.Text, firstLink: link };
}
