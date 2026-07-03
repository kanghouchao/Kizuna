package com.kizuna.storage.application;

import org.springframework.web.multipart.MultipartFile;

/** ファイルストレージサービスのインターフェース */
public interface FileStorageService {

  /** ファイルを保存し、相対パスを返す */
  String store(String prefix, String bucket, MultipartFile file);
}
