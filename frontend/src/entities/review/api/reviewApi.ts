import {
  apiClient,
  fromSpringPage,
  fromCursorPage,
  PageResult,
  CursorPageResult,
} from '@/shared/api';
import {
  Review,
  ReviewSummary,
  ReviewHistory,
  ReviewSearch,
  ReviewCommand,
  ReviewWriteResponse,
} from '../model/types';
const path = '/store/reviews';
const children = {
  CORRECT: 'corrections',
  DECIDE: 'decisions',
  WITHDRAW: 'withdrawals',
  GRANT: 'permissions',
  REVOKE: 'permission-revocations',
};
export const reviewApi = {
  list: async (page: number, search: ReviewSearch): Promise<PageResult<ReviewSummary>> =>
    fromSpringPage((await apiClient.get(path, { params: { page, size: 20, ...search } })).data),
  get: async (id: string): Promise<Review> => (await apiClient.get(`${path}/${id}`)).data,
  history: async (id: string, cursor?: string): Promise<CursorPageResult<ReviewHistory>> =>
    fromCursorPage(
      (await apiClient.get(`${path}/${id}/history`, { params: { cursor, size: 20 } })).data
    ),
  write: async (command: ReviewCommand): Promise<ReviewWriteResponse> =>
    (
      await apiClient.post(
        command.kind === 'CREATE' ? path : `${path}/${command.id}/${children[command.kind]}`,
        command.input
      )
    ).data,
};
