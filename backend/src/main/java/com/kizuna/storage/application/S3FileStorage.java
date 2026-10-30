package com.kizuna.storage.application;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import java.io.IOException;
import java.util.Set;
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

/** SeaweedFS(S3互換オブジェクトストレージ)を使用したファイルストレージサービスの実装 */
@Service
@RequiredArgsConstructor
public class S3FileStorage implements FileStorageService, ApplicationRunner {

  /** 受け付ける唯一の bucket。object key の先頭になるので、自由入力を通さず固定する。 */
  private static final String PUBLIC_BUCKET = "public";

  /** 用途は画像の置き場所ごとの区分で、object key の一段になるので許可リストに限る。 */
  private static final Set<String> PURPOSES =
      Set.of("cast-photo", "store-logo", "store-banner", "store-mv", "partner-logo");

  /** 完成形 key は監査の target_id（VARCHAR(100)）にそのまま入るので、これを超える key は保存しない。 */
  private static final int MAX_KEY_LENGTH = 100;

  private final S3Client s3Client;
  private final AppProperties appProperties;

  @Override
  public String store(long storeId, String bucket, String purpose, MultipartFile file) {
    AppProperties.Upload upload = appProperties.getUpload();

    if (!PUBLIC_BUCKET.equals(bucket)) {
      throw new ServiceException("不正なバケット名です");
    }

    if (purpose == null || !PURPOSES.contains(purpose)) {
      throw new ServiceException("不正な用途です");
    }

    if (file.getSize() > upload.getMaxFileSize()) {
      throw new ServiceException("ファイルサイズが上限を超えています");
    }

    String contentType = file.getContentType();
    if (contentType == null || !upload.getAllowedTypes().contains(contentType)) {
      throw new ServiceException("許可されていないファイル形式です");
    }

    String originalFilename = file.getOriginalFilename();
    String extension = "";
    if (originalFilename != null && originalFilename.contains(".")) {
      extension = originalFilename.substring(originalFilename.lastIndexOf("."));
    }

    // object key は bucket/storeId/purpose/UUID.ext 形式とする
    // （実 S3 bucket は固定値、リクエストの bucket 値は key の一部として扱う）
    String filename = UUID.randomUUID() + extension;
    String key = bucket + "/" + storeId + "/" + purpose + "/" + filename;
    if (key.length() > MAX_KEY_LENGTH) {
      throw new ServiceException("ファイル名の拡張子が長すぎます");
    }

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
