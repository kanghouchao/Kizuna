import {
  benefitRuleApi,
  benefitRuleRepeatPolicyLabel,
  benefitRuleTypeLabel,
} from '@/entities/benefit-rule';
import { apiClient } from '@/shared/api';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async () => ({ data: {} })),
    post: jest.fn(async () => ({ data: {} })),
    put: jest.fn(async () => ({ data: {} })),
  },
}));

const mockedGet = apiClient.get as jest.Mock;
const mockedPost = apiClient.post as jest.Mock;
const mockedPut = apiClient.put as jest.Mock;

describe('benefitRuleApi', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('list は Spring Page を正規化して返す', async () => {
    mockedGet.mockResolvedValueOnce({
      data: {
        content: [{ id: 1, name: '来店ボーナス', store_count: 0, enabled: true }],
        number: 0,
        total_pages: 1,
        total_elements: 1,
      },
    });

    await expect(benefitRuleApi.list({ page: 0, size: 10 })).resolves.toEqual({
      rows: [{ id: 1, name: '来店ボーナス', store_count: 0, enabled: true }],
      page: 0,
      pageCount: 1,
      total: 1,
    });
    expect(mockedGet).toHaveBeenCalledWith('/platform/benefit-rules', {
      params: { page: 0, size: 10 },
    });
  });

  it('get は編集フォーム向けの詳細を GET する', async () => {
    mockedGet.mockResolvedValueOnce({ data: { id: 7, store_ids: [3, 5] } });

    await expect(benefitRuleApi.get(7)).resolves.toEqual({ id: 7, store_ids: [3, 5] });
    expect(mockedGet).toHaveBeenCalledWith('/platform/benefit-rules/7');
  });

  it('create は種別を含む本体を POST する', async () => {
    await benefitRuleApi.create({
      name: '来店ボーナス',
      type: 'VISIT',
      store_scope_type: 'ALL_STORES',
      repeat_policy: 'EVERY_TIME',
      points: 500,
    });

    expect(mockedPost).toHaveBeenCalledWith('/platform/benefit-rules', {
      name: '来店ボーナス',
      type: 'VISIT',
      store_scope_type: 'ALL_STORES',
      repeat_policy: 'EVERY_TIME',
      points: 500,
    });
  });

  it('update は単一リソース URI へ PUT し、取得した version を往復する', async () => {
    await benefitRuleApi.update(7, {
      name: '来店ボーナス',
      store_scope_type: 'ALL_STORES',
      repeat_policy: 'EVERY_TIME',
      points: 500,
      version: 3,
    });

    expect(mockedPut).toHaveBeenCalledWith('/platform/benefit-rules/7', {
      name: '来店ボーナス',
      store_scope_type: 'ALL_STORES',
      repeat_policy: 'EVERY_TIME',
      points: 500,
      version: 3,
    });
  });

  it('停用は名詞化した子リソースへの POST で確認した版を運び、削除の口は持たない', async () => {
    await benefitRuleApi.deactivate(7, 3);

    expect(mockedPost).toHaveBeenCalledWith('/platform/benefit-rules/7/deactivation', {
      version: 3,
    });
    expect(benefitRuleApi).not.toHaveProperty('remove');
    expect(benefitRuleApi).not.toHaveProperty('delete');
  });
});

describe('特典規則の訳語', () => {
  it('目録の値を日本語へ写す', () => {
    expect(benefitRuleTypeLabel('REFERRAL')).toBe('紹介');
    expect(benefitRuleTypeLabel('LOGIN')).toBe('ログイン');
    expect(benefitRuleTypeLabel('VISIT')).toBe('来店');
    expect(benefitRuleRepeatPolicyLabel('ONCE_PER_MEMBER')).toBe('一人一回限り');
    expect(benefitRuleRepeatPolicyLabel('EVERY_TIME')).toBe('毎回');
  });

  it('未知の値はコードのまま出す（サーバが種別を増やした日に空欄へ落とさない）', () => {
    expect(benefitRuleTypeLabel('BIRTHDAY' as never)).toBe('BIRTHDAY');
    expect(benefitRuleTypeLabel(undefined)).toBe('');
    expect(benefitRuleRepeatPolicyLabel(undefined)).toBe('');
  });
});
