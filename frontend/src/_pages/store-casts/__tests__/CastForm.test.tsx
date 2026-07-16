import { render, screen } from '@testing-library/react';
import { CastForm } from '../ui/CastForm';
import { CastFieldDefinitionResponse, castFieldDefinitionApi } from '@/entities/cast';

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn(), back: jest.fn() }),
}));

jest.mock('@/entities/cast', () => {
  const actual = jest.requireActual('@/entities/cast');
  return {
    ...actual,
    castFieldDefinitionApi: {
      ...actual.castFieldDefinitionApi,
      list: jest.fn(),
    },
  };
});

const mockedApi = castFieldDefinitionApi as jest.Mocked<typeof castFieldDefinitionApi>;

const definition = (key: string, label: string): CastFieldDefinitionResponse => ({
  id: `def-${key}`,
  key,
  label,
  display_order: 0,
  is_public: false,
  created_at: '2026-07-01T00:00:00Z',
  updated_at: '2026-07-01T00:00:00Z',
});

describe('カスタムフィールドの初期値は自身が所有するキーのみ採用する（#277）', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('existingCustomFields が所有しない（プロトタイプ継承の）キーの値は初期値に採用せず空にすること', async () => {
    // 本番のトリガーは key='constructor'（Object.prototype 由来）だが、react-hook-form 自身が
    // 'constructor' を含むフィールド名の register で内部クラッシュする別バグがあり描画に至れない。
    // ここでは同じ「継承プロパティを素朴なブラケットアクセスで拾う」経路を、register 可能な
    // 非予約キーとプロトタイプ注入で再現し、hasOwn ガード（本修正）のみを切り出して検証する。
    const inherited = Object.create({ blood_type: 'INHERITED_VALUE' }) as Record<string, string>;
    mockedApi.list.mockResolvedValue([definition('blood_type', '血液型')]);

    render(
      <CastForm
        initialData={{ name: '花子' }}
        existingCustomFields={inherited}
        onSubmit={jest.fn()}
      />
    );

    const input = (await screen.findByLabelText('血液型')) as HTMLInputElement;
    expect(input.value).toBe('');
  });

  it('existingCustomFields が自身で所有するキーの値は初期値として採用すること（正常系の退行防止）', async () => {
    mockedApi.list.mockResolvedValue([definition('blood_type', '血液型')]);

    render(
      <CastForm
        initialData={{ name: '花子' }}
        existingCustomFields={{ blood_type: 'A' }}
        onSubmit={jest.fn()}
      />
    );

    const input = (await screen.findByLabelText('血液型')) as HTMLInputElement;
    expect(input.value).toBe('A');
  });
});
