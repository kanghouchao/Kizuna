import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { BenefitRuleSummaryResponse, benefitRuleApi } from '@/entities/benefit-rule';
import BenefitRulesPage from '../ui/BenefitRulesPage';

jest.mock('@/entities/benefit-rule', () => ({
  benefitRuleApi: { list: jest.fn(), deactivate: jest.fn() },
  benefitRuleTypeLabel: (type: string) =>
    ({ REFERRAL: '紹介', LOGIN: 'ログイン', VISIT: '来店' })[type] ?? type,
  benefitRuleRepeatPolicyLabel: (policy: string) =>
    ({ ONCE_PER_MEMBER: '一人一回限り', EVERY_TIME: '毎回' })[policy] ?? policy,
}));

jest.mock('@/entities/user', () => ({
  platformAuthApi: { stores: jest.fn(async () => []) },
}));

jest.mock('../ui/BenefitRuleFormModal', () => {
  const React = require('react');
  return {
    // 実体は開いたときだけ mount される。mock はマーカーだけ出す。
    BenefitRuleFormModal: () => React.createElement('div', null, '規則モーダル表示中'),
  };
});

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedApi = benefitRuleApi as jest.Mocked<typeof benefitRuleApi>;

const rule = (override: Partial<BenefitRuleSummaryResponse>): BenefitRuleSummaryResponse => ({
  id: 1,
  name: '来店ボーナス',
  type: 'VISIT',
  store_scope_type: 'ALL_STORES',
  store_count: 0,
  repeat_policy: 'EVERY_TIME',
  points: 500,
  enabled: true,
  version: 4,
  ...override,
});

function pageOf(rows: BenefitRuleSummaryResponse[]) {
  return { rows, page: 0, pageCount: 1, total: rows.length };
}

describe('特典規則一覧ページ', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedApi.list.mockResolvedValue(pageOf([rule({})]));
  });

  it('紹介規則は紹介者・被紹介者の二値を並べること', async () => {
    mockedApi.list.mockResolvedValue(
      pageOf([
        rule({
          id: 2,
          name: '紹介キャンペーン',
          type: 'REFERRAL',
          points: undefined,
          referrer_points: 1000,
          referred_points: 500,
        }),
      ])
    );
    render(<BenefitRulesPage />);
    await screen.findByText('紹介キャンペーン');

    expect(screen.getByText('紹介者 1000P / 被紹介者 500P')).toBeInTheDocument();
  });

  it('適用期間を持たない規則は常設と名乗ること', async () => {
    render(<BenefitRulesPage />);
    await screen.findByText('来店ボーナス');

    expect(screen.getByText('常設')).toBeInTheDocument();
    expect(screen.getByText('全店舗')).toBeInTheDocument();
  });

  it('停用済みの規則も一覧に並び、編集・停用の入口を持たないこと', async () => {
    mockedApi.list.mockResolvedValue(
      pageOf([rule({ id: 3, name: '終了した施策', enabled: false })])
    );
    render(<BenefitRulesPage />);
    await screen.findByText('終了した施策');

    expect(screen.getByText('停用済み')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '編集' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '停用' })).not.toBeInTheDocument();
  });

  it('モーダルは開くまで mount しないこと', async () => {
    render(<BenefitRulesPage />);
    await screen.findByText('来店ボーナス');

    expect(screen.queryByText('規則モーダル表示中')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '規則を作成' }));
    expect(screen.getByText('規則モーダル表示中')).toBeInTheDocument();
  });

  it('停用は確認を挟んでから実行すること（再開の口が無い一方通行のため）', async () => {
    mockedApi.deactivate.mockResolvedValue(undefined);
    render(<BenefitRulesPage />);
    await screen.findByText('来店ボーナス');

    fireEvent.click(screen.getByRole('button', { name: '停用' }));
    expect(mockedApi.deactivate).not.toHaveBeenCalled();

    fireEvent.click(await screen.findByRole('button', { name: '停用する' }));
    // 確認した行の版をそのまま運ぶ（見ていない規則を消させないため）
    await waitFor(() => expect(mockedApi.deactivate).toHaveBeenCalledWith(1, 4));
    // 停用後は一覧を取り直す（停用済みバッジへ切り替わる）
    await waitFor(() => expect(mockedApi.list).toHaveBeenCalledTimes(2));
  });
});
