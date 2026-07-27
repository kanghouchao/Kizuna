import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { toast } from 'react-hot-toast';
import { platformAuthApi, platformStaffApi } from '@/entities/user';
import { StaffCreateModal } from '../StaffCreateModal';

jest.mock('@/entities/user', () => ({
  platformAuthApi: { stores: jest.fn() },
  platformStaffApi: { bundles: jest.fn(), create: jest.fn() },
}));

jest.mock('react-hot-toast', () => ({
  toast: { success: jest.fn(), error: jest.fn() },
}));

const mockedAuthApi = platformAuthApi as jest.Mocked<typeof platformAuthApi>;
const mockedStaffApi = platformStaffApi as jest.Mocked<typeof platformStaffApi>;
const mockedToast = toast as jest.Mocked<typeof toast>;

const fillBasics = () => {
  fireEvent.change(screen.getByLabelText('メールアドレス'), {
    target: { value: 'new@example.com' },
  });
  fireEvent.change(screen.getByLabelText('初期パスワード'), { target: { value: 'secret' } });
  fireEvent.change(screen.getByLabelText('氏名'), { target: { value: '佐藤次郎' } });
};

describe('スタッフ新規作成モーダル', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedAuthApi.stores.mockResolvedValue([{ id: 9, name: '店舗A' }]);
    mockedStaffApi.bundles.mockResolvedValue([
      { id: 3, name: '店長' },
      { id: 4, name: '経理' },
    ]);
    mockedStaffApi.create.mockResolvedValue({} as never);
  });

  it('閉じているときは何も描画しない', () => {
    render(<StaffCreateModal open={false} onClose={jest.fn()} onCreated={jest.fn()} />);

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('入力値と選択状態を snake_case のまま作成 API へ送る', async () => {
    render(<StaffCreateModal open onClose={jest.fn()} onCreated={jest.fn()} />);
    await screen.findByLabelText('店長');

    fillBasics();
    fireEvent.click(screen.getByLabelText('店長'));
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    await waitFor(() => expect(mockedStaffApi.create).toHaveBeenCalledTimes(1));
    expect(mockedStaffApi.create.mock.calls[0][0]).toEqual({
      email: 'new@example.com',
      password: 'secret',
      display_name: '佐藤次郎',
      bundle_ids: [3],
      store_scope_type: 'ALL_STORES',
      store_ids: [],
      settlement_scope_type: null,
      settlement_store_ids: [],
    });
  });

  it('個別店舗を選ぶと店舗集合が SPECIFIC_STORES と店舗 id で送られる', async () => {
    render(<StaffCreateModal open onClose={jest.fn()} onCreated={jest.fn()} />);
    await screen.findByLabelText('店長');

    fillBasics();
    fireEvent.click(screen.getByLabelText('店長'));
    fireEvent.click(screen.getByLabelText('個別店舗'));
    fireEvent.click(await screen.findByLabelText('店舗A'));
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    await waitFor(() => expect(mockedStaffApi.create).toHaveBeenCalledTimes(1));
    expect(mockedStaffApi.create.mock.calls[0][0]).toMatchObject({
      store_scope_type: 'SPECIFIC_STORES',
      store_ids: [9],
    });
  });

  it('精算範囲は指定店舗を選ぶと scope と店舗 id が載る', async () => {
    render(<StaffCreateModal open onClose={jest.fn()} onCreated={jest.fn()} />);
    await screen.findByLabelText('店長');

    fillBasics();
    fireEvent.click(screen.getByLabelText('店長'));
    fireEvent.click(screen.getByLabelText('指定店舗'));
    const storeCheckboxes = await screen.findAllByLabelText('店舗A');
    fireEvent.click(storeCheckboxes[storeCheckboxes.length - 1]);
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    await waitFor(() => expect(mockedStaffApi.create).toHaveBeenCalledTimes(1));
    expect(mockedStaffApi.create.mock.calls[0][0]).toMatchObject({
      settlement_scope_type: 'SPECIFIC_STORES',
      settlement_store_ids: [9],
    });
  });

  it('権限束が未選択なら作成 API を呼ばず警告する', async () => {
    render(<StaffCreateModal open onClose={jest.fn()} onCreated={jest.fn()} />);
    await screen.findByLabelText('店長');

    fillBasics();
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    await waitFor(() =>
      expect(mockedToast.error).toHaveBeenCalledWith('権限束を 1 つ以上選択してください')
    );
    expect(mockedStaffApi.create).not.toHaveBeenCalled();
  });

  it('必須項目が空なら作成 API を呼ばない', async () => {
    render(<StaffCreateModal open onClose={jest.fn()} onCreated={jest.fn()} />);
    await screen.findByLabelText('店長');

    fireEvent.click(screen.getByLabelText('店長'));
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    await waitFor(() => expect(mockedStaffApi.bundles).toHaveBeenCalled());
    expect(mockedStaffApi.create).not.toHaveBeenCalled();
  });

  it('作成成功で onCreated と onClose を呼ぶ', async () => {
    const onClose = jest.fn();
    const onCreated = jest.fn();
    render(<StaffCreateModal open onClose={onClose} onCreated={onCreated} />);
    await screen.findByLabelText('店長');

    fillBasics();
    fireEvent.click(screen.getByLabelText('店長'));
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    await waitFor(() => expect(onCreated).toHaveBeenCalledTimes(1));
    expect(mockedToast.success).toHaveBeenCalledWith('スタッフを追加しました');
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('キャンセルは作成せず閉じる', async () => {
    const onClose = jest.fn();
    render(<StaffCreateModal open onClose={onClose} onCreated={jest.fn()} />);
    await screen.findByLabelText('店長');

    fireEvent.click(screen.getByRole('button', { name: 'キャンセル' }));

    expect(onClose).toHaveBeenCalledTimes(1);
    expect(mockedStaffApi.create).not.toHaveBeenCalled();
  });
});
