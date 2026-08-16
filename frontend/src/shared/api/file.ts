import { FileUploadResponse, apiClient } from '@/shared/api';

export const fileApi = {
  /** ファイルをアップロードする */
  upload: async (file: File, bucket: string = 'public'): Promise<FileUploadResponse> => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('bucket', bucket);
    // apiClient の既定 Content-Type は application/json のため、この上書きが無いと axios の
    // transformRequest が FormData を JSON へ変換してしまい multipart として送られない。
    // multipart/form-data を明示すると JSON 変換を回避でき、境界はブラウザが自動付与する。
    const response = await apiClient.post('/files', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  },
};
