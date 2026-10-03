import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useForm } from 'react-hook-form';
import {
  orderApi,
  OrderFeeLineInput,
  OrderSpecialService,
  SpecialServiceCandidate,
  SpecialServiceRevision,
} from '@/entities/order';
import { Form } from '@/shared/ui';
import { course } from '../lib/orderTestSupport';
import { OrderCourseField } from '../ui/OrderCourseField';
import { OrderSpecialServicesField } from '../ui/OrderSpecialServicesField';
import { OrderFeeLinesField } from '../ui/OrderFeeLinesField';

jest.mock('next/navigation', () => ({ useParams: () => ({ storeId: '1' }) }));
jest.mock('@/entities/order', () => ({
  ...jest.requireActual('@/entities/order'),
  orderApi: {
    courseCandidates: jest.fn(),
    courseRevisions: jest.fn(),
    specialServiceCandidates: jest.fn(),
    specialServiceRevisions: jest.fn(),
  },
}));
const special: OrderSpecialService & SpecialServiceCandidate & SpecialServiceRevision = {
  consent_event_id: 'consent-1',
  consent_version: 1,
  occurred_at: '2026-09-15T00:00:00Z',
  service_deleted: false,
  service_id: 'special-1',
  revision_id: 'special-r1',
  revision_number: 1,
  name: '特殊',
  price: 2000,
  remuneration: 1000,
  charge_type: 'PAID',
  terms_version: 1,
  enrollment_id: 'cast-1',
  adoption_basis: 'ACCEPTED_TERMS',
  requires_attention: false,
};
function Editor({
  existing = false,
  historical = false,
}: {
  existing?: boolean;
  historical?: boolean;
}) {
  const form = useForm({
    defaultValues: {
      course_id: '',
      course_revision_id: '',
      cast_id: 'cast-1',
      special_service_ids: existing ? [special.service_id] : [],
      special_service_revision_ids: existing ? [special.revision_id] : [],
      fee_lines: [{ kind: 'DISCOUNT', name: '割引', amount: 1000 }] as OrderFeeLineInput[],
    },
  });
  const history = historical ? 'order-1' : undefined;
  return (
    <Form {...form}>
      <OrderCourseField current={existing ? course : undefined} historicalOrderId={history} />
      <OrderSpecialServicesField
        current={existing ? [special] : []}
        originalCast={existing ? 'cast-1' : undefined}
        historicalOrderId={history}
      />
      <OrderFeeLinesField
        systemLines={
          existing
            ? [
                {
                  kind: 'BASE_COURSE',
                  amount: course.price,
                  remuneration: 7000,
                  system_owned: false,
                },
                {
                  kind: 'SPECIAL_SERVICE',
                  amount: special.price,
                  remuneration: 1000,
                  system_owned: false,
                },
              ]
            : []
        }
      />
      <button onClick={() => form.setValue('cast_id', 'cast-2')}>担当を変更</button>
      <button onClick={() => form.reset()}>入力を戻す</button>
    </Form>
  );
}
async function selectCourse(name: RegExp, historical = false) {
  fireEvent.click(
    screen.getByRole('combobox', { name: historical ? '訂正する過去の版' : 'コース' })
  );
  const option = await screen.findByRole('option', { name });
  fireEvent.pointerDown(option);
  fireEvent.click(option);
}
beforeEach(() => {
  jest.resetAllMocks();
  jest
    .mocked(orderApi.courseCandidates)
    .mockResolvedValue({ rows: [course], total: 1, page: 0, pageCount: 1 });
  jest.mocked(orderApi.courseRevisions).mockResolvedValue({ content: [course] });
  jest
    .mocked(orderApi.specialServiceCandidates)
    .mockResolvedValue({ rows: [special], total: 1, page: 0, pageCount: 1 });
  jest
    .mocked(orderApi.specialServiceRevisions)
    .mockResolvedValue({ rows: [special], nextCursor: null });
});
test('新規選択のコース・特殊サービス・割引を合算し、担当変更で特殊サービス額を除く', async () => {
  render(<Editor />);
  await selectCourse(/基本/);
  expect(screen.getByText('小計 ¥11,000')).toBeInTheDocument();
  fireEvent.click(await screen.findByRole('checkbox'));
  expect(screen.getByText('小計 ¥13,000')).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('明細1の金額'), { target: { value: '500' } });
  expect(screen.getByText('小計 ¥13,500')).toBeInTheDocument();
  fireEvent.click(screen.getByText('担当を変更'));
  expect(screen.getByText('小計 ¥11,500')).toBeInTheDocument();
});
test('コースの改選は旧額を置換し、候補のページ移動でも価格を保持する', async () => {
  jest
    .mocked(orderApi.courseCandidates)
    .mockResolvedValueOnce({
      rows: [{ ...course, service_id: 'new-course', name: '長時間', price: 18000 }],
      total: 21,
      page: 0,
      pageCount: 2,
    })
    .mockResolvedValue({ rows: [], total: 21, page: 1, pageCount: 2 });
  render(<Editor existing />);
  expect(screen.getByText('小計 ¥13,000')).toBeInTheDocument();
  await selectCourse(/長時間/);
  expect(screen.getByText('小計 ¥19,000')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('combobox', { name: 'コース' }));
  fireEvent.click(screen.getByRole('button', { name: '次へ' }));
  await screen.findByText('選択できるコースがありません。');
  expect(screen.getByText('小計 ¥19,000')).toBeInTheDocument();
  const retained = screen.getByRole('option', { name: '採用済み条件を保持' });
  fireEvent.pointerDown(retained);
  fireEvent.click(retained);
  expect(screen.getByText('小計 ¥13,000')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('checkbox'));
  expect(screen.getByText('小計 ¥11,000')).toBeInTheDocument();
  fireEvent.click(screen.getByText('入力を戻す'));
  expect(screen.getByText('小計 ¥13,000')).toBeInTheDocument();
});
test('候補の検索後も選択した特殊サービスの価格を保持する', async () => {
  const other = {
    ...special,
    service_id: 'special-2',
    revision_id: 'special-r2',
    name: '追加サービス',
    price: 3000,
  };
  const load = jest.mocked(orderApi.specialServiceCandidates);
  load
    .mockResolvedValueOnce({ rows: [special], total: 1, page: 0, pageCount: 1 })
    .mockResolvedValue({ rows: [other], total: 1, page: 0, pageCount: 1 });
  render(<Editor />);
  await selectCourse(/基本/);
  fireEvent.click(await screen.findByRole('checkbox', { name: /特殊/ }));
  fireEvent.change(screen.getByLabelText('特殊サービスを検索'), { target: { value: '追加' } });
  fireEvent.click(await screen.findByRole('checkbox', { name: /追加サービス/ }));
  expect(screen.getByText('小計 ¥16,000')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('checkbox', { name: /特殊/ }));
  expect(screen.getByText('小計 ¥14,000')).toBeInTheDocument();
});
test('完了後訂正で歴史版本を選ぶとコースと特殊サービスの旧額を置換する', async () => {
  jest.mocked(orderApi.courseRevisions).mockResolvedValue({
    content: [{ ...course, revision_id: 'course-r2', name: '訂正コース', price: 15000 }],
  });
  jest.mocked(orderApi.specialServiceRevisions).mockResolvedValue({
    rows: [{ ...special, revision_id: 'special-r2', revision_number: 2, price: 4000 }],
    nextCursor: null,
  });
  render(<Editor existing historical />);
  await selectCourse(/訂正コース/, true);
  fireEvent.click(await screen.findByRole('checkbox', { name: /版2/ }));
  expect(screen.getByText('小計 ¥18,000')).toBeInTheDocument();
  await waitFor(() => expect(screen.getByRole('checkbox', { name: /版1/ })).not.toBeChecked());
});
