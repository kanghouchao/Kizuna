package com.kizuna.auth.infrastructure;

import com.kizuna.shared.config.AppProperties;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * LINE プラットフォーム API の呼び出し。前端が取得した認可コードを id_token へ交換し、id_token を LINE 自身に検証させて 本人同一性を得る（チャネルシークレットは
 * バックエンドの外へ出ない）。
 *
 * <p>応答は JSON をキー名で読む。LINE の項目名（id_token / sub / name）はワイヤ契約であり、Jackson の命名戦略に依存しないよう 専用の
 * JsonMapper で木として読む。
 */
@Log4j2
@Component
public class LineApiClient {

  private static final String TOKEN_PATH = "/oauth2/v2.1/token";
  private static final String VERIFY_PATH = "/oauth2/v2.1/verify";

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final RestClient restClient;

  public LineApiClient(AppProperties appProperties) {
    this.restClient = RestClient.builder().baseUrl(appProperties.getLine().getApiBaseUrl()).build();
  }

  /**
   * 認可コードを id_token へ交換し、LINE の検証端点で id_token を検証して本人同一性を返す。
   *
   * @throws LineAuthenticationException LINE がエラーを返した、または応答に sub が含まれない場合
   */
  public LineIdentity exchangeAndVerify(
      LineChannel channel, String code, String redirectUri, String codeVerifier) {
    String idToken = requestIdToken(channel, code, redirectUri, codeVerifier);
    return verify(channel, idToken);
  }

  private String requestIdToken(
      LineChannel channel, String code, String redirectUri, String codeVerifier) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "authorization_code");
    form.add("code", code);
    form.add("redirect_uri", redirectUri);
    form.add("client_id", channel.channelId());
    form.add("client_secret", channel.channelSecret());
    if (codeVerifier != null && !codeVerifier.isBlank()) {
      form.add("code_verifier", codeVerifier);
    }
    JsonNode body = post(TOKEN_PATH, form);
    String idToken = body.path("id_token").asString(null);
    if (idToken == null || idToken.isBlank()) {
      throw new LineAuthenticationException("LINE のトークン応答に id_token が含まれていません");
    }
    return idToken;
  }

  private LineIdentity verify(LineChannel channel, String idToken) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("id_token", idToken);
    form.add("client_id", channel.channelId());
    JsonNode body = post(VERIFY_PATH, form);
    String lineUserId = body.path("sub").asString(null);
    if (lineUserId == null || lineUserId.isBlank()) {
      throw new LineAuthenticationException("LINE の検証応答に sub が含まれていません");
    }
    return new LineIdentity(lineUserId, body.path("name").asString(""));
  }

  private JsonNode post(String path, MultiValueMap<String, String> form) {
    String raw;
    try {
      raw =
          restClient
              .post()
              .uri(path)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(String.class);
    } catch (RestClientException e) {
      // LINE 側のエラー本文（invalid_grant 等）は攻撃者への手掛かりになるためログにのみ残す。
      log.warn("LINE API 呼び出しに失敗しました path={}: {}", path, e.getMessage());
      throw new LineAuthenticationException("LINE 認証に失敗しました", e);
    }
    try {
      return JSON.readTree(raw == null ? "" : raw);
    } catch (JacksonException e) {
      log.warn("LINE API の応答を解釈できませんでした path={}", path);
      throw new LineAuthenticationException("LINE 認証に失敗しました", e);
    }
  }
}
