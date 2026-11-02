package com.kizuna.medium.application;

import com.kizuna.medium.api.dto.MediumResponses.MediumOption;
import com.kizuna.medium.api.dto.MediumResponses.MediumSummary;
import com.kizuna.medium.domain.Medium;
import com.kizuna.medium.domain.MediumRepository;
import com.kizuna.medium.domain.MediumStatus;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.user.application.BusinessAudit;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MediumService {
  private static final String TARGET = "MEDIUM";
  private final MediumRepository media;
  private final BusinessAudit audit;

  @Transactional(readOnly = true)
  public List<MediumSummary> list(Long storeId, MediumStatus status) {
    var rows =
        status == null
            ? media.findAllByStoreIdOrderByNameAscIdAsc(storeId)
            : media.findAllByStoreIdAndStatusOrderByNameAscIdAsc(storeId, status);
    return rows.stream().map(MediumSummary::of).toList();
  }

  @Transactional(readOnly = true)
  public List<MediumOption> options(Long storeId) {
    return media.findAllByStoreIdAndStatusOrderByNameAscIdAsc(storeId, MediumStatus.ACTIVE).stream()
        .map(MediumOption::of)
        .toList();
  }

  @Transactional
  public String create(Long storeId, String name) {
    String normalized = Medium.normalizeName(name);
    requireUnique(storeId, normalized);
    var medium = Medium.builder().name(normalized).build();
    medium.setStoreId(storeId);
    flushUnique(() -> media.saveAndFlush(medium));
    audit.recordCurrent(
        storeId, "MEDIUM_CREATED", TARGET, medium.getId(), Map.of(), snapshot(medium));
    return medium.getId();
  }

  @Transactional
  public MediumSummary rename(Long storeId, String id, String name, long version) {
    var medium = find(storeId, id, version);
    String normalized = Medium.normalizeName(name);
    if (normalized.equals(medium.getName())) return MediumSummary.of(medium);
    requireUnique(storeId, normalized);
    var before = snapshot(medium);
    medium.rename(normalized);
    flushUnique(media::flush);
    audit.recordCurrent(storeId, "MEDIUM_RENAMED", TARGET, id, before, snapshot(medium));
    return MediumSummary.of(medium);
  }

  @Transactional
  public MediumSummary stop(Long storeId, String id, long version) {
    var medium = find(storeId, id, version);
    var before = snapshot(medium);
    medium.stop();
    media.flush();
    audit.recordCurrent(storeId, "MEDIUM_STOPPED", TARGET, id, before, snapshot(medium));
    return MediumSummary.of(medium);
  }

  @Transactional
  public MediumSummary resume(Long storeId, String id, long version) {
    var medium = find(storeId, id, version);
    var before = snapshot(medium);
    medium.resume();
    media.flush();
    audit.recordCurrent(storeId, "MEDIUM_RESUMED", TARGET, id, before, snapshot(medium));
    return MediumSummary.of(medium);
  }

  @Transactional
  public void delete(Long storeId, String id, long version) {
    var medium = find(storeId, id, version);
    var before = snapshot(medium);
    media.delete(medium);
    media.flush();
    audit.recordCurrent(storeId, "MEDIUM_DELETED", TARGET, id, before, Map.of("exists", "false"));
  }

  private Medium find(Long storeId, String id, long version) {
    var medium =
        media
            .findByIdAndStoreId(id, storeId)
            .orElseThrow(() -> new NotFoundException("媒体が見つかりません"));
    medium.requireVersion(version);
    return medium;
  }

  private void requireUnique(Long storeId, String name) {
    if (media.existsByStoreIdAndName(storeId, name)) throw duplicate();
  }

  // 検査と書き込みの間に並行して同名が確定した場合も、同じ 409 に揃える。
  private void flushUnique(Runnable write) {
    IntegrityViolations.translateOnFailure(
        () -> {
          write.run();
          return null;
        },
        Map.of(DbConstraint.UQ_T_MEDIA_STORE_NAME, MediumService::duplicate));
  }

  private static ConflictException duplicate() {
    return new ConflictException("同じ名前の媒体が既に登録されています");
  }

  private static Map<String, String> snapshot(Medium medium) {
    return Map.of(
        "exists",
        "true",
        "name",
        medium.getName(),
        "status",
        medium.getStatus().name(),
        "version",
        String.valueOf(medium.getVersion()));
  }
}
