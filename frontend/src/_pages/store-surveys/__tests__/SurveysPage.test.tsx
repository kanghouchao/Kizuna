import { act, fireEvent, render, screen } from '@testing-library/react';
import { SurveysPage } from '../ui/SurveysPage';
import { useMe } from '@/entities/user';
import { api, fixtures, permission, resetSurveyTest } from '../testing/survey-test-support';

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
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, replace: jest.fn(), back: jest.fn() }),
}));
const mockedUseMe = jest.mocked(useMe);

beforeEach(resetSurveyTest);

test('一覧の行は最新版の詳細へのリンクを持つ', async () => {
  fixtures();
  render(<SurveysPage />);
  expect(await screen.findByRole('link', { name: '版と回答' })).toHaveAttribute(
    'href',
    '/store/1/surveys/10/revisions/20'
  );
});

test('管理権限があれば作成画面へのリンクを出す', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_MANAGE');
  render(<SurveysPage />);
  expect(await screen.findByRole('link', { name: 'アンケートを作成' })).toHaveAttribute(
    'href',
    '/store/1/surveys/create'
  );
});

test('閲覧権限なしでは取得せず、閲覧のみでは設問作成を表示しない', async () => {
  permission('SURVEY_MANAGE');
  const view = render(<SurveysPage />);
  expect(screen.getByRole('alert')).toHaveTextContent('権限の無い画面');
  expect(api.list).not.toHaveBeenCalled();
  permission('SURVEY_VIEW');
  view.rerender(<SurveysPage />);
  expect(await screen.findByText('アンケートはありません')).toBeVisible();
  expect(screen.queryByRole('link', { name: 'アンケートを作成' })).not.toBeInTheDocument();
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
  expect(screen.queryByRole('link', { name: 'アンケートを作成' })).not.toBeInTheDocument();
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
  expect(screen.queryByRole('link', { name: 'アンケートを作成' })).not.toBeInTheDocument();
  expect(api.list).not.toHaveBeenCalled();
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
