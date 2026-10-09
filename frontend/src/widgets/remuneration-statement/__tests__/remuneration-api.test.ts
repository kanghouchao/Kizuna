import { apiClient } from '@/shared/api';
import { remunerationApi } from '../api/remuneration';
jest.mock('@/shared/api', () => ({ apiClient: { get: jest.fn(), post: jest.fn() } }));
const get = jest.mocked(apiClient.get),
  post = jest.mocked(apiClient.post);
beforeEach(() => {
  jest.clearAllMocks();
  get.mockResolvedValue({ data: {} });
  post.mockResolvedValue({ data: {} });
});
it('本人照会には本人IDを送らず店と月と各ページを指定する', async () => {
  await remunerationApi.statement({ scope: 'self', storeId: 7 }, '2026-09', 2, 3);
  expect(get).toHaveBeenCalledWith('/platform/me/remuneration-statements', {
    params: { store_id: 7, month: '2026-09', order_page: 2, bonus_page: 3, size: 20 },
  });
});
it('店舗と平台はそれぞれの境界で本人を指定する', async () => {
  await remunerationApi.statement({ scope: 'store', personId: 4 }, '2026-09', 0, 0);
  expect(get).toHaveBeenLastCalledWith(
    '/store/remuneration-statements',
    expect.objectContaining({ params: expect.objectContaining({ person_id: 4 }) })
  );
  await remunerationApi.statement({ scope: 'platform', storeId: 7, personId: 4 }, '2026-09', 0, 0);
  expect(get).toHaveBeenLastCalledWith(
    '/platform/remuneration-statements',
    expect.objectContaining({ params: expect.objectContaining({ person_id: 4, store_id: 7 }) })
  );
});
it('条件の停止は空の日額、版、要求IDを保持して送信する', async () => {
  const input = {
    effective_from: '2026-10-01',
    state: 'STOPPED' as const,
    daily_amount: null,
    reason: '停止',
    expected_version: 2,
    request_id: 'request',
  };
  await remunerationApi.createGuarantee(4, input);
  expect(post).toHaveBeenLastCalledWith('/store/remuneration-guarantees', {
    ...input,
    person_id: 4,
  });
  await remunerationApi.correctGuarantee('g', { ...input, correction_reason: '日付訂正' });
  expect(post).toHaveBeenLastCalledWith('/store/remuneration-guarantees/g/corrections', {
    ...input,
    correction_reason: '日付訂正',
  });
  await remunerationApi.cancelGuarantee('g', {
    reason: '誤記',
    expected_version: 3,
    request_id: 'cancel',
  });
  expect(post).toHaveBeenLastCalledWith('/store/remuneration-guarantees/g/cancellation', {
    reason: '誤記',
    expected_version: 3,
    request_id: 'cancel',
  });
});
it('ボーナスの付与・有理由訂正・取消を別操作にする', async () => {
  const input = { award_date: '2026-09-30', amount: 1000, reason: '付与', request_id: 'request' };
  await remunerationApi.createBonus(4, input);
  expect(post).toHaveBeenLastCalledWith('/store/bonus-awards', { ...input, person_id: 4 });
  await remunerationApi.correctBonus('b', {
    ...input,
    expected_version: 0,
    correction_reason: '訂正',
  });
  expect(post).toHaveBeenLastCalledWith('/store/bonus-awards/b/corrections', {
    ...input,
    expected_version: 0,
    correction_reason: '訂正',
  });
  await remunerationApi.cancelBonus('b', {
    reason: '誤記',
    expected_version: 1,
    request_id: 'cancel',
  });
  expect(post).toHaveBeenLastCalledWith('/store/bonus-awards/b/cancellation', {
    reason: '誤記',
    expected_version: 1,
    request_id: 'cancel',
  });
});
it('増え続ける一覧と履歴はページとカーソルを引き継ぐ', async () => {
  await remunerationApi.guarantees(4, 2);
  expect(get).toHaveBeenLastCalledWith('/store/remuneration-guarantees', {
    params: { person_id: 4, page: 2, size: 20 },
  });
  await remunerationApi.bonuses(4, '2026-09', 1);
  expect(get).toHaveBeenLastCalledWith('/store/bonus-awards', {
    params: { person_id: 4, month: '2026-09', page: 1, size: 20 },
  });
  await remunerationApi.changes('guarantee', 'g', 'cursor');
  expect(get).toHaveBeenLastCalledWith('/store/remuneration-guarantees/g/changes', {
    params: { cursor: 'cursor', size: 20 },
  });
  await remunerationApi.changes('bonus', 'b');
  expect(get).toHaveBeenLastCalledWith('/store/bonus-awards/b/changes', {
    params: { cursor: undefined, size: 20 },
  });
});
