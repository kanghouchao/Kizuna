import { createUuid } from '../uuid';

const v4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const original = crypto.randomUUID;
afterEach(() => {
  Object.defineProperty(crypto, 'randomUUID', { configurable: true, value: original });
});

it('安全なコンテキストでない（randomUUID の無い）http 環境でも版 4 の UUID を返す', () => {
  Object.defineProperty(crypto, 'randomUUID', { configurable: true, value: undefined });
  const first = createUuid();
  expect(first).toMatch(v4);
  expect(createUuid()).not.toBe(first);
});
