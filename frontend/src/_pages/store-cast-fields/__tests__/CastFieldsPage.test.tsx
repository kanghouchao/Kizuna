import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { toast } from 'react-hot-toast';
import type { CastFieldDefinitionResponse } from '@/entities/cast';
import { castFieldDefinitionApi } from '@/entities/cast';
import CastFieldsPage from '../ui/CastFieldsPage';

jest.mock('@/entities/cast', () => ({
  castFieldDefinitionApi: {
    list: jest.fn(),
    create: jest.fn(),
    update: jest.fn(),
    delete: jest.fn(),
  },
}));

jest.mock('react-hot-toast', () => ({
  toast: { success: jest.fn(), error: jest.fn() },
}));

const mockedApi = castFieldDefinitionApi as jest.Mocked<typeof castFieldDefinitionApi>;
const mockedToast = toast as jest.Mocked<typeof toast>;

const definitions = [
  {
    id: 'def-1',
    key: 'blood_type',
    label: '血液型',
    display_order: 1,
    is_public: true,
  },
  {
    id: 'def-2',
    key: 'hobby',
    label: '趣味',
    display_order: 2,
    is_public: false,
  },
] as CastFieldDefinitionResponse[];

describe('カスタムフィールド定義の管理ページ', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedApi.list.mockResolvedValue(definitions);
    mockedApi.delete.mockResolvedValue(undefined as never);
  });

  it('一覧の key・label・公開設定・表示順を表示する', async () => {
    render(<CastFieldsPage />);

    expect(await screen.findByText('blood_type')).toBeInTheDocument();
    expect(screen.getByText('趣味')).toBeInTheDocument();
    expect(screen.getByText('公開')).toBeInTheDocument();
    expect(screen.getByText('非公開')).toBeInTheDocument();
  });

  it('削除は確認ダイアログを label 付きで出し、キャンセルされたら削除 API を呼ばない', async () => {
    render(<CastFieldsPage />);
    await screen.findByText('blood_type');

    fireEvent.click(screen.getAllByRole('button', { name: '削除' })[0]);

    const dialog = await screen.findByRole('alertdialog');
    expect(dialog).toHaveTextContent('「血液型」を削除しますか？');

    fireEvent.click(screen.getByRole('button', { name: 'キャンセル' }));

    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    expect(mockedApi.delete).not.toHaveBeenCalled();
  });

  it('削除を承諾すると対象 id を削除し一覧を再取得する', async () => {
    render(<CastFieldsPage />);
    await screen.findByText('blood_type');

    fireEvent.click(screen.getAllByRole('button', { name: '削除' })[1]);
    fireEvent.click(await screen.findByRole('button', { name: '削除する' }));

    await waitFor(() => expect(mockedApi.delete).toHaveBeenCalledWith('def-2'));
    await waitFor(() => expect(mockedApi.list).toHaveBeenCalledTimes(2));
    expect(mockedToast.success).toHaveBeenCalledWith('フィールドを削除しました');
  });

  it('削除ボタンのクリックは行クリックへ伝播せず編集モーダルを開かない', async () => {
    render(<CastFieldsPage />);
    await screen.findByText('blood_type');

    fireEvent.click(screen.getAllByRole('button', { name: '削除' })[0]);

    expect(screen.queryByText('フィールドを編集')).not.toBeInTheDocument();
  });

  it('行クリックで対象定義の編集モーダルが開く', async () => {
    render(<CastFieldsPage />);

    fireEvent.click(await screen.findByText('hobby'));

    expect(await screen.findByText('フィールドを編集')).toBeInTheDocument();
    expect(screen.getByLabelText('label')).toHaveValue('趣味');
  });

  it('フィールドを追加ボタンで作成モーダルが開く', async () => {
    render(<CastFieldsPage />);
    await screen.findByText('blood_type');

    fireEvent.click(screen.getByRole('button', { name: 'フィールドを追加' }));

    expect(await screen.findByText('フィールドを追加', { selector: 'h2' })).toBeInTheDocument();
  });

  it('定義が0件なら未登録メッセージを表示する', async () => {
    mockedApi.list.mockResolvedValue([]);
    render(<CastFieldsPage />);

    expect(await screen.findByText('フィールドが登録されていません')).toBeInTheDocument();
  });
});

describe('カスタムフィールド管理ページの外殻', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedApi.list.mockResolvedValue([]);
  });

  it('見出し（h1）・副題を備え、主アクションが button ロールのままであること', async () => {
    const { container } = render(<CastFieldsPage />);
    await screen.findByText('フィールドが登録されていません');

    expect(
      screen.getByRole('heading', { level: 1, name: 'カスタムフィールド管理' })
    ).toBeInTheDocument();
    // 外殻の class 文字列は DESIGN.md が規格として定めた面そのもの。
    expect(container.firstElementChild).toHaveClass('space-y-6');
    expect(
      screen.getByText(
        'キャストの追加プロフィール項目を定義します。公開設定した項目は公開詳細ページに表示されます。'
      )
    ).toBeInTheDocument();
    // e2e（cast-custom-fields）は button ロールで取得するため、リンク化してはならない
    expect(screen.getByRole('button', { name: 'フィールドを追加' })).toBeInTheDocument();
  });
});
