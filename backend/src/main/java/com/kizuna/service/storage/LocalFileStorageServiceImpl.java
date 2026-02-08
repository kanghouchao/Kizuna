package com.kizuna.service.storage;

import com.kizuna.config.AppProperties;
import com.kizuna.exception.ServiceException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** ローカルファイルシステムを使用したファイルストレージサービスの実装 */
@Service
@RequiredArgsConstructor
public class LocalFileStorageServiceImpl implements FileStorageService {

  private final AppProperties appProperties;

  @Override
  public String store(Long tenantId, String directory, MultipartFile file) {
    AppProperties.Upload upload = appProperties.getUpload();

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
    String relativePath = tenantId + "/" + directory + "/" + filename;
    Path targetPath = Paths.get(upload.getBasePath()).resolve(relativePath);

    try {
      // ディレクトリを自動作成
      Files.createDirectories(targetPath.getParent());
      Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new ServiceException("ファイルの保存に失敗しました");
    }

    return relativePath;
  }

  @Override
  public void delete(String filePath) {
    Path path = Paths.get(appProperties.getUpload().getBasePath()).resolve(filePath);
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      throw new ServiceException("ファイルの削除に失敗しました");
    }
  }

  @Override
  public Resource load(String filePath) {
    try {
      Path path = Paths.get(appProperties.getUpload().getBasePath()).resolve(filePath);
      Resource resource = new UrlResource(path.toUri());
      if (!resource.exists() || !resource.isReadable()) {
        throw new ServiceException("ファイルが見つかりません");
      }
      return resource;
    } catch (IOException e) {
      throw new ServiceException("ファイルの読み込みに失敗しました");
    }
  }
}
