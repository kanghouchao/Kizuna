import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { SurveysPage } from '../ui/SurveysPage';
import { useMe } from '@/entities/user';
import {
  api,
  fixtures,
  permission,
  resetSurveyTest,
  revision,
  written,
} from '../testing/survey-test-support';

jest.mock('@/entities/user', () => ({
  ...jest.requireActual('@/entities/user'),
  useStoreContext: () => ({
    stores: [{ id: 1, name: 'テスト店舗' }],
    currentStoreId: '1',
    currentStore: { id: 1, name: 'テスト店舗' },
    loadFailed: false,
    reload: jest.fn(),
    switchStore: jest.fn(),
  }),
}));
jest.mock('@/entities/survey', () => ({
  surveyApi: {
    list: jest.fn(),
    survey: jest.fn(),
    revisions: jest.fn(),
    revision: jest.fn(),
    answers: jest.fn(),
    answer: jest.fn(),
    counts: jest.fn(),
    history: jest.fn(),
    write: jest.fn(),
  },
}));
jest.mock('@/entities/user/model/MeContext', () => ({
  ...jest.requireActual('@/entities/user/model/MeContext'),
  useMe: jest.fn(),
}));
jest.mock('@/shared/notify', () => ({ notify: { success: jest.fn(), error: jest.fn() } }));
const mockPush = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, replace: jest.fn(), back: jest.fn() }),
}));
const mockedUseMe = jest.mocked(useMe);

async function createForm() {
  fireEvent.click(await screen.findByRole('button', { name: 'アンケートを作成' }));
  const dialog = await screen.findByRole('dialog');
  fireEvent.change(within(dialog).getByLabelText('題名'), { target: { value: '新しい問票' } });
  fireEvent.change(within(dialog).getByLabelText('設問文'), {
    target: { value: 'スタッフ入力の設問' },
  });
  return dialog;
}
beforeEach(resetSurveyTest);

test('一覧の行は最新版の詳細へのリンクを持つ', async () => {
  fixtures();
  render(<SurveysPage />);
  expect(await screen.findByRole('link', { name: '版と回答' })).toHaveAttribute(
    'href',
    '/store/surveys/10/revisions/20'
  );
});

test('閲覧権限なしでは取得せず、閲覧のみでは設問作成を表示しない', async () => {
  permission('SURVEY_MANAGE');
  const view = render(<SurveysPage />);
  expect(screen.getByRole('alert')).toHaveTextContent('閲覧権限がありません');
  expect(api.list).not.toHaveBeenCalled();
  permission('SURVEY_VIEW');
  view.rerender(<SurveysPage />);
  expect(await screen.findByText('アンケートはありません')).toBeVisible();
  expect(screen.queryByRole('button', { name: 'アンケートを作成' })).not.toBeInTheDocument();
});

test('本人情報の取得中は読み込み中だけを出し、一覧を取得しない', () => {
  mockedUseMe.mockReturnValue({
    me: null,
    isLoading: true,
    failure: null,
    reload: jest.fn(),
    setMe: jest.fn(),
  });
  render(<SurveysPage />);
  expect(screen.getByRole('status')).toHaveTextContent('読み込み中...');
  expect(screen.queryByText('アンケートはありません')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'アンケートを作成' })).not.toBeInTheDocument();
  expect(api.list).not.toHaveBeenCalled();
});

test('本人情報の取得に失敗したら再試行だけを出し、一覧を取得しない', () => {
  mockedUseMe.mockReturnValue({
    me: null,
    isLoading: false,
    failure: 'error',
    reload: jest.fn(),
    setMe: jest.fn(),
  });
  render(<SurveysPage />);
  expect(screen.getByText('権限を確認できませんでした')).toBeVisible();
  expect(screen.queryByText('アンケートはありません')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'アンケートを作成' })).not.toBeInTheDocument();
  expect(api.list).not.toHaveBeenCalled();
});

test('作成の結果不明を閉じても入力を保持し、新しい操作を止めて再確認へ導く', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_MANAGE');
  api.write.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce({
    revision,
    operation: {
      id: '50',
      type: 'DRAFT_CREATED',
      resource_id: '20',
      committed_version: 0,
      replayed: true,
    },
  });
  render(<SurveysPage />);
  const dialog = await createForm();
  fireEvent.click(within(dialog).getByRole('button', { name: '下書きを保存' }));
  await screen.findByText(/結果が確認できません。同じ要求/);
  fireEvent.click(within(dialog).getByRole('button', { name: 'Close' }));
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  expect(screen.getByRole('button', { name: 'アンケートを作成' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: '入力画面へ戻る' }));
  expect(await screen.findByLabelText('題名')).toHaveValue('新しい問票');
  expect(screen.getByLabelText('設問文')).toHaveValue('スタッフ入力の設問');
  fireEvent.click(screen.getByRole('button', { name: '同じ要求で結果を確認' }));
  await waitFor(() => expect(api.write).toHaveBeenCalledTimes(2));
  expect(api.write.mock.calls[0][2]).toEqual({ headers: { 'X-Store-ID': '1' } });
  await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/store/surveys/10/revisions/20'));
});

test('画面を離れても結果不明の操作は残り、開き直した画面から同じ要求で確かめる', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_MANAGE');
  api.write
    .mockRejectedValueOnce(new Error('network'))
    .mockResolvedValueOnce(written('DRAFT_CREATED', { revision }));
  const first = render(<SurveysPage />);
  const dialog = await createForm();
  fireEvent.click(within(dialog).getByRole('button', { name: '下書きを保存' }));
  await screen.findByText(/結果が確認できません。同じ要求/);
  first.unmount();
  render(<SurveysPage />);
  expect(await screen.findByText(/結果が確認できません。同じ要求/)).toBeVisible();
  expect(screen.getByRole('button', { name: 'アンケートを作成' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: '同じ要求で結果を確認' }));
  await waitFor(() => expect(api.write).toHaveBeenCalledTimes(2));
  expect(api.write.mock.calls[1][0]).toEqual(api.write.mock.calls[0][0]);
  expect(api.write.mock.calls[1][1]).toBe(api.write.mock.calls[0][1]);
  await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/store/surveys/10/revisions/20'));
});

test('遅れて失敗した旧一覧要求で新しい検索結果の権限を消さない', async () => {
  fixtures();
  let rejectOld!: (error: unknown) => void;
  api.list.mockReturnValueOnce(
    new Promise((_, reject) => {
      rejectOld = reject;
    })
  );
  render(<SurveysPage />);
  fireEvent.change(screen.getByLabelText('最新版の題名'), { target: { value: '新しい検索' } });
  fireEvent.click(screen.getByRole('button', { name: '検索' }));
  await screen.findByRole('link', { name: '版と回答' });
  await act(async () => rejectOld({ response: { status: 403 } }));
  expect(screen.getByRole('link', { name: '版と回答' })).toBeVisible();
  expect(screen.queryByText(/現在の閲覧・操作権限/)).not.toBeInTheDocument();
});

test('任意の単一選択は空から選択でき、空の質問票は保存しない', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_MANAGE');
  render(<SurveysPage />);
  fireEvent.click(await screen.findByRole('button', { name: 'アンケートを作成' }));
  fireEvent.click(await screen.findByRole('button', { name: '下書きを保存' }));
  expect(await screen.findByText('題名を1〜120文字で入力してください')).toBeVisible();
  expect(screen.getByLabelText('題名')).toHaveAttribute('aria-invalid', 'true');
  await waitFor(() => expect(screen.getByLabelText('題名')).toHaveFocus());
  expect(api.write).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('題名'), { target: { value: '選択の問票' } });
  fireEvent.change(screen.getByLabelText('設問文'), { target: { value: 'どちらですか' } });
  fireEvent.click(screen.getByLabelText('回答形式'));
  const option = await screen.findByRole('option', { name: '単一選択' });
  fireEvent.pointerDown(option);
  fireEvent.click(option);
  fireEvent.change(await screen.findByLabelText('選択肢 1'), { target: { value: '一つ目' } });
  fireEvent.change(screen.getByLabelText('選択肢 2'), { target: { value: '二つ目' } });
  api.write.mockResolvedValue({
    revision,
    operation: {
      id: '50',
      type: 'DRAFT_CREATED',
      resource_id: '20',
      committed_version: 0,
      replayed: false,
    },
  });
  fireEvent.click(screen.getByRole('button', { name: '下書きを保存' }));
  await waitFor(() => expect(api.write).toHaveBeenCalledTimes(1));
  await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/store/surveys/10/revisions/20'));
  expect(api.write.mock.calls[0][0]).toMatchObject({
    kind: 'CREATE',
    input: {
      questions: [{ type: 'SINGLE_CHOICE', options: [{ label: '一つ目' }, { label: '二つ目' }] }],
    },
  });
});
