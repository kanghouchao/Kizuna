package com.kizuna.shared.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

  private Scheme scheme = Scheme.HTTP;

  private String domain;

  /** 「本日」判定に用いるタイムゾーン。公開出勤表などの当日算出に使用する。 */
  private String timezone = "Asia/Tokyo";

  /** app.jwt.* */
  private Jwt jwt = new Jwt();

  /** app.upload.* */
  private Upload upload = new Upload();

  /** app.line.* */
  private Line line = new Line();

  @Getter
  @Setter
  public static class Jwt {
    private String secret;
    private long expiration;
  }

  @Getter
  @Setter
  public static class Upload {
    private String endpoint = "http://localhost:9000";
    private String bucket = "uploads";
    private String accessKey;
    private String secretKey;
    private String urlPrefix = "/static/uploads/";
    private long maxFileSize = 10485760;
    private List<String> allowedTypes =
        List.of("image/jpeg", "image/png", "image/gif", "image/webp");
  }

  /**
   * LINE ログインの設定。チャネル資格情報はシステム設定（DB）が優先で、未設定のときだけここ（環境変数）へフォールバックする。
   *
   * <p>{@code apiBaseUrl} は LINE プラットフォーム API の基底 URL。統合テストがスタブサーバーへ差し替えるための可変点で、 運用では既定値のまま使う。
   */
  @Getter
  @Setter
  public static class Line {
    private String apiBaseUrl = "https://api.line.me";
    private String channelId;
    private String channelSecret;
  }

  public enum Scheme {
    HTTP("http"),
    HTTPS("https");

    private final String name;

    Scheme(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }

  public String getScheme() {
    return scheme != null ? scheme.getName() : null;
  }

  public String getJwtSecret() {
    return jwt != null ? jwt.getSecret() : null;
  }

  public long getJwtExpiration() {
    return jwt != null ? jwt.getExpiration() : 0L;
  }
}
