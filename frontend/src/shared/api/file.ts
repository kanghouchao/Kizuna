import { FileUploadResponse, apiClient } from '@/shared/api';

export const fileApi = {
  /** ファイルをアップロードする */
  upload: async (file: File, bucket: string = 'public'): Promise<FileUploadResponse> => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('bucket', bucket);
    // axiosが自動的に境界を設定するため、Content-Typeヘッダーは手動で設定しない
    const response = await apiClient.post('/files/upload', formData);
    return response.data;
  },
};
