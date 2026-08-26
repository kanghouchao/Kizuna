import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { PageResult } from '@/shared/api';
import { StoreManagerCandidateResponse, storeManagerApi } from '@/entities/user';
import { notify } from '@/shared/notify';
import { StoreManagerAppointModal } from '../ui/StoreManagerAppointModal';

jest.mock('@/entities/user', () => ({
  storeManagerApi: { candidates: jest.fn(), appoint: jest.fn() },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedApi = storeManagerApi as jest.Mocked<typeof storeManagerApi>;

const paginated = (
  rows: StoreManagerCandidateResponse[],
  override: Partial<PageResult<StoreManagerCandidateResponse>> = {}
): PageResult<StoreManagerCandidateResponse> => ({
  rows,
  page: 0,
  pageCount: 1,
  total: rows.length,
  ...override,
});

const onClose = jest.fn();
const onAppointed = jest.fn();

const renderModal = () =>
  render(<StoreManagerAppointModal storeId="1" onClose={onClose} onAppointed={onAppointed} />);

beforeEach(() => {
  jest.clearAllMocks();
  mockedApi.candidates.mockResolvedValue(paginated([]));
});

describe('店長の任命モーダル', () => {
  it('既存アカウントの任命は候補の id だけを送ること', async () => {
    mockedApi.candidates.mockResolvedValue(
      paginated([{ id: 5, email: 'clerk@example.com', display_name: '山田次郎' }])
    );
    mockedApi.appoint.mockResolvedValue({ id: 5, enabled: true });

    renderModal();
    fireEvent.click(await screen.findByRole('button', { name: '任命' }));

    await waitFor(() => expect(mockedApi.appoint).toHaveBeenCalledWith('1', { user_id: 5 }));
    expect(notify.success).toHaveBeenCalledWith('店長に任命しました');
    expect(onAppointed).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('候補が 0 件なら任命できるアカウントが無いことを名乗ること', async () => {
    renderModal();

    expect(await screen.findByText('任命できるアカウントがありません')).toBeInTheDocument();
  });

  it('検索は 1 ページ目から取り直すこと', async () => {
    renderModal();
    await screen.findByText('任命できるアカウントがありません');

    fireEvent.change(screen.getByLabelText('任命候補を検索'), { target: { value: '山田' } });
    fireEvent.click(screen.getByRole('button', { name: '検索' }));

    await waitFor(() =>
      expect(mockedApi.candidates).toHaveBeenLastCalledWith('1', {
        page: 0,
        size: 5,
        search: '山田',
      })
    );
  });

  it('新規作成は 3 項目だけを送り、user_id を混ぜないこと', async () => {
    mockedApi.appoint.mockResolvedValue({ id: 9, enabled: true });

    renderModal();
    fireEvent.click(await screen.findByRole('tab', { name: '新規作成' }));

    fireEvent.change(screen.getByLabelText('メールアドレス'), {
      target: { value: 'new@example.com' },
    });
    fireEvent.change(screen.getByLabelText('初期パスワード'), { target: { value: 'secret123' } });
    fireEvent.change(screen.getByLabelText('氏名'), { target: { value: '初代店長' } });
    fireEvent.click(screen.getByRole('button', { name: '作成して任命' }));

    await waitFor(() =>
      expect(mockedApi.appoint).toHaveBeenCalledWith('1', {
        email: 'new@example.com',
        password: 'secret123',
        display_name: '初代店長',
      })
    );
  });

  // rules が必須を執行することだけを見る。noValidate の有無は jsdom が原生制約を実行しないため
  // ここでは赤にできない（外しても緑のままであることを実測済み）。
  it('新規作成の必須欄は未入力なら送信せず文言を出すこと', async () => {
    renderModal();
    fireEvent.click(await screen.findByRole('tab', { name: '新規作成' }));
    fireEvent.click(screen.getByRole('button', { name: '作成して任命' }));

    expect(await screen.findByText('メールアドレスを入力してください')).toBeInTheDocument();
    expect(screen.getByText('初期パスワードを入力してください')).toBeInTheDocument();
    expect(screen.getByText('氏名を入力してください')).toBeInTheDocument();
    expect(mockedApi.appoint).not.toHaveBeenCalled();
  });

  it('任命の拒否はサーバの文言をそのまま通知し、モーダルを閉じないこと', async () => {
    mockedApi.candidates.mockResolvedValue(
      paginated([{ id: 5, email: 'clerk@example.com', display_name: '山田次郎' }])
    );
    mockedApi.appoint.mockRejectedValue({
      response: { data: { error: 'このアカウントは既にこの店舗の店長です' } },
    });

    renderModal();
    fireEvent.click(await screen.findByRole('button', { name: '任命' }));

    await waitFor(() =>
      expect(notify.error).toHaveBeenCalledWith('このアカウントは既にこの店舗の店長です')
    );
    expect(onClose).not.toHaveBeenCalled();
  });
});
