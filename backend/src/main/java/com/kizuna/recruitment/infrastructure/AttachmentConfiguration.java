package com.kizuna.recruitment.infrastructure;

import com.kizuna.shared.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AttachmentConfiguration {
  @Bean(destroyMethod = "close")
  public PrivateAttachmentStorage privateAttachmentStorage(AppProperties properties) {
    return new PrivateAttachmentStorage(properties);
  }

  @Bean
  public RasterImageNormalizer rasterImageNormalizer(AppProperties properties) {
    return new RasterImageNormalizer(properties.getPrivateAttachments());
  }
}
