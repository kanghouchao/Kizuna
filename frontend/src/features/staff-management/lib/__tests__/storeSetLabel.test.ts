import { storeSetLabel } from '../storeSetLabel';

const stores = [
  { id: 1, name: '新宿店' },
  { id: 2, name: '渋谷店' },
];

it('空の店舗集合は「担当店舗なし」と表す', () => {
  expect(storeSetLabel([], stores)).toBe('担当店舗なし');
  expect(storeSetLabel(undefined, stores)).toBe('担当店舗なし');
});

it('目録で引ける店舗は名前を並べる', () => {
  expect(storeSetLabel([2, 1], stores)).toBe('新宿店・渋谷店');
});

// 目録が未着・行使者の集合が空などで名前を引けなくても、担当が無いとは述べない
it('名前を引けない店舗しか無ければ件数で示す', () => {
  expect(storeSetLabel([5, 6], [])).toBe('2 店舗');
});
