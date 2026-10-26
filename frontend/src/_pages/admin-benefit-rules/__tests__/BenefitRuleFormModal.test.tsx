import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { benefitRuleApi } from '@/entities/benefit-rule';
import { BenefitRuleFormModal } from '../ui/BenefitRuleFormModal';

jest.mock('@/entities/benefit-rule', () => ({
  ...jest.requireActual('@/entities/benefit-rule'),
  benefitRuleApi: { get: jest.fn(), create: jest.fn(), update: jest.fn() },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedApi = benefitRuleApi as jest.Mocked<typeof benefitRuleApi>;

function renderCreateModal() {
  render(
    <BenefitRuleFormModal
      onClose={jest.fn()}
      editingId={null}
      onSaved={jest.fn()}
      stores={[]}
      storesLoading={false}
      storesFailed={false}
      onReloadStores={jest.fn()}
    />
  );
}

async function pick(comboboxName: string, optionName: string) {
  fireEvent.click(screen.getByRole('combobox', { name: comboboxName }));
  const option = await screen.findByRole('option', { name: optionName });
  fireEvent.pointerDown(option);
  fireEvent.click(option);
}

// 開いた一覧は閉じない（Escape は Dialog ごと閉じる）ので、各テストの最後に読む。
async function optionNamesOf(comboboxName: string): Promise<string[]> {
  fireEvent.click(screen.getByRole('combobox', { name: comboboxName }));
  const options = await screen.findAllByRole('option');
  return options.map(o => o.textContent ?? '');
}

describe('特典規則フォームの重複可否', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedApi.create.mockResolvedValue({ id: 1, store_count: 0, enabled: true });
  });

  it('ログインを選ぶと重複可否は一日一回・一人一回限りの二値に絞られ、既定は一日一回になる', async () => {
    renderCreateModal();
    await pick('種別', 'ログイン');

    await waitFor(() =>
      expect(screen.getByRole('combobox', { name: '重複可否' })).toHaveTextContent('一日一回')
    );
    expect(screen.getByText(/ログイン規則は毎回を取れません/)).toBeInTheDocument();
    expect(await optionNamesOf('重複可否')).toEqual(['一日一回', '一人一回限り']);
  });

  it('ログイン規則の作成は一日一回の重複可否を送る', async () => {
    renderCreateModal();
    await pick('種別', 'ログイン');
    fireEvent.change(screen.getByRole('textbox', { name: '規則名' }), {
      target: { value: 'ログインボーナス' },
    });
    fireEvent.change(screen.getByRole('spinbutton', { name: '付与ポイント' }), {
      target: { value: '10' },
    });
    await waitFor(() =>
      expect(screen.getByRole('combobox', { name: '重複可否' })).toHaveTextContent('一日一回')
    );

    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockedApi.create).toHaveBeenCalledTimes(1));
    expect(mockedApi.create).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'LOGIN',
        repeat_policy: 'ONCE_PER_DAY',
        store_scope_type: 'ALL_STORES',
        points: 10,
      })
    );
  });

  it('来店に戻すと毎回・一人一回限りへ戻り、ログインでしか取れない一日一回は残らない', async () => {
    renderCreateModal();
    await pick('種別', 'ログイン');
    await waitFor(() =>
      expect(screen.getByRole('combobox', { name: '重複可否' })).toHaveTextContent('一日一回')
    );

    await pick('種別', '来店');

    await waitFor(() =>
      expect(screen.getByRole('combobox', { name: '重複可否' })).toHaveTextContent('毎回')
    );
    expect(await optionNamesOf('重複可否')).toEqual(['毎回', '一人一回限り']);
  });
});
