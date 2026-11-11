import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { customerApi, MergePreview, MergeProfile } from '@/entities/customer';
import { useMe } from '@/entities/user';
import type { PermissionCode } from '@/entities/user';
import { notify } from '@/shared/notify';
import CustomerMergePage from '../ui/CustomerMergePage';

jest.mock('@/entities/user', () => ({
  ...jest.requireActual('@/entities/user'),
  useCurrentStoreId: () => '1',
}));
jest.mock('@/entities/customer', () => ({
  customerApi: { mergePreview: jest.fn(), merge: jest.fn() },
}));
jest.mock('@/entities/user/model/MeContext', () => ({
  ...jest.requireActual('@/entities/user/model/MeContext'),
  useMe: jest.fn(),
}));
jest.mock('@/shared/notify', () => ({ notify: { success: jest.fn(), error: jest.fn() } }));

const mockReplace = jest.fn();
let mockQuery = 'surviving=a&merged=b';
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn(), replace: mockReplace, back: jest.fn() }),
  useSearchParams: () => new URLSearchParams(mockQuery),
}));

const api = customerApi as jest.Mocked<typeof customerApi>;
const mockedUseMe = useMe as jest.MockedFunction<typeof useMe>;
const meWith = (permissions: PermissionCode[]) =>
  mockedUseMe.mockReturnValue({
    me: {
      email: 'staff@example.com',
      user_type: 'STAFF',
      permissions,
      line_linked: false,
      email_verified: false,
    },
    isLoading: false,
    failure: null,
    reload: jest.fn(),
    setMe: jest.fn(),
  });

const ACKNOWLEDGE = '双方の注意事項・確定資料・優先指定・会員への影響を確認しました';
const profile: MergeProfile = {
  name: '存続',
  address: null,
  building_name: null,
  landmark: null,
  classification: null,
  has_pet: null,
  usage_areas: null,
  ng_type: null,
  ng_content: '犬に注意',
};
const preview: MergePreview = {
  surviving: { id: 'a', profile, contacts: [], member_links: [] },
  merged: { id: 'b', profile: { ...profile, name: '被統合' }, contacts: [], member_links: [] },
  profile,
  preferred_contacts: { phone: null, email: null, line: null },
  preference_conflicts: [],
  member_linked: false,
  unfinished_order_count: 2,
  moved_order_count: 1,
  moved_contact_count: 0,
  moved_link_count: 0,
  preview_token: 'proof',
};

beforeEach(() => {
  jest.clearAllMocks();
  mockQuery = 'surviving=a&merged=b';
  meWith(['CUSTOMER_MANAGE', 'CUSTOMER_MERGE']);
  api.mergePreview.mockResolvedValue(preview);
});

/** 理由を入れてプレビューし、確認のチェックを入れるところまで進める。 */
async function reviewAndAcknowledge(reason = '同一人物を確認') {
  fireEvent.change(await screen.findByLabelText('統合理由'), { target: { value: reason } });
  fireEvent.click(screen.getByRole('button', { name: '確定資料でプレビュー' }));
  const checkbox = screen.getByRole('checkbox', { name: ACKNOWLEDGE });
  await waitFor(() => expect(checkbox).not.toHaveAttribute('aria-disabled', 'true'));
  fireEvent.click(checkbox);
  return checkbox;
}

async function confirmMerge() {
  fireEvent.click(screen.getByRole('button', { name: '統合する' }));
  fireEvent.click(await screen.findByRole('button', { name: '統合を確定' }));
}

it('URL の surviving を存続側として読み込み、離れても何も確定しないと示す', async () => {
  render(<CustomerMergePage />);

  expect(await screen.findByRole('heading', { level: 1, name: '顧客の統合' })).toBeInTheDocument();
  expect(api.mergePreview).toHaveBeenCalledWith('a', { merged_customer_id: 'b' });
  expect(await screen.findByText('存続側の原資料')).toBeInTheDocument();
  expect(screen.getByText(/途中で画面を離れても、何も確定しません/)).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '統合せずに戻る' })).toHaveAttribute(
    'href',
    '/store/1/customers'
  );
});

it.each([
  ['merged が無い', 'surviving=a'],
  ['surviving が無い', 'merged=b'],
  ['同じ顧客を指す', 'surviving=a&merged=a'],
])('統合する 2 件を URL で名指せない場合は読み込まず一覧へ帰す: %s', async (_, query) => {
  mockQuery = query;
  render(<CustomerMergePage />);

  expect(await screen.findByRole('alert')).toHaveTextContent('統合する 2 件の顧客');
  expect(screen.getByRole('link', { name: '顧客一覧へ' })).toHaveAttribute(
    'href',
    '/store/1/customers'
  );
  expect(api.mergePreview).not.toHaveBeenCalled();
});

it('統合の権限が無ければ読み込まない', async () => {
  meWith(['CUSTOMER_MANAGE']);
  render(<CustomerMergePage />);

  expect(await screen.findByRole('alert')).toHaveTextContent('顧客の管理と統合の権限が必要です。');
  expect(api.mergePreview).not.toHaveBeenCalled();
});

it('対象が見つからなければ再試行や統合を出さず一覧へ帰す', async () => {
  api.mergePreview.mockRejectedValueOnce({ response: { status: 404 } });
  render(<CustomerMergePage />);

  expect(await screen.findByRole('alert')).toHaveTextContent('顧客が見つかりません');
  expect(screen.queryByRole('button', { name: '再試行' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '統合する' })).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: '顧客一覧へ' })).toHaveAttribute(
    'href',
    '/store/1/customers'
  );
});

it('再確認時の 404 も編集フォームを一覧への案内に置き換える', async () => {
  render(<CustomerMergePage />);
  fireEvent.change(await screen.findByLabelText('統合理由'), { target: { value: '重複確認' } });
  api.mergePreview.mockRejectedValueOnce({ response: { status: 404 } });
  fireEvent.click(screen.getByRole('button', { name: '確定資料でプレビュー' }));

  expect(await screen.findByRole('alert')).toHaveTextContent('顧客が見つかりません');
  expect(screen.queryByLabelText('統合理由')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '再試行' })).not.toBeInTheDocument();
});

it('再確認と再取得の失敗では旧資料を隠し、入力を保って再試行できる', async () => {
  render(<CustomerMergePage />);
  fireEvent.change(await screen.findByLabelText('統合理由'), { target: { value: '重複確認' } });
  fireEvent.change(screen.getByLabelText('統合後の氏名'), { target: { value: '編集中の氏名' } });
  api.mergePreview.mockRejectedValueOnce(new Error('offline'));
  fireEvent.click(screen.getByRole('button', { name: '確定資料でプレビュー' }));
  await screen.findByRole('alert');
  expect(screen.queryByText('存続側の原資料')).not.toBeInTheDocument();
  expect(screen.queryByText('移る受注')).not.toBeInTheDocument();
  expect(screen.getByLabelText('統合後の氏名')).toHaveValue('編集中の氏名');
  expect(screen.getByLabelText('統合理由')).toHaveValue('重複確認');
  expect(screen.getByRole('button', { name: '統合する' })).toBeDisabled();
  api.mergePreview.mockRejectedValueOnce(new Error('offline'));
  fireEvent.click(screen.getByRole('button', { name: '再試行' }));
  await screen.findByRole('alert');
  expect(screen.queryByText('存続側の原資料')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '再試行' }));
  expect(await screen.findByText('存続側の原資料')).toBeInTheDocument();
  expect(screen.getByLabelText('統合後の氏名')).toHaveValue('編集中の氏名');
  expect(screen.getByRole('button', { name: '統合する' })).toBeDisabled();
});

it('資料と注意事項の確認前は統合を実行できない', async () => {
  render(<CustomerMergePage />);
  expect(await screen.findByRole('button', { name: '統合する' })).toBeDisabled();
  expect(screen.getByText(/この内容での影響を確かめてください/)).toBeInTheDocument();
  expect(screen.getByRole('checkbox', { name: ACKNOWLEDGE })).toHaveAttribute(
    'aria-disabled',
    'true'
  );
});

it('確定ボタンの直前に、移る受注・連絡先・関連とポイントへの影響を並べる', async () => {
  render(<CustomerMergePage />);
  const impact = (await screen.findByRole('heading', { level: 2, name: '統合の影響' })).closest(
    'section'
  )!;

  expect(within(impact).getByText('移る受注').parentElement).toHaveTextContent('1 件');
  expect(within(impact).getByText('影響する未完了受注（両顧客）').parentElement).toHaveTextContent(
    '2 件'
  );
  expect(within(impact).getByText('移る連絡先')).toBeInTheDocument();
  expect(within(impact).getByText('移る会員関連')).toBeInTheDocument();
  expect(within(impact).getByText('ポイント残高（全体）')).toBeInTheDocument();
  expect(within(impact).getByText(/統合は取り消せません/)).toBeInTheDocument();
});

it('項目ごとに被統合側の値を採用し、理由・プレビュー・確認を経て統合して一覧へ戻る', async () => {
  api.merge.mockResolvedValue({
    merge_id: 'm',
    surviving_customer_id: 'a',
    moved_order_count: 1,
    moved_contact_count: 0,
    moved_link_count: 0,
  });
  render(<CustomerMergePage />);
  fireEvent.click(await screen.findByRole('button', { name: '被統合側の氏名を採用' }));
  expect(screen.getByLabelText('統合後の氏名')).toHaveValue('被統合');
  await reviewAndAcknowledge();
  expect(screen.queryByText(/この内容での影響を確かめてください/)).not.toBeInTheDocument();
  // 「統合する」は確認を開くだけ。ここで走ってしまうと取り返しがつかない
  fireEvent.click(screen.getByRole('button', { name: '統合する' }));
  expect(api.merge).not.toHaveBeenCalled();
  fireEvent.click(await screen.findByRole('button', { name: '統合を確定' }));

  await waitFor(() =>
    expect(api.merge).toHaveBeenCalledWith(
      'a',
      expect.objectContaining({
        merged_customer_id: 'b',
        profile: { ...profile, name: '被統合' },
        warnings_acknowledged: true,
        operation_reason: '同一人物を確認',
        preview_token: 'proof',
      })
    )
  );
  expect(notify.success).toHaveBeenCalledWith('顧客を統合しました');
  expect(mockReplace).toHaveBeenCalledWith('/store/1/customers');
});

it('競合後も入力を保持し、再確認するまで実行を止める', async () => {
  api.merge.mockRejectedValue({
    response: { status: 409, data: { error: '関連情報が変更されました' } },
  });
  render(<CustomerMergePage />);
  await reviewAndAcknowledge('重複確認');
  await confirmMerge();

  expect(await screen.findByText('関連情報が変更されました')).toBeInTheDocument();
  expect(screen.getByLabelText('統合理由')).toHaveValue('重複確認');
  expect(screen.getByRole('button', { name: '統合する' })).toBeDisabled();
  expect(mockReplace).not.toHaveBeenCalled();
});

it('両行が会員に紐づいている拒否は、先に関連を解除すると読める形で画面に出す', async () => {
  const guidance = '両方の顧客に会員が紐づいています。先に関連を解除してから統合してください';
  api.merge.mockRejectedValue({ response: { status: 409, data: { error: guidance } } });
  render(<CustomerMergePage />);
  await reviewAndAcknowledge();
  await confirmMerge();

  // サーバの案内を汎用文言へ潰すと、次の一手が画面から判らなくなる
  expect(await screen.findByText(guidance)).toBeInTheDocument();
});

it('プレビュー後に理由を消した場合は入力欄で訂正を求める', async () => {
  render(<CustomerMergePage />);
  const checkbox = await reviewAndAcknowledge('確認済み');
  fireEvent.change(screen.getByLabelText('統合理由'), { target: { value: '  ' } });
  fireEvent.click(screen.getByRole('button', { name: '統合する' }));

  expect(await screen.findByText('統合理由を入力してください')).toBeInTheDocument();
  expect(api.merge).not.toHaveBeenCalled();
  expect(checkbox).toHaveAttribute('aria-checked', 'true');
});

it('選択欄は内部値ではなく日本語のラベルを表示する', async () => {
  render(<CustomerMergePage />);
  expect(await screen.findByRole('combobox', { name: 'ペットの有無' })).toHaveTextContent('未確認');
  expect(screen.getByRole('combobox', { name: 'メールの優先連絡先' })).toHaveTextContent(
    '指定なし'
  );
});

it('優先連絡先の競合は送信時に関連付けたエラーと最初の欄へのフォーカスで示す', async () => {
  api.mergePreview.mockResolvedValueOnce({
    ...preview,
    preference_conflicts: ['PHONE', 'EMAIL'],
    preview_token: undefined,
  });
  render(<CustomerMergePage />);
  fireEvent.change(await screen.findByLabelText('統合理由'), { target: { value: '確認済み' } });
  const submit = screen.getByRole('button', { name: '確定資料でプレビュー' });
  expect(submit).toBeEnabled();
  fireEvent.click(submit);
  const phone = screen.getByRole('combobox', { name: '電話の優先連絡先' });
  await waitFor(() => expect(phone).toHaveFocus());
  expect(phone).toHaveAttribute('aria-invalid', 'true');
  expect(phone).toHaveAccessibleDescription('優先連絡先を一件、または指定なしを選択してください');
  expect(screen.getByRole('combobox', { name: 'メールの優先連絡先' })).toHaveAttribute(
    'aria-invalid',
    'true'
  );
  expect(api.mergePreview).toHaveBeenCalledTimes(1);
  for (const name of ['電話の優先連絡先', 'メールの優先連絡先']) {
    fireEvent.click(screen.getByRole('combobox', { name }));
    const option = await screen.findByRole('option', { name: '指定なし' });
    fireEvent.pointerDown(option);
    fireEvent.click(option);
    await waitFor(() =>
      expect(screen.queryByRole('option', { name: '指定なし' })).not.toBeInTheDocument()
    );
  }
  fireEvent.click(submit);
  await waitFor(() => expect(api.mergePreview).toHaveBeenCalledTimes(2));
  expect(api.mergePreview).toHaveBeenLastCalledWith(
    'a',
    expect.objectContaining({ preferred_contacts: { phone: null, email: null, line: null } })
  );
});
