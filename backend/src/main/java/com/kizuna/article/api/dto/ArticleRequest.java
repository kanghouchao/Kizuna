package com.kizuna.article.api.dto;

import com.kizuna.shared.exception.ServiceException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 記事の作成・更新。版は更新でだけ使い、作成では読まない。 */
public record ArticleRequest(
    @NotBlank(message = "表題は必須です") @Size(max = 100, message = "表題は 100 文字以内で入力してください") String title,
    @NotBlank(message = "本文は必須です") @Size(max = 10000, message = "本文は 10000 文字以内で入力してください")
        String body,
    @Size(max = 500, message = "表紙画像の URL は 500 文字以内で指定してください") String coverImageUrl,
    Long version) {
  public long expectedVersion() {
    if (version == null) throw new ServiceException("編集の対象バージョンは必須です");
    return version;
  }
}
