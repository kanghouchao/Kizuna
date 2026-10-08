import { apiClient, fromCursorPage, type CursorPageResult } from '@/shared/api';
import { requireId } from '@/shared/lib';
import type {
  ApplicantAttachment,
  ApplicantAttachmentUpload,
  ApplicantAttachmentPolicy,
} from '../model/types';
const resource = (id: string) => `/store/applicants/${requireId(id, '応募者')}`;
export const applicantAttachmentApi = {
  policy: async (): Promise<ApplicantAttachmentPolicy> =>
    (await apiClient.get('/store/applicants/attachment-policy')).data,
  list: async (id: string, cursor?: string): Promise<CursorPageResult<ApplicantAttachment>> =>
    fromCursorPage(
      (await apiClient.get(`${resource(id)}/attachments`, { params: { cursor, size: 20 } })).data
    ),
  uploads: async (
    id: string,
    cursor?: string
  ): Promise<CursorPageResult<ApplicantAttachmentUpload>> =>
    fromCursorPage(
      (await apiClient.get(`${resource(id)}/attachment-uploads`, { params: { cursor, size: 20 } }))
        .data
    ),
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
  download: async (id: string, attachmentId: string, signal?: AbortSignal): Promise<Blob> =>
    (
      await apiClient.get(
        `${resource(id)}/attachments/${requireId(attachmentId, '添付画像')}/content`,
        { responseType: 'blob', signal, timeout: 60_000 }
      )
    ).data,
};
