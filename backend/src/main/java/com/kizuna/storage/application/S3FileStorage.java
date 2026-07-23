package com.kizuna.storage.application;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** MinIO(S3互換オブジェクトストレージ)を使用したファイルストレージサービスの実装 */
@Service
@RequiredArgsConstructor
public class S3FileStorage implements FileStorageService, ApplicationRunner {

  private final S3Client s3Client;
  private final AppProperties appProperties;

  @Override
  public String store(String prefix, String bucket, MultipartFile file) {
    AppProperties.Upload upload = appProperties.getUpload();

    // プレフィックスのバリデーション (パストラバーサル防止)
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

    // UUIDでファイル名を生成。object key は bucket/prefix/UUID.ext 形式とする
    // （実 S3 bucket は固定値、リクエストの bucket 値は key の一部として扱う）
    String filename = UUID.randomUUID() + extension;
    String key = bucket + "/" + prefix + "/" + filename;

    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw new ServiceException("ファイルの保存に失敗しました");
    }

    s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(upload.getBucket())
            .key(key)
            .contentType(contentType)
            .build(),
        RequestBody.fromBytes(bytes));

    return key;
  }

  /** 起動時に固定バケットを冪等に作成し、public-read ポリシーを付与する。 */
  @Override
  public void run(ApplicationArguments args) {
    String bucket = appProperties.getUpload().getBucket();
    try {
      s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
    } catch (BucketAlreadyOwnedByYouException e) {
      // 既に存在する場合は何もしない（冪等）
    }
    s3Client.putBucketPolicy(
        PutBucketPolicyRequest.builder().bucket(bucket).policy(publicReadPolicy(bucket)).build());
  }

  private String publicReadPolicy(String bucket) {
    return """
        {
          "Version": "2012-10-17",
          "Statement": [
            {
              "Effect": "Allow",
              "Principal": "*",
              "Action": "s3:GetObject",
              "Resource": "arn:aws:s3:::%s/*"
            }
          ]
        }
        """
        .formatted(bucket);
  }
}
