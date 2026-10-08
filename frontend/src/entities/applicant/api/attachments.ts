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
  download: async (id: string, attachmentId: string, signal?: AbortSignal): Promise<Blob> => {
    try {
      return (
        await apiClient.get(
          `${resource(id)}/attachments/${requireId(attachmentId, '添付画像')}/content`,
          { responseType: 'blob', signal, timeout: 60_000 }
        )
      ).data;
    } catch (error) {
      if (signal?.aborted) throw error;
      const response = (error as { response?: { data?: unknown } } | null)?.response;
      if (response?.data instanceof Blob && response.data.type.includes('application/json')) {
        try {
          const body: unknown = JSON.parse(await response.data.text());
          if (body && typeof body === 'object' && 'error' in body && typeof body.error === 'string')
            response.data = body;
        } catch {
          // 解読できない本文は共通の代替文言で通知し、HTTP状態と元の失敗を保持する。
        }
      }
      throw error;
    }
  },
};
