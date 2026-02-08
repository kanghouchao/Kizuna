package com.kizuna.service.storage;

import org.springframework.web.multipart.MultipartFile;

/** ファイルストレージサービスのインターフェース */
public interface FileStorageService {

  /** ファイルを保存し、相対パスを返す */
  String store(Long tenantId, String directory, MultipartFile file);

  /** ファイルを削除する */
  void delete(String filePath);
}
