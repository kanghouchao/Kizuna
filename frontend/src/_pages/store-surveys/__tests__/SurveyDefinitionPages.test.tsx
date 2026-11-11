import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { SurveyCreatePage } from '../ui/SurveyCreatePage';
import { SurveyReplacePage, SurveyRevisePage } from '../ui/SurveyRevisionEditPages';
import { SurveysPage } from '../ui/SurveysPage';
import {
  api,
  fixtures,
  permission,
  resetSurveyTest,
  revision,
  survey,
  written,
} from '../testing/survey-test-support';

jest.mock('@/widgets/admin-shell', () => ({
  DeniedPage: () => <p role="alert">権限の無い画面</p>,
}));

jest.mock('@/entities/user', () => ({
  ...jest.requireActual('@/entities/user'),
  useCurrentStoreId: () => '1',
  useStoreContext: () => ({
    stores: [{ id: 1, name: 'テスト店舗' }],
    currentStoreId: '1',
    currentStore: { id: 1, name: 'テスト店舗' },
    loadFailed: false,
    reload: jest.fn(),
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
const mockReplace = jest.fn();
let mockSearch = new URLSearchParams();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, replace: mockReplace, back: jest.fn() }),
  useParams: () => ({ sid: '10', rid: '20' }),
  useSearchParams: () => mockSearch,
}));

beforeEach(() => {
  resetSurveyTest();
  mockSearch = new URLSearchParams();
});

async function fillCreate() {
  fireEvent.change(await screen.findByLabelText('題名'), { target: { value: '新しい問票' } });
  fireEvent.change(screen.getByLabelText('設問文'), { target: { value: 'スタッフ入力の設問' } });
}

describe('作成', () => {
  test('管理権限が無ければ入力を出さない', () => {
    permission('SURVEY_VIEW');
    render(<SurveyCreatePage />);
    expect(screen.getByRole('alert')).toHaveTextContent('権限の無い画面');
    expect(screen.queryByLabelText('題名')).not.toBeInTheDocument();
  });

  test('画面の見出しと一覧へ戻るリンクを持つ', async () => {
    permission('SURVEY_VIEW', 'SURVEY_MANAGE');
    render(<SurveyCreatePage />);
    expect(screen.getByRole('heading', { level: 1, name: 'アンケートを作成' })).toBeVisible();
    expect(screen.getByRole('link', { name: 'アンケート一覧へ' })).toHaveAttribute(
      'href',
      '/store/1/surveys'
    );
  });

  test('任意の単一選択は空から選択でき、空の質問票は保存せず、保存したら版の詳細へ置き換えて移る', async () => {
    permission('SURVEY_VIEW', 'SURVEY_MANAGE');
    render(<SurveyCreatePage />);
    fireEvent.click(await screen.findByRole('button', { name: '下書きを保存' }));
    expect(await screen.findByText('題名を1〜120文字で入力してください')).toBeVisible();
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
    api.write.mockResolvedValue(written('DRAFT_CREATED', { revision }));
    fireEvent.click(screen.getByRole('button', { name: '下書きを保存' }));
    await waitFor(() =>
      expect(mockReplace).toHaveBeenCalledWith('/store/1/surveys/10/revisions/20')
    );
    expect(api.write.mock.calls[0][0]).toMatchObject({
      kind: 'CREATE',
      input: {
        questions: [{ type: 'SINGLE_CHOICE', options: [{ label: '一つ目' }, { label: '二つ目' }] }],
      },
    });
    expect(mockPush).not.toHaveBeenCalled();
  });

  test('結果不明では入力を保ったまま新しい保存を止め、同じ要求で確かめて版の詳細へ移る', async () => {
    permission('SURVEY_VIEW', 'SURVEY_MANAGE');
    api.write
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce(written('DRAFT_CREATED', { revision }));
    render(<SurveyCreatePage />);
    await fillCreate();
    fireEvent.click(screen.getByRole('button', { name: '下書きを保存' }));
    await screen.findByText(/結果が確認できません。同じ要求/);
    expect(screen.getByLabelText('題名')).toHaveValue('新しい問票');
    expect(screen.getByRole('button', { name: '下書きを保存' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '同じ要求で結果を確認' }));
    await waitFor(() => expect(api.write).toHaveBeenCalledTimes(2));
    expect(api.write.mock.calls[1][0]).toEqual(api.write.mock.calls[0][0]);
    expect(api.write.mock.calls[1][1]).toBe(api.write.mock.calls[0][1]);
    expect(api.write.mock.calls[0][2]).toEqual({ headers: { 'X-Store-ID': '1' } });
    await waitFor(() =>
      expect(mockReplace).toHaveBeenCalledWith('/store/1/surveys/10/revisions/20')
    );
  });

  test('画面を離れても結果不明の操作は残り、一覧から同じ要求で確かめる', async () => {
    fixtures();
    permission('SURVEY_VIEW', 'SURVEY_MANAGE');
    api.write
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce(written('DRAFT_CREATED', { revision }));
    const first = render(<SurveyCreatePage />);
    await fillCreate();
    fireEvent.click(screen.getByRole('button', { name: '下書きを保存' }));
    await screen.findByText(/結果が確認できません。同じ要求/);
    first.unmount();
    render(<SurveysPage />);
    expect(await screen.findByText(/結果が確認できません。同じ要求/)).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: '同じ要求で結果を確認' }));
    await waitFor(() => expect(api.write).toHaveBeenCalledTimes(2));
    expect(api.write.mock.calls[1][0]).toEqual(api.write.mock.calls[0][0]);
    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/store/1/surveys/10/revisions/20'));
  });
});

describe('差替', () => {
  const draft = { ...revision, status: 'DRAFT' as const, version: 3 };

  test('下書きの内容を入れて開き、版の番号を付けて差し替え、版の詳細へ戻る', async () => {
    fixtures();
    permission('SURVEY_VIEW', 'SURVEY_MANAGE');
    api.revision.mockResolvedValue(draft);
    api.write.mockResolvedValue(written('DRAFT_REPLACED', { revision: draft }));
    render(<SurveyReplacePage />);
    expect(await screen.findByRole('heading', { level: 1, name: '下書きを編集' })).toBeVisible();
    expect(screen.getByRole('link', { name: '設問版へ戻る' })).toHaveAttribute(
      'href',
      '/store/1/surveys/10/revisions/20'
    );
    expect(await screen.findByLabelText('題名')).toHaveValue('店舗の問票');
    expect(screen.getByLabelText('設問文')).toHaveValue('担当者が入力した質問');
    fireEvent.change(screen.getByLabelText('題名'), { target: { value: '直した題名' } });
    fireEvent.click(screen.getByRole('button', { name: '下書きを保存' }));
    await waitFor(() => expect(api.write).toHaveBeenCalledTimes(1));
    expect(api.write.mock.calls[0][0]).toMatchObject({
      kind: 'REPLACE',
      sid: '10',
      rid: '20',
      input: { title: '直した題名', version: 3 },
    });
    await waitFor(() =>
      expect(mockReplace).toHaveBeenCalledWith('/store/1/surveys/10/revisions/20')
    );
  });

  test('下書きでない版では入力を出さない', async () => {
    fixtures();
    permission('SURVEY_VIEW', 'SURVEY_MANAGE');
    render(<SurveyReplacePage />);
    expect(await screen.findByText(/下書きではないため編集できません/)).toBeVisible();
    expect(screen.queryByLabelText('題名')).not.toBeInTheDocument();
  });

  test('409 の後に読み直して受付が始まっていたら、入力を保ったまま保存を止める', async () => {
    fixtures();
    permission('SURVEY_VIEW', 'SURVEY_MANAGE');
    api.revision.mockResolvedValue(draft);
    api.write.mockRejectedValue({ response: { status: 409 } });
    render(<SurveyReplacePage />);
    fireEvent.change(await screen.findByLabelText('題名'), { target: { value: '保持する題名' } });
    fireEvent.click(screen.getByRole('button', { name: '下書きを保存' }));
    await screen.findByText(/状態が更新されています/);
    api.revision.mockResolvedValue({ ...draft, status: 'OPEN', version: 4 });
    fireEvent.click(screen.getByRole('button', { name: '最新状態を確認' }));
    await screen.findByText(/現在の状態ではこの操作を実行できません/);
    expect(screen.getByLabelText('題名')).toHaveValue('保持する題名');
    expect(screen.getByRole('button', { name: '下書きを保存' })).toBeDisabled();
    expect(api.write).toHaveBeenCalledTimes(1);
  });
});

describe('改訂', () => {
  beforeEach(() => {
    mockSearch = new URLSearchParams({ from: '20' });
  });

  test('改版元の内容を入れて開き、改版元を付けて新しい版を作る', async () => {
    fixtures();
    permission('SURVEY_VIEW', 'SURVEY_MANAGE');
    api.write.mockResolvedValue(
      written('DRAFT_CREATED', { revision: { ...revision, id: '22', status: 'DRAFT' } })
    );
    render(<SurveyRevisePage />);
    expect(await screen.findByRole('heading', { level: 1, name: '新しい版を作成' })).toBeVisible();
    expect(screen.getByRole('link', { name: '設問版へ戻る' })).toHaveAttribute(
      'href',
      '/store/1/surveys/10/revisions/20'
    );
    expect(await screen.findByLabelText('題名')).toHaveValue('店舗の問票');
    fireEvent.click(screen.getByRole('button', { name: '下書きを保存' }));
    await waitFor(() => expect(api.write).toHaveBeenCalledTimes(1));
    expect(api.write.mock.calls[0][0]).toMatchObject({
      kind: 'REVISE',
      sid: '10',
      input: { based_on_revision_id: '20', title: '店舗の問票' },
    });
    await waitFor(() =>
      expect(mockReplace).toHaveBeenCalledWith('/store/1/surveys/10/revisions/22')
    );
  });

  test('最新版でない版や下書きが既にある系列からは作らない', async () => {
    fixtures();
    permission('SURVEY_VIEW', 'SURVEY_MANAGE');
    api.survey.mockResolvedValue({ ...survey, draft_revision_id: '21' });
    render(<SurveyRevisePage />);
    expect(await screen.findByText(/この版からは新しい版を作成できません/)).toBeVisible();
    expect(screen.queryByLabelText('題名')).not.toBeInTheDocument();
  });

  test('改版元の指定が無ければ版が見つからない旨を出す', async () => {
    fixtures();
    permission('SURVEY_VIEW', 'SURVEY_MANAGE');
    mockSearch = new URLSearchParams();
    render(<SurveyRevisePage />);
    expect(await screen.findByText('設問版が見つかりません')).toBeVisible();
    expect(api.revision).not.toHaveBeenCalled();
  });

  test('改版元404でも親アンケートが存在すればフォームを保持する', async () => {
    fixtures();
    permission('SURVEY_VIEW', 'SURVEY_MANAGE');
    api.write.mockRejectedValue({ response: { status: 404 } });
    render(<SurveyRevisePage />);
    fireEvent.change(await screen.findByLabelText('題名'), { target: { value: '保持する改版案' } });
    fireEvent.click(screen.getByRole('button', { name: '下書きを保存' }));
    await screen.findByText(/対象が見つかりません。入力は保持/);
    api.revision.mockRejectedValue({ response: { status: 404 } });
    fireEvent.click(screen.getByRole('button', { name: '最新状態を確認' }));
    await waitFor(() => expect(api.survey).toHaveBeenCalledTimes(2));
    expect(screen.getByLabelText('題名')).toHaveValue('保持する改版案');
    expect(screen.getByRole('button', { name: '下書きを保存' })).toBeDisabled();
  });

  test('改版競合では最新系列を確認し入力を保って明示的に新基準を選び、新しい版へ移る', async () => {
    fixtures();
    permission('SURVEY_VIEW', 'SURVEY_MANAGE');
    api.write.mockRejectedValueOnce({ response: { status: 409 } });
    render(<SurveyRevisePage />);
    fireEvent.change(await screen.findByLabelText('題名'), { target: { value: '自分の改版案' } });
    fireEvent.click(screen.getByRole('button', { name: '下書きを保存' }));
    await screen.findByText(/状態が更新されています/);
    api.survey.mockResolvedValue({
      ...survey,
      latest_revision_id: '21',
      latest_revision_number: 2,
    });
    api.revision.mockImplementation(async (_sid, rid) => ({
      ...revision,
      id: rid,
      revision_number: rid === '21' ? 2 : 1,
    }));
    fireEvent.click(screen.getByRole('button', { name: '最新状態を確認' }));
    expect(await screen.findByText(/最新版は版 2/)).toBeVisible();
    expect(screen.getByLabelText('題名')).toHaveValue('自分の改版案');
    expect(screen.getByRole('button', { name: '下書きを保存' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: 'この最新版を改版元にする' }));
    api.write.mockResolvedValueOnce(
      written('DRAFT_CREATED', { revision: { ...revision, id: '22', status: 'DRAFT' } })
    );
    fireEvent.click(screen.getByRole('button', { name: '下書きを保存' }));
    await waitFor(() => expect(api.write).toHaveBeenCalledTimes(2));
    expect(api.write.mock.calls[1][0]).toMatchObject({
      input: { based_on_revision_id: '21', title: '自分の改版案' },
    });
    await waitFor(() =>
      expect(mockReplace).toHaveBeenCalledWith('/store/1/surveys/10/revisions/22')
    );
  });

  test('改版元が不在でも系列確認の通信障害で入力を消さない', async () => {
    fixtures();
    permission('SURVEY_VIEW', 'SURVEY_MANAGE');
    api.write.mockRejectedValue({ response: { status: 404 } });
    render(<SurveyRevisePage />);
    fireEvent.change(await screen.findByLabelText('題名'), { target: { value: '消さない改版案' } });
    fireEvent.click(screen.getByRole('button', { name: '下書きを保存' }));
    await screen.findByText(/対象が見つかりません。入力は保持/);
    api.survey.mockRejectedValue(new Error('network'));
    fireEvent.click(screen.getByRole('button', { name: '最新状態を確認' }));
    expect(await screen.findByText(/最新状態を確認できません。入力は保持しています/)).toBeVisible();
    expect(screen.getByLabelText('題名')).toHaveValue('消さない改版案');
  });
});
