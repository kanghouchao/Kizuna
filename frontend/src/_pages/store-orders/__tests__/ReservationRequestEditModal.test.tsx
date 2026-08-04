import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { toast } from 'react-hot-toast';
import { ReservationRequestEditModal } from '../ui/ReservationRequestEditModal';
import { Order, orderApi } from '@/entities/order';
import { castApi } from '@/entities/cast';

jest.mock('@/entities/order', () => ({
  orderApi: { listReceptionists: jest.fn(), updateReservationRequest: jest.fn() },
}));

jest.mock('@/entities/cast', () => ({
  castApi: { get: jest.fn(), list: jest.fn() },
}));

jest.mock('react-hot-toast', () => ({
  toast: { success: jest.fn(), error: jest.fn() },
}));

const mockedUpdate = orderApi.updateReservationRequest as jest.Mock;
const mockedReceptionists = orderApi.listReceptionists as jest.Mock;
const mockedCastList = castApi.list as jest.Mock;

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
    expect(await screen.findByRole('combobox', { name: '指名' })).toHaveValue('');
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
      expect(toast.error).toHaveBeenCalledWith(
        '指名できるキャストではありません。在籍中のキャストを選ぶか、指名を外してください'
      )
    );
    expect(onSaved).not.toHaveBeenCalled();
  });
});

describe('ReservationRequestEditModal の指名の差し替え', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.useFakeTimers();
    mockedReceptionists.mockResolvedValue([]);
    mockedUpdate.mockResolvedValue({});
    mockedCastList.mockResolvedValue({
      rows: [{ id: 'cast-2', name: 'みか' }],
      page: 0,
      pageCount: 1,
      total: 1,
    });
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  /** 入力→デバウンス経過→候補描画までを進める。 */
  const openSuggestions = async (keyword: string) => {
    fireEvent.change(screen.getByRole('combobox', { name: '指名' }), {
      target: { value: keyword },
    });
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

  it('候補と同じ名前を打ち切ってから選んでも、次の検索が止まらない', async () => {
    // 打った文字列と選んだ名前が同じだと入力欄の値が動かない。検索の抑止を「値が変わったか」に
    // 頼ると、この一手で抑止が解除されないまま次の入力を飲み込む
    renderModal(nominationFreeRequest);

    await openSuggestions('みか');
    fireEvent.click(screen.getByRole('option', { name: /みか/ }));
    await openSuggestions('あ');

    expect(mockedCastList).toHaveBeenLastCalledWith(expect.objectContaining({ search: 'あ' }));
  });

  it('古い検索の応答が遅れて届いても、今の候補を上書きしない', async () => {
    // デバウンスの片付けは飛んだ後の通信を止められない。遅れて着いた古い応答をそのまま入れると、
    // 今の入力とは無関係なキャストが選べてしまう
    let landStaleResponse = () => {};
    mockedCastList.mockImplementationOnce(
      () =>
        new Promise(resolve => {
          landStaleResponse = () =>
            resolve({ rows: [{ id: 'cast-9', name: 'ふるい' }], page: 0, pageCount: 1, total: 1 });
        })
    );
    renderModal(nominationFreeRequest);

    await openSuggestions('ふ');
    await openSuggestions('み');
    landStaleResponse();
    await act(async () => {});

    expect(screen.getByRole('option', { name: /みか/ })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: /ふるい/ })).not.toBeInTheDocument();
  });

  it('指名済みの申請を、別のキャストへ差し替えて保存できる', async () => {
    renderModal(nominatedRequest);

    expect(screen.getByRole('combobox', { name: '指名' })).toHaveValue('あや');
    await openSuggestions('み');
    fireEvent.click(screen.getByRole('option', { name: /みか/ }));
    await save();

    expect(mockedUpdate).toHaveBeenCalledWith('o1', expect.objectContaining({ cast_id: 'cast-2' }));
  });

  it('指名なしの申請に、キャストを立てて保存できる', async () => {
    renderModal(nominationFreeRequest);

    await openSuggestions('み');
    fireEvent.click(screen.getByRole('option', { name: /みか/ }));
    await save();

    expect(mockedUpdate).toHaveBeenCalledWith('o1', expect.objectContaining({ cast_id: 'cast-2' }));
  });

  it('候補から選ばずに名前を打っただけでは保存させず、外すなら明示するよう促す', async () => {
    // 契約は部分更新ではないので、打ちかけのまま送ると指名が消える。解除は明示操作だけの権能
    renderModal(nominatedRequest);

    await openSuggestions('み');
    await save();

    expect(mockedUpdate).not.toHaveBeenCalled();
    expect(
      await screen.findByText(
        '指名を変えるときは候補から選んでください。外す場合は「指名を外す」にチェックしてください'
      )
    ).toBeInTheDocument();
  });

  it('指名なしの申請でも、打ちかけのままなら保存させない', async () => {
    // 消える指名は無いが、名前を打った以上は指名するつもり。黙って指名なしで保存すると気付けない
    renderModal(nominationFreeRequest);

    await openSuggestions('み');
    await save();

    expect(mockedUpdate).not.toHaveBeenCalled();
  });

  it('指名なしの申請は、打ちかけを消せばそのまま指名なしで保存できる', async () => {
    // 立てかけたキャストを取り消す唯一の経路。空欄まで止めると指名なしへ戻せなくなる
    renderModal(nominationFreeRequest);

    await openSuggestions('み');
    await openSuggestions('');
    await save();

    expect(mockedUpdate).toHaveBeenCalledWith(
      'o1',
      expect.objectContaining({ cast_id: undefined })
    );
  });

  it('指名済みの申請は、名前を消しただけでは指名を落とさない', async () => {
    // 空欄は「外す」ではない。契約は部分更新ではないので、通すと黙って指名が消える
    renderModal(nominatedRequest);

    await openSuggestions('');
    await save();

    expect(mockedUpdate).not.toHaveBeenCalled();
  });

  it('打ちかけを候補の選択で決着させれば保存できる', async () => {
    renderModal(nominatedRequest);

    await openSuggestions('み');
    await save();
    fireEvent.click(screen.getByRole('option', { name: /みか/ }));
    await save();

    expect(mockedUpdate).toHaveBeenCalledWith('o1', expect.objectContaining({ cast_id: 'cast-2' }));
  });

  it('指名を外す操作は、別のキャストを選んだ後でも解除として優先する', async () => {
    renderModal(nominatedRequest);

    await openSuggestions('み');
    fireEvent.click(screen.getByRole('option', { name: /みか/ }));
    fireEvent.click(screen.getByRole('checkbox', { name: '指名を外す' }));
    await save();

    expect(mockedUpdate).toHaveBeenCalledWith(
      'o1',
      expect.objectContaining({ cast_id: undefined })
    );
  });
});
