package com.kizuna.storage.application;

import org.springframework.web.multipart.MultipartFile;

/** ファイルストレージサービスのインターフェース */
public interface FileStorageService {

  /** ファイルを保存し、相対パスを返す */
  String store(long storeId, String bucket, String purpose, MultipartFile file);
}
