import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { notify } from '@/shared/notify';
import CastCreatePage from '../ui/CastCreatePage';
import CastEditPage from '../ui/CastEditPage';
import { castApi, castFieldDefinitionApi } from '@/entities/cast';

const mockPush = jest.fn();

jest.mock('@/entities/cast', () => ({
  castApi: {
    create: jest.fn(),
    get: jest.fn(),
    update: jest.fn(),
    changePublication: jest.fn(),
    resume: jest.fn(),
    statusHistories: jest.fn(async () => ({ rows: [], nextCursor: null })),
    snapshots: jest.fn(async () => ({ rows: [], nextCursor: null })),
  },
  castFieldDefinitionApi: {
    list: jest.fn(),
  },
}));

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, back: jest.fn() }),
  useParams: () => ({ storeId: '1', id: 'cast-1' }),
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
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

  it('公開切替は未保存の源氏名を維持し、非公開へ戻せること', async () => {
    mockedCastApi.get.mockResolvedValue({
      id: 'cast-1',
      name: '花子',
      status: 'ENROLLED',
      publication_status: 'UNPUBLISHED',
    });
    mockedFieldApi.list.mockResolvedValue([]);
    mockedCastApi.changePublication
      .mockResolvedValueOnce({ publication_status: 'PUBLISHED' })
      .mockResolvedValueOnce({ publication_status: 'UNPUBLISHED' });
    const { container } = render(<CastEditPage />);
    await screen.findByRole('button', { name: '公開する' });
    fireEvent.change(inputByName(container, 'name'), { target: { value: '未保存の源氏名' } });
    fireEvent.click(screen.getByRole('button', { name: '公開する' }));
    await screen.findByRole('button', { name: '非公開にする' });
    expect(inputByName(container, 'name').value).toBe('未保存の源氏名');
    expect(mockedCastApi.changePublication).toHaveBeenLastCalledWith('cast-1', 'PUBLISHED');
    fireEvent.click(screen.getByRole('button', { name: '非公開にする' }));
    await screen.findByRole('button', { name: '公開する' });
    expect(mockedCastApi.changePublication).toHaveBeenLastCalledWith('cast-1', 'UNPUBLISHED');
    expect(mockedCastApi.get).toHaveBeenCalledTimes(1);
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
    expect(body).toHaveProperty('status', 'ENROLLED');
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
      status: 'SUSPENDED',
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
    expect(screen.getByText('在籍状態: 在籍停止')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockedCastApi.update).toHaveBeenCalledTimes(1));
    expect(mockedCastApi.update.mock.calls[0][0]).toBe('cast-1');
    const body = mockedCastApi.update.mock.calls[0][1] as unknown as Record<string, unknown>;
    expect(body).toHaveProperty('name', '花子');
    expect(body).not.toHaveProperty('status');
    expect(body).toHaveProperty('introduction', '紹介');
    expect(body).toHaveProperty('age', 25);
    expect(body).toHaveProperty('display_order', 3);
    // 取得値に無い数値は undefined のままキーごと欠落する
    expect(body).toHaveProperty('bust', undefined);
    expect(body).toHaveProperty('custom_fields', { blood_type: 'A' });
    expect(body).not.toHaveProperty('customFields');
    expect(body).not.toHaveProperty('invitation_status');
  });

  it('退店済みの取得値を汎用更新の状態として送信しないこと', async () => {
    mockedCastApi.get.mockResolvedValue({
      id: 'cast-1',
      name: '花子',
      status: 'WITHDRAWN',
    });
    mockedCastApi.update.mockResolvedValue({} as never);
    mockedFieldApi.list.mockResolvedValue([]);

    render(<CastEditPage />);
    fireEvent.click(await screen.findByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockedCastApi.update).toHaveBeenCalledTimes(1));
    const body = mockedCastApi.update.mock.calls[0][1];
    expect(body.name).toBe('花子');
    expect(JSON.parse(JSON.stringify(body))).not.toHaveProperty('status');
  });

  it('再開操作は専用 API を使い、未保存の資料入力を維持する', async () => {
    mockedCastApi.get.mockResolvedValue({ id: 'cast-1', name: '花子', status: 'SUSPENDED' });
    mockedCastApi.resume.mockResolvedValue({ id: 'cast-1', status: 'ENROLLED' });
    mockedFieldApi.list.mockResolvedValue([]);
    const { container } = render(<CastEditPage />);
    await screen.findByRole('button', { name: '再開する' });
    fireEvent.change(inputByName(container, 'name'), { target: { value: '未保存の源氏名' } });
    fireEvent.click(screen.getByRole('button', { name: '再開する' }));
    await screen.findByRole('button', { name: '停止する' });
    expect(inputByName(container, 'name').value).toBe('未保存の源氏名');
    expect(mockedCastApi.resume).toHaveBeenCalledWith('cast-1');
    expect(mockedCastApi.update).not.toHaveBeenCalled();
  });
});

describe('キャスト編集の取得失敗', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedFieldApi.list.mockResolvedValue([]);
  });

  it('取得に失敗しても一覧へ離脱せず、頁自身が失敗を名乗って再試行できること', async () => {
    mockedCastApi.get.mockRejectedValueOnce({ response: { status: 500 } });

    render(<CastEditPage />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('キャスト情報の取得に失敗しました')).toBeInTheDocument();
    // 離脱すると説明責任が着地先へ移り、開いていた頁で再試行できなくなる
    expect(mockPush).not.toHaveBeenCalled();
    expect(notify.error).not.toHaveBeenCalled();

    mockedCastApi.get.mockResolvedValue({
      id: 'cast-1',
      name: '花子',
      status: 'ENROLLED',
      display_order: 0,
      invitation_status: 'NOT_INVITED',
      created_at: '2026-07-01T00:00:00Z',
      updated_at: '2026-07-01T00:00:00Z',
    });
    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByRole('button', { name: '保存する' })).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('応答が空でも白紙にせず、再試行できる失敗として名乗ること', async () => {
    // 例外にならない空応答（204・本文なし）でも描くものは無い。白紙で返すと失敗の告知も
    // 再試行の導線も同時に消える。404 ではないので、出すのは再試行を持つ側の姿
    mockedCastApi.get.mockResolvedValueOnce(undefined as never);

    render(<CastEditPage />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('キャスト情報の取得に失敗しました')).toBeInTheDocument();
    expect(within(region).getByRole('button', { name: '再試行' })).toBeInTheDocument();
    expect(screen.queryByText('このキャストは見つかりませんでした')).not.toBeInTheDocument();
    expect(mockPush).not.toHaveBeenCalled();
  });

  it('404 では再試行を出さず、一覧への導線だけを出すこと', async () => {
    mockedCastApi.get.mockRejectedValueOnce({ response: { status: 404 } });

    render(<CastEditPage />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('このキャストは見つかりませんでした')).toBeInTheDocument();
    // 何度押しても取れないものを押させない
    expect(within(region).queryByRole('button', { name: '再試行' })).not.toBeInTheDocument();
    expect(within(region).getByRole('link', { name: 'キャスト一覧へ' })).toHaveAttribute(
      'href',
      '/store/1/casts'
    );
  });
});

it('公開切替が失敗しても元の状態と入力を保持し、再試行できる', async () => {
  mockedCastApi.get.mockResolvedValue({
    id: 'cast-1',
    name: '花子',
    status: 'ENROLLED',
    publication_status: 'UNPUBLISHED',
  });
  mockedFieldApi.list.mockResolvedValue([]);
  mockedCastApi.changePublication
    .mockRejectedValueOnce(new Error('network'))
    .mockResolvedValueOnce({ publication_status: 'PUBLISHED' });
  render(<CastEditPage />);
  fireEvent.change(await screen.findByLabelText('源氏名 *'), { target: { value: '未保存' } });
  fireEvent.click(screen.getByRole('button', { name: '公開する' }));
  await waitFor(() => expect(notify.error).toHaveBeenCalledWith('公開状態の更新に失敗しました'));
  expect(screen.getByText('公開状態: 非公開')).toBeInTheDocument();
  expect(screen.getByLabelText('源氏名 *')).toHaveValue('未保存');
  fireEvent.click(screen.getByRole('button', { name: '公開する' }));
  expect(await screen.findByRole('button', { name: '非公開にする' })).toBeEnabled();
});
