package com.kizuna.storage.application;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** ローカルファイルシステムを使用したファイルストレージサービスの実装 */
@Service
@RequiredArgsConstructor
public class LocalFileStorageServiceImpl implements FileStorageService {

  private final AppProperties appProperties;

  @Override
  public String store(String prefix, String bucket, MultipartFile file) {
    AppProperties.Upload upload = appProperties.getUpload();

    // 1. プレフィックスのバリデーション (パストラバーサル防止)
    if (prefix == null || !prefix.matches("^[a-zA-Z0-9_-]+$")) {
      throw new ServiceException("不正なプレフィックスです");
    }

    // バケット名のバリデーション (パストラバーサル防止)
    // 英数字、ハイフン、アンダースコアのみを許可
    if (bucket == null || !bucket.matches("^[a-zA-Z0-9_-]+$")) {
      throw new ServiceException("不正なバケット名です");
    }

    // ファイルサイズのバリデーション
    if (file.getSize() > upload.getMaxFileSize()) {
      throw new ServiceException("ファイルサイズが上限を超えています");
    }

    // MIMEタイプのバリデーション
    String contentType = file.getContentType();
    if (contentType == null || !upload.getAllowedTypes().contains(contentType)) {
      throw new ServiceException("許可されていないファイル形式です");
    }

    // 拡張子を元のファイル名から取得
    String originalFilename = file.getOriginalFilename();
    String extension = "";
    if (originalFilename != null && originalFilename.contains(".")) {
      extension = originalFilename.substring(originalFilename.lastIndexOf("."));
    }

    // UUIDでファイル名を生成
    String filename = UUID.randomUUID() + extension;
    String relativePath = bucket + "/" + prefix + "/" + filename;

    // 2. パスの正規化とベースディレクトリ内であることの確認
    Path basePath = Paths.get(upload.getBasePath()).toAbsolutePath().normalize();
    Path targetPath = basePath.resolve(relativePath).toAbsolutePath().normalize();

    if (!targetPath.startsWith(basePath)) {
      throw new ServiceException("不正なファイルパスです");
    }

    try {
      // ディレクトリを自動作成
      Files.createDirectories(targetPath.getParent());
      Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new ServiceException("ファイルの保存に失敗しました");
    }

    return relativePath;
  }
}
