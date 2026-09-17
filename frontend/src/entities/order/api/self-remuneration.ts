import {
  apiClient,
  fromSpringPage,
  fromCursorPage,
  PageResult,
  CursorPageResult,
} from '@/shared/api';
import {
  SelfRemuneration,
  SelfRemunerationSummary,
  SelfRemunerationEnrollment,
  SelfRemunerationChange,
} from '../model/selfRemuneration';
const base = '/platform/me/remunerations';
export const selfRemunerationApi = {
  async enrollments(page: number): Promise<PageResult<SelfRemunerationEnrollment>> {
    return fromSpringPage(
      (await apiClient.get('/platform/me/remuneration-enrollments', { params: { page, size: 20 } }))
        .data
    );
  },
  async list(page: number, enrollmentId?: string): Promise<PageResult<SelfRemunerationSummary>> {
    return fromSpringPage(
      (await apiClient.get(base, { params: { page, size: 20, enrollment_id: enrollmentId } })).data
    );
  },
  async detail(id: string): Promise<SelfRemuneration> {
    return (await apiClient.get(`${base}/${encodeURIComponent(id)}`)).data;
  },
  async changes(id: string, cursor?: string): Promise<CursorPageResult<SelfRemunerationChange>> {
    return fromCursorPage(
      (
        await apiClient.get(`${base}/${encodeURIComponent(id)}/changes`, {
          params: { cursor, size: 20 },
        })
      ).data
    );
  },
};
