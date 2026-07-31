import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { toast } from 'react-hot-toast';
import { InvitationModal } from '../InvitationModal';

jest.mock('react-hot-toast', () => ({
  toast: { success: jest.fn(), error: jest.fn() },
}));

const mockedToast = toast as jest.Mocked<typeof toast>;
const writeText = jest.fn();
const execCommand = jest.fn();

const LINK = 'https://store.example.com/cast/invite/abc';

describe('キャスト招待リンクのモーダル', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    writeText.mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    });
    Object.defineProperty(document, 'execCommand', {
      configurable: true,
      value: execCommand,
    });
    execCommand.mockReturnValue(true);
  });

  it('閉じているときは何も描画しない', () => {
    render(<InvitationModal open={false} link={LINK} expiresAt={null} onClose={jest.fn()} />);

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('招待リンクは読み取り専用の入力欄へそのまま表示する', () => {
    render(<InvitationModal open link={LINK} expiresAt={null} onClose={jest.fn()} />);

    const input = screen.getByLabelText('招待リンク') as HTMLInputElement;
    expect(input.value).toBe(LINK);
    expect(input).toHaveAttribute('readonly');
  });

  it('コピーはクリップボードへ招待リンクを書き込む', async () => {
    render(<InvitationModal open link={LINK} expiresAt={null} onClose={jest.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: 'コピー' }));

    await waitFor(() => expect(writeText).toHaveBeenCalledWith(LINK));
    expect(mockedToast.success).toHaveBeenCalledWith('リンクをコピーしました');
  });

  it('Clipboard API が使えない場合は入力欄を選択してコピーする', async () => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: undefined,
    });
    render(<InvitationModal open link={LINK} expiresAt={null} onClose={jest.fn()} />);
    const input = screen.getByLabelText('招待リンク') as HTMLInputElement;
    const select = jest.spyOn(input, 'select');

    fireEvent.click(screen.getByRole('button', { name: 'コピー' }));

    await waitFor(() => expect(execCommand).toHaveBeenCalledWith('copy'));
    expect(select).toHaveBeenCalled();
    expect(mockedToast.success).toHaveBeenCalledWith('リンクをコピーしました');
  });

  it('コピー失敗は失敗トーストを出す', async () => {
    writeText.mockRejectedValue(new Error('denied'));
    render(<InvitationModal open link={LINK} expiresAt={null} onClose={jest.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: 'コピー' }));

    await waitFor(() =>
      expect(mockedToast.error).toHaveBeenCalledWith(
        'リンクをコピーできませんでした。招待リンクを選択して手動でコピーしてください。'
      )
    );
  });

  it('有効期限があれば日本語ロケールで表示し、無ければ表示しない', () => {
    const { rerender } = render(
      <InvitationModal open link={LINK} expiresAt="2026-07-31T15:00:00Z" onClose={jest.fn()} />
    );

    expect(screen.getByText(/有効期限:/)).toBeInTheDocument();

    rerender(<InvitationModal open link={LINK} expiresAt={null} onClose={jest.fn()} />);

    expect(screen.queryByText(/有効期限:/)).not.toBeInTheDocument();
  });

  it('閉じるは onClose を呼ぶ', () => {
    const onClose = jest.fn();
    render(<InvitationModal open link={LINK} expiresAt={null} onClose={onClose} />);

    fireEvent.click(screen.getByRole('button', { name: '閉じる' }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
