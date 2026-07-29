package com.kizuna.store.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StoreCreateDTO {

  /**
   * 小文字のホスト名（ラベルを . で連ねた形）だけを受け付ける。
   *
   * <p>この項目は店舗サイトへのリンクとして描画されるため、{@code /} {@code :} {@code @} を通すと {@code 正規.example@攻撃者.example}
   * のような値が別ホストへの導線になる。形式検査は前端にもあるが、REST を直接叩けば迂回できるので保存の手前で閉じる。長さ上限は列定義（VARCHAR(128)）に合わせる。
   *
   * <p>大文字を受け付けないのは、この値が Host ヘッダによる店舗文脈の解決キーだから。ブラウザは Host を小文字で送るのに照合は大小を区別するため、{@code
   * Shop.Example.com} を保存すると誰も到達できない店舗ができる。正規化して呑まずに拒むことで、その取り違えを保存時に表面化させる。
   */
  private static final String DOMAIN_PATTERN =
      "^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*$";

  @NotBlank(message = "name is required")
  private String name;

  @NotBlank(message = "domain is required")
  @Size(max = 128, message = "domain is too long")
  @Pattern(regexp = DOMAIN_PATTERN, message = "domain must be a hostname")
  private String domain;

  @NotBlank(message = "email is required")
  private String email;
}
