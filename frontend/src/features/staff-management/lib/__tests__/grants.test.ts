import { grantsOf, toGrantRequest } from '../grants';

it('応答の授権をフォームの形へ移し、欠けた店舗集合は空の集合とする', () => {
  expect(grantsOf({ roles: [{ id: 3, name: '店長' }], store_ids: [1, 2] })).toEqual({
    role_ids: [3],
    store_ids: [1, 2],
  });
  expect(grantsOf({ roles: [{ id: 3, name: '店長' }] })).toEqual({ role_ids: [3], store_ids: [] });
});

// 更新端点は未知のキーを 400 で拒むため、フォームの他の欄を混ぜない
it('API へはロールと店舗集合だけを送り、空の店舗集合もそのまま送る', () => {
  const values = { display_name: '夜間失効', role_ids: [3], store_ids: [] };
  expect(toGrantRequest(values)).toEqual({ role_ids: [3], store_ids: [] });
});
