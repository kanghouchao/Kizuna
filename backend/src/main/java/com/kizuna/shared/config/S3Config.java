package com.kizuna.shared.config;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

  @Bean
  public S3Client s3Client(AppProperties appProperties) {
    AppProperties.Upload upload = appProperties.getUpload();
    return S3Client.builder()
        .endpointOverride(URI.create(upload.getEndpoint()))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(upload.getAccessKey(), upload.getSecretKey())))
        // MinIO は region を無視するが AWS SDK v2 のクライアント生成に必須のためハードコードする。
        .region(Region.US_EAST_1)
        // MinIO はパススタイル（endpoint/bucket/key）でのみ解決できるため仮想ホスト形式を無効化する。
        .forcePathStyle(true)
        .build();
  }
}
