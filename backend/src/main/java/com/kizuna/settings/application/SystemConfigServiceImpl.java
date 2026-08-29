package com.kizuna.settings.application;

import com.kizuna.settings.api.dto.SystemConfigMapper;
import com.kizuna.settings.api.dto.SystemConfigResponse;
import com.kizuna.settings.api.dto.SystemConfigUpdateRequest;
import com.kizuna.settings.domain.SystemConfig;
import com.kizuna.settings.domain.SystemConfigRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

  /**
   * TIME の受理形。{@code LocalTime.parse} の既定（ISO）は秒・小数秒も通すため、文案どおりの分精度で撥ねる。 STRICT を明示するのは既定の SMART が
   * {@code 24:00} を {@code 00:00} へ丸めて受理するためで、保存されるのは原文の {@code 24:00}、読み手の ISO 解析はそれを撥ねて兜底へ落ちる。
   */
  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT);

  private final SystemConfigRepository systemConfigRepository;
  private final SystemConfigMapper systemConfigMapper;

  @Override
  @Transactional(readOnly = true)
  public List<SystemConfigResponse> getAllConfigs() {
    return systemConfigRepository.findAll().stream()
        .map(this::toMaskedResponse)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<SystemConfigResponse> getConfigsByCategory(String category) {
    return systemConfigRepository.findByCategory(category).stream()
        .map(this::toMaskedResponse)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional
  // どのキーが更新されても確実に無効化するため全消去（設定更新は低頻度の管理操作）
  @CacheEvict(value = "systemConfigValues", allEntries = true)
  public SystemConfigResponse updateConfig(String configKey, SystemConfigUpdateRequest request) {
    SystemConfig config =
        systemConfigRepository
            .findByConfigKey(configKey)
            .orElseThrow(() -> new NotFoundException("設定キーが見つかりません: " + configKey));

    validateValue(config, request.getConfigValue());
    systemConfigMapper.updateEntityFromRequest(request, config);
    SystemConfig saved = systemConfigRepository.save(config);
    return toMaskedResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(value = "systemConfigValues", key = "#configKey")
  public Optional<String> getConfigValue(String configKey) {
    return systemConfigRepository.findByConfigKey(configKey).map(SystemConfig::getConfigValue);
  }

  /**
   * SMTP 設定は都度読む（キャッシュしない）。キャッシュ値の既定シリアライザは JDK 直列化で、{@code Serializable} でない record
   * を載せようとすると読み取り自体が例外になる（{@code getConfigValue} が動くのは Spring が Optional を解いて String
   * を保存するため）。送信は低頻度で、都度読みなら管理画面での差し替えも次の送信から効く。
   */
  @Override
  @Transactional(readOnly = true)
  public SmtpSettings smtpSettings() {
    int port = 25;
    String rawPort = rawValue("smtp_port");
    if (!rawPort.isBlank()) {
      try {
        port = Integer.parseInt(rawPort.trim());
      } catch (NumberFormatException e) {
        // 不正値は既定ポートで送信を試みる（更新時に NUMBER 検証済みのため通常は到達しない）
      }
    }
    return new SmtpSettings(
        rawValue("smtp_host"),
        port,
        rawValue("smtp_username"),
        rawValue("smtp_password"),
        rawValue("smtp_from"));
  }

  /** LINE チャネル資格情報は都度読む（キャッシュしない）。参照するのは LINE 端点だけで頻度が低く、 管理画面での差し替えを次の要求から効かせられる。 */
  @Override
  @Transactional(readOnly = true)
  public LineChannelSettings lineChannelSettings() {
    return new LineChannelSettings(rawValue("line_channel_id"), rawValue("line_channel_secret"));
  }

  /**
   * ポイント制度の設定は都度読む（キャッシュしない）。理由は {@link #smtpSettings} と同じで、record は {@code Serializable} でないため既定の
   * JDK 直列化キャッシュに載せられない。
   *
   * <p>既定値は「付与しない・利用単位 1」で、未設定や不正値のまま受注完了が落ちることはない。
   */
  @Override
  @Transactional(readOnly = true)
  public PointSettings pointSettings() {
    return new PointSettings(
        intValue("point_grant_unit_amount", 0),
        intValue("point_grant_points_per_unit", 0),
        intValue("point_usage_unit", 1));
  }

  /**
   * 会員ランクの閾値も都度読む。理由は {@link #pointSettings} と同じで、都度読みなら閾値の変更が次回の判定から効く。
   *
   * <p>既定値は 0 で、{@link MemberRankSettings.Threshold} 側がそれを「成立しえない条件」として扱う — 未設定を 「0
   * 以上で達成」と読むと最初の付与で全員が最上位へ上がる。
   */
  @Override
  @Transactional(readOnly = true)
  public MemberRankSettings memberRankSettings() {
    return new MemberRankSettings(
        new MemberRankSettings.Threshold(
            intValue("member_rank_silver_visit_count", 0),
            intValue("member_rank_silver_granted_points", 0)),
        new MemberRankSettings.Threshold(
            intValue("member_rank_gold_visit_count", 0),
            intValue("member_rank_gold_granted_points", 0)));
  }

  /** 数値設定の読み取り。未設定・不正値は既定値へ倒す（更新時に NUMBER として int の範囲まで検証済みのため不正値は通常は到達しない）。 */
  private int intValue(String configKey, int fallback) {
    String raw = rawValue(configKey);
    if (raw.isBlank()) {
      return fallback;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  /** {@code getConfigValue} のキャッシュを経由しない内部読み取り（設定スナップショットは都度 DB から組み立てる）。 */
  private String rawValue(String configKey) {
    return systemConfigRepository
        .findByConfigKey(configKey)
        .map(SystemConfig::getConfigValue)
        .orElse("");
  }

  /** value_type に応じて設定値を検証する */
  private void validateValue(SystemConfig config, String value) {
    if (value == null || value.isBlank()) {
      return; // 未設定（空）は許容する
    }
    if ("BOOLEAN".equals(config.getValueType())
        && !"true".equals(value)
        && !"false".equals(value)) {
      throw new ServiceException("真偽値（true / false）を指定してください: " + config.getConfigKey());
    }
    if ("NUMBER".equals(config.getValueType())) {
      long parsed;
      try {
        parsed = Long.parseLong(value.trim());
      } catch (NumberFormatException e) {
        throw new ServiceException("数値を指定してください: " + config.getConfigKey());
      }
      // NUMBER の読み手はいずれも int で読み、解釈できない値は既定値へ倒す。int に収まらない値を通すと
      // 保存だけが成功し、読み取りでは利用単位 1・付与 0 という別の意味へ静かに入れ替わる。
      if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
        throw new ServiceException(
            "数値は -2147483648 から 2147483647 の範囲で指定してください: " + config.getConfigKey());
      }
    }
    // 解釈できない時刻は読み手が 00:00 へ倒すため、保存だけが成功して暦日と同じ挙動へ静かに入れ替わる。
    // 秒より細かい値も撥ねる — 通すと境界が文案の分より内側へずれ、画面が謳う時刻と実際が食い違う。
    if ("TIME".equals(config.getValueType())) {
      try {
        LocalTime.parse(value.trim(), TIME_FORMAT);
      } catch (DateTimeParseException e) {
        throw new ServiceException("時刻を HH:mm 形式で指定してください: " + config.getConfigKey());
      }
    }
  }

  /** 秘匿設定の値をマスクしてレスポンスへ変換する */
  private SystemConfigResponse toMaskedResponse(SystemConfig config) {
    SystemConfigResponse response = systemConfigMapper.toResponse(config);
    if (Boolean.TRUE.equals(config.getSecret())) {
      response.setConfigValue(null);
    }
    return response;
  }
}
