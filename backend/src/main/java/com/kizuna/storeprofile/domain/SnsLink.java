package com.kizuna.storeprofile.domain;

import jakarta.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

/**
 * jsonb 列 {@code t_store_profiles.sns_links} の要素。
 *
 * <p>hypersistence-utils は dirty checking 用の深いコピーを Java 直列化で作る。要素型が Serializable
 * でないと、空でない配列の書き込みが実行時に失敗する。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SnsLink implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @NotBlank(message = "プラットフォームは必須です")
  private String platform;

  @NotBlank(message = "URL は必須です")
  @URL(message = "有効な URL を入力してください")
  private String url;

  private String label;
}
