import { render, screen, within } from '@testing-library/react';
import { advertisingApi, type RecruitmentEffectReport } from '../api/advertising';
import { RecruitmentEffectPage } from './RecruitmentEffectPage';
jest.mock('../api/advertising', () => ({
  ...jest.requireActual('../api/advertising'),
  advertisingApi: { recruitmentEffects: jest.fn() },
}));
function report(): RecruitmentEffectReport {
  return {
    store_id: 1,
    month: '2026-05',
    month_version: 1,
    generated_at: '2026-07-15T00:00:00Z',
    retention_as_of: '2026-07-15',
    basis: 'recorded-recruitment-cost-per-hire-v1',
    media_matching: 'EXACT_STORED_NAME',
    hire_basis: 'HIRED_APPLICANT_ENROLLMENT_STARTED_IN_MONTH',
    retention_basis: 'NOT_ENDED_AFTER_STARTED_ON_PLUS_MONTHS',
    rounding: 'HALF_UP_2_DECIMAL_YEN',
    cost_entry_count: 2,
    recorded_recruitment_amount: 30500,
    hire_count: 4,
    unnamed_media_hire_count: 1,
    rows: {
      content: [
        {
          media_name: '求人A',
          cost_entry_count: 1,
          recorded_recruitment_amount: 30000,
          hire_count: 2,
          cost_per_hire: '15000.00',
          calculation_status: 'CALCULATED',
          retention: [
            { months: 2, judgeable_count: 2, retained_count: 1 },
            { months: 3, judgeable_count: 0, retained_count: 0 },
            { months: 4, judgeable_count: 0, retained_count: 0 },
          ],
        },
        {
          media_name: '入店だけ',
          cost_entry_count: 0,
          recorded_recruitment_amount: null,
          hire_count: 1,
          cost_per_hire: null,
          calculation_status: 'NO_COST_RECORDS',
          retention: [
            { months: 2, judgeable_count: 1, retained_count: 1 },
            { months: 3, judgeable_count: 0, retained_count: 0 },
            { months: 4, judgeable_count: 0, retained_count: 0 },
          ],
        },
        {
          media_name: '費用だけ',
          cost_entry_count: 1,
          recorded_recruitment_amount: 500,
          hire_count: 0,
          cost_per_hire: null,
          calculation_status: 'NO_HIRES',
          retention: [
            { months: 2, judgeable_count: 0, retained_count: 0 },
            { months: 3, judgeable_count: 0, retained_count: 0 },
            { months: 4, judgeable_count: 0, retained_count: 0 },
          ],
        },
      ],
      number: 0,
      size: 20,
      total_pages: 1,
      total_elements: 3,
    },
  };
}
beforeEach(() => jest.clearAllMocks());
it('費用だけ・入店だけ・媒体未入力と定着の可判定を区別する', async () => {
  jest.mocked(advertisingApi.recruitmentEffects).mockResolvedValue(report());
  render(<RecruitmentEffectPage month="2026-05" />);
  const matched = await screen.findByRole('row', { name: /^求人A/ });
  expect(within(matched).getByText('約 15,000.00円')).toBeVisible();
  expect(within(matched).getByText('1人 / 可判定 2人')).toBeVisible();
  expect(within(matched).getAllByText('判定時点前')).toHaveLength(2);
  const hireOnly = screen.getByRole('row', { name: /^入店だけ/ });
  expect(within(hireOnly).getByText('未登録')).toBeVisible();
  expect(within(hireOnly).getByText('採用広告費が未登録')).toBeVisible();
  const costOnly = screen.getByRole('row', { name: /^費用だけ/ });
  expect(within(costOnly).getByText('未算出')).toBeVisible();
  expect(within(costOnly).getByText('入店人数が0人')).toBeVisible();
  expect(screen.getByLabelText('採用効果の月合計')).toHaveTextContent('媒体未入力の入店：1人');
  expect(screen.getByText(/ROIを示しません/)).toBeVisible();
  expect(screen.getByText(/2026-07-15/)).toBeVisible();
  expect(advertisingApi.recruitmentEffects).toHaveBeenCalledWith('2026-05', 0);
});
it('取得失敗で行と総計を出さない', async () => {
  jest.mocked(advertisingApi.recruitmentEffects).mockRejectedValue(new Error('切断'));
  render(<RecruitmentEffectPage month="2026-05" />);
  expect(await screen.findByRole('alert')).toHaveTextContent('採用効果を取得できませんでした。');
  expect(screen.queryByLabelText('採用効果の月合計')).not.toBeInTheDocument();
});
