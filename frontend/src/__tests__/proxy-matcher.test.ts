/**
 * @jest-environment node
 */
import { unstable_doesMiddlewareMatch } from 'next/experimental/testing/server';
import { config } from '../proxy';

// matcher の判定は Next 自身の照合器で確かめる（照合器は Fetch の Request を要し、jsdom には無い）。
const matches = (path: string) =>
  unstable_doesMiddlewareMatch({ config, url: `http://store.test${path}` });

describe('proxy の matcher', () => {
  it.each([
    '/admin',
    '/admin/dashboard',
    '/admin/dashboard/',
    '/store',
    '/store/orders',
    '/cast',
    '/cast/schedule',
    '/member',
    '/member/receipts',
    '/member/reservations/new/',
  ])('画面遷移の守衛が要るエリア %s を通す', path => {
    expect(matches(path)).toBe(true);
  });

  it.each(['/', '/casts', '/casts/1', '/schedule', '/reservation', '/auth/login', '/stores'])(
    '公開サイトと認証画面 %s は通さない（応答に cookie を書かない）',
    path => {
      expect(matches(path)).toBe(false);
    }
  );
});
