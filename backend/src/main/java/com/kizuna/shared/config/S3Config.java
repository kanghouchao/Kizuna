package com.kizuna.shared.config;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

  @Bean
  public S3Client s3Client(AppProperties appProperties) {
    AppProperties.Upload upload = appProperties.getUpload();
    return S3Client.builder()
        .httpClientBuilder(UrlConnectionHttpClient.builder())
        .endpointOverride(URI.create(upload.getEndpoint()))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(upload.getAccessKey(), upload.getSecretKey())))
        .region(Region.US_EAST_1)
        // 内部サービス名で接続するため、バケット名をホスト名に含めない。
        .forcePathStyle(true)
        .build();
  }
}
