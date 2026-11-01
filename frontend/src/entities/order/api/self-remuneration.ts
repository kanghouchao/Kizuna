import { apiClient, fromSpringPage, PageResult } from '@/shared/api';
import {
  SelfRemuneration,
  SelfRemunerationSummary,
  SelfRemunerationEnrollment,
  SelfRemunerationChange,
} from '../model/selfRemuneration';
const base = '/me/remunerations';
export const selfRemunerationApi = {
  async enrollments(page: number): Promise<PageResult<SelfRemunerationEnrollment>> {
    return fromSpringPage(
      (await apiClient.get('/me/remuneration-enrollments', { params: { page, size: 20 } })).data
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
  async changes(id: string, page: number): Promise<PageResult<SelfRemunerationChange>> {
    return fromSpringPage(
      (
        await apiClient.get(`${base}/${encodeURIComponent(id)}/changes`, {
          params: { page, size: 20 },
        })
      ).data
    );
  },
};
