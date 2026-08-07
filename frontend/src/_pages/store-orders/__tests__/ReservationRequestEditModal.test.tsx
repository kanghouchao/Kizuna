import { StrictMode } from 'react';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { ReservationRequestEditModal } from '../ui/ReservationRequestEditModal';
import { Order, orderApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  orderApi: {
    listReceptionists: jest.fn(),
    updateReservationRequest: jest.fn(),
    listCastCandidates: jest.fn(),
  },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedUpdate = orderApi.updateReservationRequest as jest.Mock;
const mockedReceptionists = orderApi.listReceptionists as jest.Mock;
const mockedCastCandidates = orderApi.listCastCandidates as jest.Mock;

const nominationFreeRequest: Order = {
  id: 'o1',
  status: 'CREATED',
  reception_route: 'WEB',
  business_date: '2026-08-10',
  pax: 3,
  remarks: '元の備考',
};

const nominatedRequest: Order = {
  ...nominationFreeRequest,
  cast_id: 'cast-1',
  cast_name: 'あや',
};

const renderModal = (request: Order, onSaved = jest.fn(), onClose = jest.fn()) =>
  render(<ReservationRequestEditModal request={request} onClose={onClose} onSaved={onSaved} />);

describe('ReservationRequestEditModal', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedReceptionists.mockResolvedValue([]);
    mockedUpdate.mockResolvedValue({});
  });

  it('指名なしの申請を、キャストを埋めずに保存できる', async () => {
    renderModal(nominationFreeRequest);

    fireEvent.change(await screen.findByLabelText('人数'), { target: { value: '5' } });
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() =>
      expect(mockedUpdate).toHaveBeenCalledWith('o1', {
        receptionist_id: undefined,
        cast_id: undefined,
        pax: 5,
        remarks: '元の備考',
      })
    );
  });

  it('指名を外さなければ、そのまま送り返して指名を維持する', async () => {
    // 契約は部分更新ではないため、送らなかった指名は消える。維持は「送り返す」ことで成立する
    renderModal(nominatedRequest);

    fireEvent.click(await screen.findByRole('button', { name: '保存する' }));

    await waitFor(() =>
      expect(mockedUpdate).toHaveBeenCalledWith(
        'o1',
        expect.objectContaining({ cast_id: 'cast-1' })
      )
    );
  });

  it('指名を外すと、キャストを送らずに保存して一覧の取り直しを促す', async () => {
    const onSaved = jest.fn();
    const onClose = jest.fn();
    renderModal(nominatedRequest, onSaved, onClose);

    fireEvent.click(await screen.findByRole('checkbox', { name: '指名を外す' }));
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() =>
      expect(mockedUpdate).toHaveBeenCalledWith(
        'o1',
        expect.objectContaining({ cast_id: undefined })
      )
    );
    expect(onSaved).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('指名が無い申請には指名解除の操作を出さない', async () => {
    renderModal(nominationFreeRequest);

    // 外す対象が無いので、解除は操作としてそもそも現れない
    expect(await screen.findByRole('combobox', { name: '指名' })).toHaveTextContent('名前で検索');
    expect(screen.queryByRole('checkbox', { name: '指名を外す' })).not.toBeInTheDocument();
  });

  it('人数が空欄なら送信せず、無反応にもせず理由を出す', async () => {
    // 検証の結果を出さないと、押した保存が効いているのか分からないまま止まる
    renderModal(nominationFreeRequest);

    fireEvent.change(await screen.findByLabelText('人数'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    expect(await screen.findByText('人数を入力してください')).toBeInTheDocument();
    expect(mockedUpdate).not.toHaveBeenCalled();
  });

  it('備考がサーバ側の上限を超えたら送信せず理由を出す', async () => {
    renderModal(nominationFreeRequest);

    fireEvent.change(await screen.findByLabelText('備考'), {
      target: { value: 'あ'.repeat(501) },
    });
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    expect(await screen.findByText('備考は 500 文字以内で入力してください')).toBeInTheDocument();
    expect(mockedUpdate).not.toHaveBeenCalled();
  });

  it('保存に失敗したら、対処方法を含むサーバの文言をそのまま出す', async () => {
    // 指名の検証は「在籍中を選ぶか外すか」を伝える 400 を返す。汎用文言に潰すと行動できない
    mockedUpdate.mockRejectedValue({
      response: {
        status: 400,
        data: {
          error: '指名できるキャストではありません。在籍中のキャストを選ぶか、指名を外してください',
        },
      },
    });
    const onSaved = jest.fn();
    renderModal(nominatedRequest, onSaved);

    fireEvent.click(await screen.findByRole('button', { name: '保存する' }));

    await waitFor(() =>
      expect(notify.error).toHaveBeenCalledWith(
        '指名できるキャストではありません。在籍中のキャストを選ぶか、指名を外してください'
      )
    );
    expect(onSaved).not.toHaveBeenCalled();
  });

  // Strict Mode は mount effect を二度走らせるので取得が二重に飛ぶ。失敗が選択肢をクリアする
  // 以上、遅れて着いた古い失敗が新しい成功を消してはいけない
  it('二重 mount で古い失敗が後から着いても、新しい成功を消さないこと', async () => {
    let failStale = (): void => {};
    mockedReceptionists
      .mockReturnValueOnce(
        new Promise((_, reject) => {
          failStale = () => reject(new Error('stale'));
        })
      )
      .mockResolvedValue([{ id: 7, display_name: '受付花子' }]);

    render(
      <StrictMode>
        <ReservationRequestEditModal
          request={{ ...nominationFreeRequest, receptionist_id: 7 }}
          onClose={jest.fn()}
          onSaved={jest.fn()}
        />
      </StrictMode>
    );

    // 引き金の文言は候補一覧から引かれるので、これが出ていれば 2 度目の成功が着いている
    const trigger = await screen.findByRole('combobox', { name: '受付担当' });
    await waitFor(() => expect(trigger).toHaveTextContent('受付花子'));

    await act(async () => {
      failStale();
    });

    expect(trigger).toHaveTextContent('受付花子');
    expect(screen.queryByText('受付担当者の取得に失敗しました')).not.toBeInTheDocument();
  });
});

describe('ReservationRequestEditModal の指名の差し替え', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.useFakeTimers();
    mockedReceptionists.mockResolvedValue([]);
    mockedUpdate.mockResolvedValue({});
    mockedCastCandidates.mockResolvedValue([{ id: 'cast-2', name: 'みか' }]);
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  /** 指名の選択を開き、初回の候補取得まで進める。 */
  const openPicker = async () => {
    fireEvent.click(screen.getByRole('combobox', { name: '指名' }));
    await act(async () => {
      jest.advanceTimersByTime(300);
    });
  };

  /** 選択の中で絞り込む。検索語は選択を閉じると捨てられる。 */
  const search = async (keyword: string) => {
    fireEvent.change(screen.getByPlaceholderText('名前で検索'), { target: { value: keyword } });
    await act(async () => {
      jest.advanceTimersByTime(300);
    });
  };

  const save = async () => {
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));
    await act(async () => {
      jest.advanceTimersByTime(0);
    });
  };

  it('指名済みの申請を、別のキャストへ差し替えて保存できる', async () => {
    renderModal(nominatedRequest);

    expect(screen.getByRole('combobox', { name: '指名' })).toHaveTextContent('あや');
    await openPicker();
    await search('み');
    const option = screen.getByRole('option', { name: /みか/ });
    // Base UI の Item は pointerdown を経ていない mouse click を無視する
    fireEvent.pointerDown(option);
    fireEvent.click(option);
    await save();

    expect(mockedUpdate).toHaveBeenCalledWith('o1', expect.objectContaining({ cast_id: 'cast-2' }));
  });

  it('指名なしの申請に、キャストを立てて保存できる', async () => {
    renderModal(nominationFreeRequest);

    await openPicker();
    const option = screen.getByRole('option', { name: /みか/ });
    // Base UI の Item は pointerdown を経ていない mouse click を無視する
    fireEvent.pointerDown(option);
    fireEvent.click(option);
    await save();

    expect(mockedUpdate).toHaveBeenCalledWith('o1', expect.objectContaining({ cast_id: 'cast-2' }));
  });

  it('絞り込んだだけで選ばずに閉じても、元の指名はそのまま残る', async () => {
    // 検索語は選択の中だけに在り、指名を動かすのは候補のクリックだけ。打ちかけが指名を
    // 書き換える経路がそもそも無いことを固定する
    renderModal(nominatedRequest);

    await openPicker();
    await search('み');
    fireEvent.keyDown(screen.getByPlaceholderText('名前で検索'), { key: 'Escape' });
    await save();

    expect(mockedUpdate).toHaveBeenCalledWith('o1', expect.objectContaining({ cast_id: 'cast-1' }));
  });

  it('絞り込んだだけで選ばずに閉じても、指名なしの申請は指名なしのまま保存できる', async () => {
    renderModal(nominationFreeRequest);

    await openPicker();
    await search('み');
    fireEvent.keyDown(screen.getByPlaceholderText('名前で検索'), { key: 'Escape' });
    await save();

    expect(mockedUpdate).toHaveBeenCalledWith(
      'o1',
      expect.objectContaining({ cast_id: undefined })
    );
  });

  it('打ちかけの検索語は次に開いたとき残っていない', async () => {
    // 残ると、表示中の指名と食い違う候補が出たままになる
    renderModal(nominatedRequest);

    await openPicker();
    await search('み');
    fireEvent.keyDown(screen.getByPlaceholderText('名前で検索'), { key: 'Escape' });
    await openPicker();

    expect(screen.getByPlaceholderText('名前で検索')).toHaveValue('');
  });

  it('この場で立てた指名も、解除の明示操作で取り消せる', async () => {
    // 「指名を外す」を元から指名のある申請だけに出すと、今立てた指名を戻す手段が無くなる
    renderModal(nominationFreeRequest);

    expect(screen.queryByRole('checkbox', { name: '指名を外す' })).not.toBeInTheDocument();
    await openPicker();
    const option = screen.getByRole('option', { name: /みか/ });
    // Base UI の Item は pointerdown を経ていない mouse click を無視する
    fireEvent.pointerDown(option);
    fireEvent.click(option);

    fireEvent.click(await screen.findByRole('checkbox', { name: '指名を外す' }));
    await save();

    expect(mockedUpdate).toHaveBeenCalledWith(
      'o1',
      expect.objectContaining({ cast_id: undefined })
    );
  });

  it('指名を外す操作は、別のキャストを選んだ後でも解除として優先する', async () => {
    renderModal(nominatedRequest);

    await openPicker();
    const option = screen.getByRole('option', { name: /みか/ });
    // Base UI の Item は pointerdown を経ていない mouse click を無視する
    fireEvent.pointerDown(option);
    fireEvent.click(option);
    fireEvent.click(screen.getByRole('checkbox', { name: '指名を外す' }));
    await save();

    expect(mockedUpdate).toHaveBeenCalledWith(
      'o1',
      expect.objectContaining({ cast_id: undefined })
    );
  });

  it('指名を外している間は選択を開けない', async () => {
    renderModal(nominatedRequest);

    fireEvent.click(screen.getByRole('checkbox', { name: '指名を外す' }));

    expect(screen.getByRole('combobox', { name: '指名' })).toBeDisabled();
  });

  it('古い検索の応答が遅れて届いても、今の候補を上書きしない', async () => {
    // 片付けは飛んだ後の通信を止められない。遅れて着いた古い応答をそのまま入れると、
    // 今の検索語とは無関係なキャストが選べてしまう
    let landStaleResponse = () => {};
    mockedCastCandidates.mockImplementationOnce(
      () =>
        new Promise(resolve => {
          landStaleResponse = () => resolve([{ id: 'cast-9', name: 'ふるい' }]);
        })
    );
    renderModal(nominationFreeRequest);

    await openPicker();
    await search('み');
    landStaleResponse();
    await act(async () => {});

    expect(screen.getByRole('option', { name: /みか/ })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: /ふるい/ })).not.toBeInTheDocument();
  });

  it('候補の取得に失敗したら、古い候補を残さず候補欄自身が失敗を名乗る', async () => {
    renderModal(nominationFreeRequest);

    await openPicker();
    expect(screen.getByRole('option', { name: /みか/ })).toBeInTheDocument();

    mockedCastCandidates.mockRejectedValueOnce(new Error('boom'));
    await search('あ');

    expect(screen.queryByRole('option', { name: /みか/ })).not.toBeInTheDocument();
    // 空の候補一覧を「該当するキャストがいません」として見せない。横断幕も飛ばさない
    const region = screen.getByRole('alert');
    expect(within(region).getByText('キャスト候補の取得に失敗しました')).toBeInTheDocument();
    expect(screen.queryByText('該当するキャストがいません')).not.toBeInTheDocument();
    expect(notify.error).not.toHaveBeenCalled();

    // 再試行は同じ検索語のまま取り直す
    mockedCastCandidates.mockResolvedValueOnce([{ id: 'cast-3', name: 'あやか' }]);
    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));
    await act(async () => {
      jest.advanceTimersByTime(300);
    });

    expect(screen.getByRole('option', { name: /あやか/ })).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
