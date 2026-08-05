import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import CastCreatePage from '../ui/CastCreatePage';
import CastEditPage from '../ui/CastEditPage';
import { castApi, castFieldDefinitionApi } from '@/entities/cast';

jest.mock('@/entities/cast', () => ({
  castApi: {
    create: jest.fn(),
    get: jest.fn(),
    update: jest.fn(),
  },
  castFieldDefinitionApi: {
    list: jest.fn(),
  },
}));

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn(), back: jest.fn() }),
  useParams: () => ({ storeId: '1', id: 'cast-1' }),
}));

const mockedCastApi = castApi as jest.Mocked<typeof castApi>;
const mockedFieldApi = castFieldDefinitionApi as jest.Mocked<typeof castFieldDefinitionApi>;

/** name 属性で入力欄を引く（register が付与するため、素の input でも Input プリミティブでも安定する）。 */
const inputByName = (container: HTMLElement, name: string) =>
  container.querySelector(`input[name="${name}"]`) as HTMLInputElement;

describe('キャスト登録・更新の送信ペイロード', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('新規登録は未操作の既定値ごとバックエンドの DTO に合わせ snake_case キーで POST すること', async () => {
    mockedCastApi.create.mockResolvedValue({} as never);

    const { container } = render(<CastCreatePage />);
    fireEvent.change(inputByName(container, 'name'), { target: { value: '花子' } });
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockedCastApi.create).toHaveBeenCalledTimes(1));
    const body = mockedCastApi.create.mock.calls[0][0] as unknown as Record<string, unknown>;
    expect(body).toHaveProperty('name', '花子');
    // 在籍状態を未操作のときの既定ペイロード
    expect(body).toHaveProperty('status', 'ACTIVE');
    expect(body).toHaveProperty('photo_url', '');
    expect(body).toHaveProperty('introduction', '');
    expect(body).toHaveProperty('display_order', 0);
    // 未入力の数値は null → undefined に落ち、JSON からキーごと欠落する
    expect(body).toHaveProperty('age', undefined);
    expect(body).toHaveProperty('hip', undefined);
    // camelCase キーが混入しないこと
    expect(body).not.toHaveProperty('displayOrder');
    expect(body).not.toHaveProperty('photoUrl');
  });

  it('入力後に空へ戻した数値は NaN のまま送信されること', async () => {
    mockedCastApi.create.mockResolvedValue({} as never);

    const { container } = render(<CastCreatePage />);
    fireEvent.change(inputByName(container, 'name'), { target: { value: '花子' } });
    fireEvent.change(inputByName(container, 'age'), { target: { value: '25' } });
    fireEvent.change(inputByName(container, 'age'), { target: { value: '' } });
    fireEvent.change(inputByName(container, 'height'), { target: { value: '160' } });
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockedCastApi.create).toHaveBeenCalledTimes(1));
    const body = mockedCastApi.create.mock.calls[0][0] as unknown as Record<string, unknown>;
    // valueAsNumber の空文字は NaN。null ではないため ?? を素通りし、JSON 化の段で null になる
    expect(Number.isNaN(body.age)).toBe(true);
    expect(body).toHaveProperty('height', 160);
  });

  it('編集の無変更保存は取得値と同一のボディで PUT すること（custom_fields 含む）', async () => {
    mockedCastApi.get.mockResolvedValue({
      id: 'cast-1',
      name: '花子',
      status: 'INACTIVE',
      photo_url: '',
      introduction: '紹介',
      age: 25,
      height: 160,
      display_order: 3,
      custom_fields: { blood_type: 'A' },
      invitation_status: 'NOT_INVITED',
      created_at: '2026-07-01T00:00:00Z',
      updated_at: '2026-07-01T00:00:00Z',
    });
    mockedCastApi.update.mockResolvedValue({} as never);
    mockedFieldApi.list.mockResolvedValue([
      {
        id: 'def-blood_type',
        key: 'blood_type',
        label: '血液型',
        display_order: 0,
        is_public: false,
        created_at: '2026-07-01T00:00:00Z',
        updated_at: '2026-07-01T00:00:00Z',
      },
    ]);

    render(<CastEditPage />);
    const customField = (await screen.findByLabelText('血液型')) as HTMLInputElement;
    expect(customField.value).toBe('A');
    // 取得値の在籍状態がセレクトに選択済みとして表示されること
    expect(screen.getByRole('combobox', { name: '在籍状態' })).toHaveTextContent('在籍停止');

    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockedCastApi.update).toHaveBeenCalledTimes(1));
    expect(mockedCastApi.update.mock.calls[0][0]).toBe('cast-1');
    const body = mockedCastApi.update.mock.calls[0][1] as unknown as Record<string, unknown>;
    expect(body).toHaveProperty('name', '花子');
    // プリフィルされた在籍状態が往復で変わらないこと
    expect(body).toHaveProperty('status', 'INACTIVE');
    expect(body).toHaveProperty('introduction', '紹介');
    expect(body).toHaveProperty('age', 25);
    expect(body).toHaveProperty('display_order', 3);
    // 取得値に無い数値は undefined のままキーごと欠落する
    expect(body).toHaveProperty('bust', undefined);
    expect(body).toHaveProperty('custom_fields', { blood_type: 'A' });
    expect(body).not.toHaveProperty('customFields');
    expect(body).not.toHaveProperty('invitation_status');
  });

  it('セレクトで選び直した在籍状態が送信ボディに反映されること', async () => {
    mockedCastApi.get.mockResolvedValue({
      id: 'cast-1',
      name: '花子',
      status: 'INACTIVE',
      photo_url: '',
      introduction: '',
      display_order: 0,
      custom_fields: {},
      invitation_status: 'NOT_INVITED',
      created_at: '2026-07-01T00:00:00Z',
      updated_at: '2026-07-01T00:00:00Z',
    });
    mockedCastApi.update.mockResolvedValue({} as never);
    mockedFieldApi.list.mockResolvedValue([]);

    render(<CastEditPage />);
    const trigger = await screen.findByRole('combobox', { name: '在籍状態' });
    expect(trigger).toHaveTextContent('在籍停止');

    // キーボードで開く経路のみを使う（ポインタ系 API は jsdom に無い）
    fireEvent.keyDown(trigger, { key: 'ArrowDown' });
    fireEvent.click(await screen.findByRole('option', { name: '在籍中' }));
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockedCastApi.update).toHaveBeenCalledTimes(1));
    const body = mockedCastApi.update.mock.calls[0][1] as unknown as Record<string, unknown>;
    // 取得値 INACTIVE ではなく、選び直した値がフォーム状態に届いていること
    expect(body).toHaveProperty('status', 'ACTIVE');
  });
});
