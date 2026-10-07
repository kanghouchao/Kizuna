import {
  apiClient,
  fromCursorPage,
  fromSpringPage,
  type PageResult,
  type CursorPageResult,
} from '@/shared/api';
import { requireId } from '@/shared/lib';
import type {
  ApplicantDetail,
  ApplicantHistory,
  ApplicantIntake,
  ApplicantInterview,
  ApplicantStatus,
  ApplicantSummary,
  RecruitmentPolicy,
} from '../model/types';
const resource = (id: string) => `/store/applicants/${requireId(id, '応募者')}`;
export const applicantApi = {
  list: async (params: {
    page: number;
    size: number;
    search?: string;
    status?: ApplicantStatus;
  }): Promise<PageResult<ApplicantSummary>> =>
    fromSpringPage((await apiClient.get('/store/applicants', { params })).data),
  get: async (id: string): Promise<ApplicantDetail> => (await apiClient.get(resource(id))).data,
  create: async (data: ApplicantIntake): Promise<ApplicantDetail> =>
    (await apiClient.post('/store/applicants', data)).data,
  update: async (id: string, version: number, intake: ApplicantIntake): Promise<ApplicantDetail> =>
    (await apiClient.put(resource(id), { version, intake })).data,
  interview: async (
    id: string,
    version: number,
    interview: ApplicantInterview
  ): Promise<ApplicantDetail> =>
    (await apiClient.put(`${resource(id)}/interview`, { version, ...interview })).data,
  transition: async (
    id: string,
    version: number,
    status: ApplicantStatus,
    reason: string
  ): Promise<ApplicantDetail> =>
    (await apiClient.post(`${resource(id)}/transitions`, { version, status, reason })).data,
  history: async (id: string, cursor?: string): Promise<CursorPageResult<ApplicantHistory>> =>
    fromCursorPage(
      (await apiClient.get(`${resource(id)}/history`, { params: { cursor, size: 20 } })).data
    ),
  policy: async (): Promise<RecruitmentPolicy> =>
    (await apiClient.get('/store/applicants/policy')).data,
};
