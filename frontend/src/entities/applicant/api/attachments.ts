import { apiClient, fetchBlob, fromSpringPage, type Page, type PageResult } from '@/shared/api';
import { requireId } from '@/shared/lib';
import type {
  ApplicantAttachment,
  ApplicantAttachmentUpload,
  ApplicantAttachmentPolicy,
  ApplicantAttachmentOperation,
} from '../model/types';
const resource = (id: string) => `/applicants/${requireId(id, '応募者')}`;
export const applicantAttachmentApi = {
  policy: async (): Promise<ApplicantAttachmentPolicy> =>
    (await apiClient.get('/applicants/attachment-policy')).data,
  list: async (id: string, page = 0): Promise<PageResult<ApplicantAttachment>> =>
    fromSpringPage(
      (
        await apiClient.get<Page<ApplicantAttachment>>(`${resource(id)}/attachments`, {
          params: { page, size: 20 },
        })
      ).data
    ),
  uploads: async (id: string, page = 0): Promise<PageResult<ApplicantAttachmentUpload>> =>
    fromSpringPage(
      (
        await apiClient.get<Page<ApplicantAttachmentUpload>>(`${resource(id)}/attachment-uploads`, {
          params: { page, size: 20 },
        })
      ).data
    ),
  operation: async (id: string, key: string): Promise<ApplicantAttachmentOperation> =>
    (
      await apiClient.get(
        `${resource(id)}/attachment-operations/${encodeURIComponent(requireId(key, '操作'))}`
      )
    ).data,
  upload: async (
    id: string,
    file: File,
    key: string,
    uploadId?: string,
    signal?: AbortSignal
  ): Promise<ApplicantAttachment> => {
    const config = {
      headers: { 'Content-Type': file.type, 'Idempotency-Key': key },
      timeout: 120_000,
      signal,
    };
    return (
      uploadId
        ? await apiClient.put(
            `${resource(id)}/attachment-uploads/${requireId(uploadId, 'アップロード')}/content`,
            file,
            config
          )
        : await apiClient.post(`${resource(id)}/attachments`, file, config)
    ).data;
  },
  download: (id: string, attachmentId: string, signal?: AbortSignal): Promise<Blob> =>
    fetchBlob(`${resource(id)}/attachments/${requireId(attachmentId, '添付画像')}/content`, {
      signal,
      types: ['image/png', 'image/jpeg'],
      fallback: '添付画像を取得できませんでした',
      timeout: 60_000,
    }),
};
