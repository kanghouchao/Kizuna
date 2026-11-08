import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { CastRequestsPage } from '../CastRequestsPage';
import { shiftApi } from '@/entities/shift';
import { notify } from '@/shared/notify';

jest.mock('@/entities/shift', () => ({
  shiftApi: {
    myStores: jest.fn(),
    myShiftRequests: jest.fn(),
    submitShiftRequest: jest.fn(),
  },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedMyStores = shiftApi.myStores as jest.Mock;
const mockedMyShiftRequests = shiftApi.myShiftRequests as jest.Mock;
const mockedSubmit = shiftApi.submitShiftRequest as jest.Mock;
const mockedNotifyError = notify.error as jest.Mock;

const STORES = [
  { store_id: 1, store_name: '店舗A' },
  { store_id: 2, store_name: '店舗B' },
];

function historyItem(over: Record<string, unknown>) {
  return {
    id: 'sr',
    work_date: '2025-08-01',
    start_time: '18:00:00',
    end_time: '23:00:00',
    note: null,
    type: 'NEW',
    status: 'PENDING',
    store_id: 1,
    store_name: '店舗A',
    created_at: '2025-07-20T00:00:00Z',
    ...over,
  };
}

/** 提出フォームの初期日付（明日）。 */
function tomorrowStr(): string {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
    d.getDate()
  ).padStart(2, '0')}`;
}

/** 所属店舗の読み込みが済み、提出の入口が押せるようになるのを待ってからシートを開く。 */
async function openSheet() {
  const trigger = await screen.findByRole('button', { name: '出勤希望を出す' });
  await waitFor(() => expect(trigger).toBeEnabled());
  fireEvent.click(trigger);
  return screen.findByRole('dialog', { name: '出勤希望を出す' });
}

function page(rows: unknown[]) {
  return { rows, page: 0, pageCount: rows.length === 0 ? 0 : 1, total: rows.length };
}

describe('CastRequestsPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedMyStores.mockResolvedValue(STORES);
    mockedMyShiftRequests.mockResolvedValue(page([]));
  });

  describe('提出履歴', () => {
    it('各行に店舗名・共通書式の日付と時間帯・状態を出すこと', async () => {
      mockedMyShiftRequests.mockResolvedValue(
        page([
          historyItem({ id: 'sr1', note: 'よろしくお願いします' }),
          historyItem({
            id: 'sr2',
            work_date: '2025-08-02',
            start_time: '20:00:00',
            end_time: '02:00:00',
            store_name: '店舗B',
            status: 'APPROVED',
          }),
        ])
      );

      render(<CastRequestsPage />);

      const history = await screen.findByRole('list', { name: '提出履歴' });
      const rows = within(history).getAllByRole('listitem');
      expect(rows[0]).toHaveTextContent('店舗A');
      expect(rows[0]).toHaveTextContent('2025/8/1（金）18:00〜23:00');
      expect(rows[0]).toHaveTextContent('よろしくお願いします');
      expect(within(rows[0]).getByText('申請中')).toBeInTheDocument();
      expect(rows[1]).toHaveTextContent('店舗B');
      expect(rows[1]).toHaveTextContent('2025/8/2（土）20:00〜翌2:00');
      expect(within(rows[1]).getByText('承認')).toBeInTheDocument();
    });

    it('通常の申請も変更申請も、店舗の否定は「却下」と名乗ること', async () => {
      mockedMyShiftRequests.mockResolvedValue(
        page([
          historyItem({ id: 'sr1', status: 'DECLINED' }),
          historyItem({ id: 'sr2', type: 'CHANGE', status: 'DECLINED' }),
          historyItem({ id: 'sr3', type: 'CHANGE', status: 'APPROVED' }),
        ])
      );

      render(<CastRequestsPage />);

      const history = await screen.findByRole('list', { name: '提出履歴' });
      expect(within(history).getAllByText('却下')).toHaveLength(2);
      expect(within(history).getByText('承認')).toBeInTheDocument();
      expect(within(history).getAllByText('変更申請')).toHaveLength(2);
      expect(screen.queryByText('謝絶')).not.toBeInTheDocument();
    });
  });

  describe('提出', () => {
    it('1 店舗なら店舗チップを出さず、下部シートがその店舗を名乗って既定値で提出すること', async () => {
      mockedMyStores.mockResolvedValue([STORES[0]]);
      mockedSubmit.mockResolvedValue({ id: 'sr1', status: 'PENDING' });
      render(<CastRequestsPage />);

      const sheet = await openSheet();
      expect(screen.queryByRole('group', { name: '店舗' })).not.toBeInTheDocument();
      expect(sheet).toHaveAccessibleDescription('店舗A に提出します');
      fireEvent.click(within(sheet).getByRole('button', { name: '提出する' }));

      await waitFor(() => expect(mockedSubmit).toHaveBeenCalledTimes(1));
      // note のキーは値が undefined でも残る。toStrictEqual はキーの欠落を検出する。
      expect(mockedSubmit.mock.calls[0][0]).toStrictEqual({
        store_id: 1,
        work_date: tomorrowStr(),
        start_time: '18:00:00',
        end_time: '23:00:00',
        note: undefined,
      });
      // 提出した希望は先頭ページの最上段に載るので、シートを閉じて先頭ページを取り直す
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
      expect(mockedMyShiftRequests).toHaveBeenLastCalledWith(0);
      expect(mockedMyShiftRequests).toHaveBeenCalledTimes(2);
    });

    it('複数店舗なら店舗チップで提出先を選び、提出後も選んだ店舗を保つこと', async () => {
      mockedSubmit.mockResolvedValue({ id: 'sr1', status: 'PENDING' });
      render(<CastRequestsPage />);

      const chips = await screen.findByRole('group', { name: '店舗' });
      expect(within(chips).getByRole('button', { name: '店舗A' })).toHaveAttribute(
        'aria-pressed',
        'true'
      );
      fireEvent.click(within(chips).getByRole('button', { name: '店舗B' }));

      let sheet = await openSheet();
      expect(sheet).toHaveAccessibleDescription('店舗B に提出します');
      fireEvent.change(within(sheet).getByLabelText('開始'), { target: { value: '20:00' } });
      fireEvent.change(within(sheet).getByLabelText('備考'), { target: { value: '遅れます' } });
      fireEvent.click(within(sheet).getByRole('button', { name: '提出する' }));

      await waitFor(() => expect(mockedSubmit).toHaveBeenCalledTimes(1));
      expect(mockedSubmit.mock.calls[0][0]).toMatchObject({
        store_id: 2,
        start_time: '20:00:00',
        note: '遅れます',
      });
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

      // 日時は既定へ戻り、提出先は選んだ店舗のまま
      sheet = await openSheet();
      expect(sheet).toHaveAccessibleDescription('店舗B に提出します');
      expect(within(sheet).getByLabelText('開始')).toHaveValue('18:00');
    });

    it('過去日は client 側で弾かず、サーバの拒否文言を出してシートを開いたままにすること', async () => {
      // 受理できる下限は営業日で決まり、その境界を知るのはサーバだけ。client 側で暦日から下限を組むと
      // 深夜帯や時差でサーバと食い違い、サーバが受け付ける日を先に弾いてしまう。
      mockedSubmit.mockRejectedValue({
        response: { status: 400, data: { error: '勤務日は本日以降を指定してください' } },
      });
      render(<CastRequestsPage />);

      const sheet = await openSheet();
      fireEvent.change(within(sheet).getByLabelText('日付'), { target: { value: '2000-01-01' } });
      fireEvent.click(within(sheet).getByRole('button', { name: '提出する' }));

      await waitFor(() =>
        expect(mockedNotifyError).toHaveBeenCalledWith('勤務日は本日以降を指定してください')
      );
      expect(mockedSubmit.mock.calls[0][0]).toMatchObject({ work_date: '2000-01-01' });
      expect(screen.getByRole('dialog', { name: '出勤希望を出す' })).toBeInTheDocument();
    });

    it('備考が501文字だと検証エラーを表示し、提出しないこと', async () => {
      render(<CastRequestsPage />);

      const sheet = await openSheet();
      fireEvent.change(within(sheet).getByLabelText('備考'), {
        target: { value: 'あ'.repeat(501) },
      });
      fireEvent.click(within(sheet).getByRole('button', { name: '提出する' }));

      expect(
        await within(sheet).findByText('備考は500文字以内で入力してください')
      ).toBeInTheDocument();
      expect(mockedSubmit).not.toHaveBeenCalled();
    });

    it('所属店舗が無いときは理由を出し、提出の入口を押せないこと', async () => {
      mockedMyStores.mockResolvedValue([]);
      render(<CastRequestsPage />);

      expect(
        await screen.findByText('所属店舗が無いため、出勤希望を出せません')
      ).toBeInTheDocument();
      expect(screen.getByRole('button', { name: '出勤希望を出す' })).toBeDisabled();
    });

    it('所属店舗の取得中は「所属店舗が無い」と言い切らず、入口を押せないこと', async () => {
      mockedMyStores.mockReturnValue(new Promise(() => {}));
      render(<CastRequestsPage />);

      // 履歴側の読み込みは先に解ける
      expect(await screen.findByText('まだ提出した希望はありません')).toBeInTheDocument();
      expect(
        screen.queryByText('所属店舗が無いため、出勤希望を出せません')
      ).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: '出勤希望を出す' })).toBeDisabled();
    });

    it('所属店舗が取れなければ履歴ではなく店舗の区画が失敗を名乗り、再試行できること', async () => {
      mockedMyStores.mockRejectedValueOnce(new Error('network error'));
      mockedMyStores.mockResolvedValueOnce(STORES);
      render(<CastRequestsPage />);

      const region = await screen.findByRole('alert');
      expect(within(region).getByText('所属店舗の取得に失敗しました')).toBeInTheDocument();
      expect(screen.queryByText('履歴の取得に失敗しました')).not.toBeInTheDocument();

      fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

      expect(await screen.findByRole('group', { name: '店舗' })).toBeInTheDocument();
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });
  });

  describe('提出履歴の取得', () => {
    it('取得に失敗した場合はその区画に失敗を出し、再試行で空の案内へ復帰できること', async () => {
      mockedMyShiftRequests.mockRejectedValueOnce(new Error('network error'));
      mockedMyShiftRequests.mockResolvedValueOnce(page([]));
      render(<CastRequestsPage />);

      const region = await screen.findByRole('alert');
      expect(within(region).getByText('履歴の取得に失敗しました')).toBeInTheDocument();

      fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

      expect(await screen.findByText('まだ提出した希望はありません')).toBeInTheDocument();
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it('履歴をページ番号で送り、次のページを取り直すこと', async () => {
      mockedMyShiftRequests
        .mockResolvedValueOnce({
          rows: [historyItem({ id: 'sr2', store_name: '新しい店舗' })],
          page: 0,
          pageCount: 2,
          total: 2,
        })
        .mockResolvedValueOnce({
          rows: [historyItem({ id: 'sr1', store_name: '古い店舗' })],
          page: 1,
          pageCount: 2,
          total: 2,
        });
      render(<CastRequestsPage />);

      expect(await screen.findByText('新しい店舗')).toBeInTheDocument();
      fireEvent.click(screen.getAllByRole('button', { name: '次へ' })[0]);
      expect(await screen.findByText('古い店舗')).toBeInTheDocument();
      expect(screen.queryByText('新しい店舗')).not.toBeInTheDocument();
      expect(mockedMyShiftRequests).toHaveBeenLastCalledWith(1);
    });
  });
});
