package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.api.dto.PlatformStaffCreateRequest;
import com.kizuna.user.api.dto.PlatformStaffResponse;
import com.kizuna.user.api.dto.PlatformStaffUpdateRequest;
import com.kizuna.user.domain.Capability;
import com.kizuna.user.domain.CapabilityBundle;
import com.kizuna.user.domain.CapabilityBundleRepository;
import com.kizuna.user.domain.DuplicateStaffEmailException;
import com.kizuna.user.domain.GrantAction;
import com.kizuna.user.domain.GrantHistory;
import com.kizuna.user.domain.GrantHistoryRepository;
import com.kizuna.user.domain.InvalidStoreScopeException;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StaleStaffUpdateException;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PlatformStaffServiceTest {

  private static final long HQ_BUNDLE = 10L;
  private static final long MANAGER_BUNDLE = 11L;

  @Mock private PlatformUserRepository repository;

  @Mock private CapabilityBundleRepository capabilityBundleRepository;

  @Mock private GrantHistoryRepository grantHistoryRepository;

  @Mock private PasswordEncoder encoder;

  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks private PlatformStaffService service;

  @Captor private ArgumentCaptor<PlatformUser> userCaptor;

  @Captor private ArgumentCaptor<GrantHistory> historyCaptor;

  private static final String ACTOR = "actor@kizuna.test";

  private CapabilityBundle bundle(long id, String name) {
    CapabilityBundle bundle =
        CapabilityBundle.builder().name(name).capabilities(Set.of(Capability.ORDER_MANAGE)).build();
    bundle.setId(id);
    return bundle;
  }

  private PlatformUser staff(
      long id, String email, Set<Long> bundleIds, StoreScopeType scopeType, Set<Long> storeIds) {
    PlatformUser user =
        PlatformUser.builder()
            .email(email)
            .password("hash")
            .displayName("表示名")
            .enabled(true)
            .userType(UserType.STAFF)
            .bundleIds(bundleIds)
            .storeScopeType(scopeType)
            .storeIds(storeIds)
            .build();
    user.setId(id);
    // 永続化済みエンティティを模す（DB の version 列は 0 で初期化される — #400）。
    ReflectionTestUtils.setField(user, "version", 0L);
    return user;
  }

  private PlatformUser castUser(long id, String email) {
    PlatformUser user =
        PlatformUser.builder()
            .email(email)
            .password("hash")
            .displayName("キャスト")
            .enabled(true)
            .userType(UserType.CAST)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .build();
    user.setId(id);
    return user;
  }

  private PlatformStaffCreateRequest createRequest(
      String email,
      String password,
      Set<Long> bundleIds,
      StoreScopeType scopeType,
      Set<Long> storeIds) {
    PlatformStaffCreateRequest req = new PlatformStaffCreateRequest();
    req.setEmail(email);
    req.setPassword(password);
    req.setDisplayName("表示名");
    req.setBundleIds(bundleIds);
    req.setStoreScopeType(scopeType);
    req.setStoreIds(storeIds);
    return req;
  }

  private PlatformStaffUpdateRequest updateRequest(
      Set<Long> bundleIds, StoreScopeType scopeType, Set<Long> storeIds) {
    PlatformStaffUpdateRequest req = new PlatformStaffUpdateRequest();
    req.setBundleIds(bundleIds);
    req.setStoreScopeType(scopeType);
    req.setStoreIds(storeIds);
    // staff() ヘルパの現行 version と一致させる（版の往復 — #400）。
    req.setVersion(0L);
    return req;
  }

  @Test
  void list_returnsStaffWithResolvedBundleNames() {
    when(repository.findByUserTypeOrderByDisplayNameAsc(UserType.STAFF))
        .thenReturn(
            List.of(
                staff(1L, "hq@kizuna.test", Set.of(HQ_BUNDLE), StoreScopeType.ALL_STORES, Set.of()),
                staff(
                    2L,
                    "mgr@kizuna.test",
                    Set.of(MANAGER_BUNDLE),
                    StoreScopeType.SPECIFIC_STORES,
                    Set.of(1L))));
    when(capabilityBundleRepository.findAllById(Set.of(HQ_BUNDLE, MANAGER_BUNDLE)))
        .thenReturn(List.of(bundle(HQ_BUNDLE, "HQ管理者"), bundle(MANAGER_BUNDLE, "店長")));

    List<PlatformStaffResponse> result = service.list();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).bundles())
        .containsExactly(new PlatformStaffResponse.BundleRef(HQ_BUNDLE, "HQ管理者"));
    assertThat(result.get(1).bundles())
        .containsExactly(new PlatformStaffResponse.BundleRef(MANAGER_BUNDLE, "店長"));
    assertThat(result.get(1).storeIds()).containsExactly(1L);
  }

  @Test
  void create_encodesPasswordAndSavesStaffWithBundles() {
    PlatformStaffCreateRequest req =
        createRequest(
            "new@kizuna.test",
            "rawpass",
            Set.of(MANAGER_BUNDLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(1L));
    when(capabilityBundleRepository.findAllById(Set.of(MANAGER_BUNDLE)))
        .thenReturn(List.of(bundle(MANAGER_BUNDLE, "店長")));
    when(repository.findByEmail("new@kizuna.test")).thenReturn(Optional.empty());
    when(encoder.encode("rawpass")).thenReturn("ENCODED");
    when(repository.saveAndFlush(userCaptor.capture()))
        .thenAnswer(
            invocation -> {
              PlatformUser saved = invocation.getArgument(0);
              saved.setId(9L);
              ReflectionTestUtils.setField(saved, "version", 0L);
              return saved;
            });

    PlatformStaffResponse res = service.create(req, ACTOR);

    verify(encoder).encode("rawpass");
    PlatformUser saved = userCaptor.getValue();
    assertThat(saved.getEmail()).isEqualTo("new@kizuna.test");
    assertThat(saved.getPassword()).isEqualTo("ENCODED");
    assertThat(saved.getDisplayName()).isEqualTo("表示名");
    assertThat(saved.getEnabled()).isTrue();
    assertThat(saved.getUserType()).isEqualTo(UserType.STAFF);
    assertThat(saved.getBundleIds()).containsExactly(MANAGER_BUNDLE);
    assertThat(saved.getStoreScopeType()).isEqualTo(StoreScopeType.SPECIFIC_STORES);
    assertThat(saved.getStoreIds()).containsExactly(1L);
    assertThat(res.id()).isEqualTo(9L);
    assertThat(res.bundles())
        .containsExactly(new PlatformStaffResponse.BundleRef(MANAGER_BUNDLE, "店長"));
    assertThat(res.enabled()).isTrue();
    assertThat(res.version()).as("作成応答も version を持つこと(#400)").isZero();
  }

  @Test
  void create_withSettlementScope_persistsSettlementDimension() {
    PlatformStaffCreateRequest req =
        createRequest(
            "acct@kizuna.test",
            "rawpass",
            Set.of(MANAGER_BUNDLE),
            StoreScopeType.ALL_STORES,
            Set.of());
    req.setSettlementScopeType(StoreScopeType.SPECIFIC_STORES);
    req.setSettlementStoreIds(Set.of(2L));
    when(capabilityBundleRepository.findAllById(Set.of(MANAGER_BUNDLE)))
        .thenReturn(List.of(bundle(MANAGER_BUNDLE, "店長")));
    when(repository.findByEmail("acct@kizuna.test")).thenReturn(Optional.empty());
    when(encoder.encode("rawpass")).thenReturn("ENCODED");
    when(repository.saveAndFlush(userCaptor.capture()))
        .thenAnswer(
            i -> {
              PlatformUser saved = i.getArgument(0);
              ReflectionTestUtils.setField(saved, "version", 0L);
              return saved;
            });

    PlatformStaffResponse res = service.create(req, ACTOR);

    assertThat(userCaptor.getValue().getSettlementScopeType())
        .isEqualTo(StoreScopeType.SPECIFIC_STORES);
    assertThat(userCaptor.getValue().getSettlementStoreIds()).containsExactly(2L);
    assertThat(res.settlementScopeType()).isEqualTo(StoreScopeType.SPECIFIC_STORES);
    assertThat(res.settlementStoreIds()).containsExactly(2L);
  }

  @Test
  void create_duplicateEmail_throwsAndDoesNotSave() {
    PlatformStaffCreateRequest req =
        createRequest(
            "dup@kizuna.test", "rawpass", Set.of(HQ_BUNDLE), StoreScopeType.ALL_STORES, Set.of());
    when(capabilityBundleRepository.findAllById(Set.of(HQ_BUNDLE)))
        .thenReturn(List.of(bundle(HQ_BUNDLE, "HQ管理者")));
    when(repository.findByEmail("dup@kizuna.test"))
        .thenReturn(
            Optional.of(
                staff(
                    5L,
                    "dup@kizuna.test",
                    Set.of(HQ_BUNDLE),
                    StoreScopeType.ALL_STORES,
                    Set.of())));

    assertThatThrownBy(() -> service.create(req, ACTOR))
        .isInstanceOf(DuplicateStaffEmailException.class);

    verify(repository, never()).save(any());
  }

  @Test
  void create_unknownBundle_throwsWithoutLookupOrEncode() {
    PlatformStaffCreateRequest req =
        createRequest(
            "new@kizuna.test", "rawpass", Set.of(999L), StoreScopeType.ALL_STORES, Set.of());
    when(capabilityBundleRepository.findAllById(Set.of(999L))).thenReturn(List.of());

    assertThatThrownBy(() -> service.create(req, ACTOR))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("能力束");

    verify(repository, never()).findByEmail(any());
    verifyNoInteractions(encoder);
  }

  @Test
  void create_unknownStoreId_convertsToInvalidStoreScope() {
    PlatformStaffCreateRequest req =
        createRequest(
            "fk@kizuna.test",
            "rawpass",
            Set.of(MANAGER_BUNDLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(999L));
    when(capabilityBundleRepository.findAllById(Set.of(MANAGER_BUNDLE)))
        .thenReturn(List.of(bundle(MANAGER_BUNDLE, "店長")));
    when(repository.findByEmail("fk@kizuna.test")).thenReturn(Optional.empty());
    when(encoder.encode("rawpass")).thenReturn("ENCODED");
    when(repository.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("fk violation"));

    assertThatThrownBy(() -> service.create(req, ACTOR))
        .isInstanceOf(InvalidStoreScopeException.class);
  }

  @Test
  void create_duplicateEmailUniqueViolation_convertsToDuplicateEmail() {
    // 事前 findByEmail を通過した後に DB 一意制約で敗者が弾かれるレース。店舗エラーではなく重複エラーへ分類する。
    PlatformStaffCreateRequest req =
        createRequest(
            "race@kizuna.test",
            "rawpass",
            Set.of(MANAGER_BUNDLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(1L));
    when(capabilityBundleRepository.findAllById(Set.of(MANAGER_BUNDLE)))
        .thenReturn(List.of(bundle(MANAGER_BUNDLE, "店長")));
    when(repository.findByEmail("race@kizuna.test")).thenReturn(Optional.empty());
    when(encoder.encode("rawpass")).thenReturn("ENCODED");
    when(repository.saveAndFlush(any()))
        .thenThrow(
            new DataIntegrityViolationException(
                "save failed",
                new RuntimeException(
                    "ERROR: duplicate key value violates unique constraint"
                        + " \"uq_t_users_email\"")));

    assertThatThrownBy(() -> service.create(req, ACTOR))
        .isInstanceOf(DuplicateStaffEmailException.class);
  }

  @Test
  void update_reassignsBundlesAndScopesAndSaves() {
    PlatformUser existing =
        staff(3L, "target@kizuna.test", Set.of(HQ_BUNDLE), StoreScopeType.ALL_STORES, Set.of());
    when(capabilityBundleRepository.findAllById(Set.of(MANAGER_BUNDLE)))
        .thenReturn(List.of(bundle(MANAGER_BUNDLE, "店長")));
    when(repository.findById(3L)).thenReturn(Optional.of(existing));
    // saveAndFlush は flush 時に version を増加させる（実挙動の模倣 — #400）。
    when(repository.saveAndFlush(existing))
        .thenAnswer(
            i -> {
              ReflectionTestUtils.setField(existing, "version", existing.getVersion() + 1);
              return existing;
            });

    Optional<PlatformStaffResponse> res =
        service.update(
            3L,
            updateRequest(Set.of(MANAGER_BUNDLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L)),
            ACTOR);

    assertThat(existing.getBundleIds()).containsExactly(MANAGER_BUNDLE);
    assertThat(existing.getStoreScopeType()).isEqualTo(StoreScopeType.SPECIFIC_STORES);
    assertThat(existing.getStoreIds()).containsExactly(1L);
    assertThat(res).isPresent();
    assertThat(res.get().id()).isEqualTo(3L);
    assertThat(res.get().bundles())
        .containsExactly(new PlatformStaffResponse.BundleRef(MANAGER_BUNDLE, "店長"));
    assertThat(res.get().version()).as("応答は保存後の増加した version を返すこと(#400)").isEqualTo(1L);
  }

  @Test
  void update_staleVersion_throwsConflictWithoutSavingOrRecordingHistory() {
    // 陳腐化した編集フォームの提出（version 不一致）は reassign 前に 409 系例外で拒否する（#400）。
    PlatformUser existing =
        staff(3L, "target@kizuna.test", Set.of(HQ_BUNDLE), StoreScopeType.ALL_STORES, Set.of());
    ReflectionTestUtils.setField(existing, "version", 5L);
    when(capabilityBundleRepository.findAllById(Set.of(MANAGER_BUNDLE)))
        .thenReturn(List.of(bundle(MANAGER_BUNDLE, "店長")));
    when(repository.findById(3L)).thenReturn(Optional.of(existing));
    PlatformStaffUpdateRequest req =
        updateRequest(Set.of(MANAGER_BUNDLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L));
    req.setVersion(4L);

    assertThatThrownBy(() -> service.update(3L, req, ACTOR))
        .isInstanceOf(StaleStaffUpdateException.class)
        .isInstanceOf(ConflictException.class)
        .hasMessage("他の管理者が更新しました。最新の内容を確認してください");

    // 授権は再割当されず、保存も付与履歴の記録も行われない。
    assertThat(existing.getBundleIds()).containsExactly(HQ_BUNDLE);
    verify(repository, never()).saveAndFlush(any());
    verifyNoInteractions(grantHistoryRepository);
  }

  @Test
  void update_unknownId_returnsEmptyWithoutSaving() {
    when(capabilityBundleRepository.findAllById(Set.of(HQ_BUNDLE)))
        .thenReturn(List.of(bundle(HQ_BUNDLE, "HQ管理者")));
    when(repository.findById(404L)).thenReturn(Optional.empty());

    Optional<PlatformStaffResponse> res =
        service.update(
            404L, updateRequest(Set.of(HQ_BUNDLE), StoreScopeType.ALL_STORES, Set.of()), ACTOR);

    assertThat(res).isEmpty();
    verify(repository, never()).save(any());
  }

  @Test
  void update_targetIsNonStaff_returnsEmptyWithoutSaving() {
    // CAST/MEMBER はスタッフ管理の可視対象外。id を直接指定してもスタッフへ昇格させない（本人種別検証）。
    when(capabilityBundleRepository.findAllById(Set.of(HQ_BUNDLE)))
        .thenReturn(List.of(bundle(HQ_BUNDLE, "HQ管理者")));
    when(repository.findById(8L)).thenReturn(Optional.of(castUser(8L, "cast@kizuna.test")));

    Optional<PlatformStaffResponse> res =
        service.update(
            8L, updateRequest(Set.of(HQ_BUNDLE), StoreScopeType.ALL_STORES, Set.of()), ACTOR);

    assertThat(res).isEmpty();
    verify(repository, never()).save(any());
  }

  @Test
  void update_unknownBundle_throwsWithoutLookup() {
    when(capabilityBundleRepository.findAllById(Set.of(999L))).thenReturn(List.of());

    assertThatThrownBy(
            () ->
                service.update(
                    3L, updateRequest(Set.of(999L), StoreScopeType.ALL_STORES, Set.of()), ACTOR))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("能力束");

    verify(repository, never()).findById(any());
    verify(repository, never()).save(any());
  }

  @Test
  void update_unknownStoreId_convertsToInvalidStoreScope() {
    PlatformUser existing =
        staff(7L, "target@kizuna.test", Set.of(HQ_BUNDLE), StoreScopeType.ALL_STORES, Set.of());
    when(capabilityBundleRepository.findAllById(Set.of(MANAGER_BUNDLE)))
        .thenReturn(List.of(bundle(MANAGER_BUNDLE, "店長")));
    when(repository.findById(7L)).thenReturn(Optional.of(existing));
    when(repository.saveAndFlush(existing))
        .thenThrow(new DataIntegrityViolationException("fk violation"));

    assertThatThrownBy(
            () ->
                service.update(
                    7L,
                    updateRequest(
                        Set.of(MANAGER_BUNDLE), StoreScopeType.SPECIFIC_STORES, Set.of(999L)),
                    ACTOR))
        .isInstanceOf(InvalidStoreScopeException.class);
  }

  @Test
  void update_duplicateEmailUniqueViolation_convertsToDuplicateEmail() {
    PlatformUser existing =
        staff(9L, "target@kizuna.test", Set.of(HQ_BUNDLE), StoreScopeType.ALL_STORES, Set.of());
    when(capabilityBundleRepository.findAllById(Set.of(MANAGER_BUNDLE)))
        .thenReturn(List.of(bundle(MANAGER_BUNDLE, "店長")));
    when(repository.findById(9L)).thenReturn(Optional.of(existing));
    when(repository.saveAndFlush(existing))
        .thenThrow(
            new DataIntegrityViolationException(
                "save failed",
                new RuntimeException(
                    "ERROR: duplicate key value violates unique constraint"
                        + " \"uq_t_users_email\"")));

    assertThatThrownBy(
            () ->
                service.update(
                    9L,
                    updateRequest(
                        Set.of(MANAGER_BUNDLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L)),
                    ACTOR))
        .isInstanceOf(DuplicateStaffEmailException.class);
  }

  @Test
  void create_recordsGrantHistoryWithActorAndSnapshot() {
    PlatformStaffCreateRequest req =
        createRequest(
            "new@kizuna.test",
            "rawpass",
            Set.of(MANAGER_BUNDLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(1L));
    when(capabilityBundleRepository.findAllById(Set.of(MANAGER_BUNDLE)))
        .thenReturn(List.of(bundle(MANAGER_BUNDLE, "店長")));
    when(repository.findByEmail("new@kizuna.test")).thenReturn(Optional.empty());
    when(encoder.encode("rawpass")).thenReturn("ENCODED");
    when(repository.saveAndFlush(any(PlatformUser.class)))
        .thenAnswer(
            invocation -> {
              PlatformUser saved = invocation.getArgument(0);
              saved.setId(9L);
              ReflectionTestUtils.setField(saved, "version", 0L);
              return saved;
            });

    service.create(req, ACTOR);

    verify(grantHistoryRepository).save(historyCaptor.capture());
    GrantHistory history = historyCaptor.getValue();
    assertThat(history.getPlatformUserId()).isEqualTo(9L);
    assertThat(history.getActorEmail()).isEqualTo(ACTOR);
    assertThat(history.getAction()).isEqualTo(GrantAction.GRANT);
    assertThat(history.getDetail()).contains("店長").contains("SPECIFIC_STORES");
  }

  @Test
  void update_recordsChangeHistory() {
    PlatformUser existing =
        staff(3L, "target@kizuna.test", Set.of(HQ_BUNDLE), StoreScopeType.ALL_STORES, Set.of());
    when(capabilityBundleRepository.findAllById(Set.of(MANAGER_BUNDLE)))
        .thenReturn(List.of(bundle(MANAGER_BUNDLE, "店長")));
    when(repository.findById(3L)).thenReturn(Optional.of(existing));
    when(repository.saveAndFlush(existing)).thenReturn(existing);

    service.update(
        3L,
        updateRequest(Set.of(MANAGER_BUNDLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L)),
        ACTOR);

    verify(grantHistoryRepository).save(historyCaptor.capture());
    GrantHistory history = historyCaptor.getValue();
    assertThat(history.getPlatformUserId()).isEqualTo(3L);
    assertThat(history.getAction()).isEqualTo(GrantAction.CHANGE);
    assertThat(history.getActorEmail()).isEqualTo(ACTOR);
  }

  @Test
  void update_disabling_stopsUserAndRecordsStopHistory() {
    PlatformUser existing =
        staff(3L, "target@kizuna.test", Set.of(HQ_BUNDLE), StoreScopeType.ALL_STORES, Set.of());
    when(capabilityBundleRepository.findAllById(Set.of(HQ_BUNDLE)))
        .thenReturn(List.of(bundle(HQ_BUNDLE, "HQ管理者")));
    when(repository.findById(3L)).thenReturn(Optional.of(existing));
    when(repository.saveAndFlush(existing)).thenReturn(existing);
    PlatformStaffUpdateRequest req =
        updateRequest(Set.of(HQ_BUNDLE), StoreScopeType.ALL_STORES, Set.of());
    req.setEnabled(false);

    Optional<PlatformStaffResponse> res = service.update(3L, req, ACTOR);

    assertThat(existing.getEnabled()).isFalse();
    assertThat(res).isPresent();
    assertThat(res.get().enabled()).isFalse();
    // 授権内容の CHANGE と停止の STOP を別行で記録する（実行主体つき）。
    verify(grantHistoryRepository, times(2)).save(historyCaptor.capture());
    assertThat(historyCaptor.getAllValues())
        .extracting(GrantHistory::getAction)
        .containsExactly(GrantAction.CHANGE, GrantAction.STOP);
  }

  @Test
  void update_reEnabling_resumesUserAndRecordsResumeHistory() {
    PlatformUser existing =
        staff(3L, "target@kizuna.test", Set.of(HQ_BUNDLE), StoreScopeType.ALL_STORES, Set.of());
    existing.stop();
    when(capabilityBundleRepository.findAllById(Set.of(HQ_BUNDLE)))
        .thenReturn(List.of(bundle(HQ_BUNDLE, "HQ管理者")));
    when(repository.findById(3L)).thenReturn(Optional.of(existing));
    when(repository.saveAndFlush(existing)).thenReturn(existing);
    PlatformStaffUpdateRequest req =
        updateRequest(Set.of(HQ_BUNDLE), StoreScopeType.ALL_STORES, Set.of());
    req.setEnabled(true);

    service.update(3L, req, ACTOR);

    assertThat(existing.getEnabled()).isTrue();
    verify(grantHistoryRepository, times(2)).save(historyCaptor.capture());
    assertThat(historyCaptor.getAllValues())
        .extracting(GrantHistory::getAction)
        .containsExactly(GrantAction.CHANGE, GrantAction.RESUME);
  }

  @Test
  void grantHistory_returnsEntriesForStaffTarget() {
    PlatformUser existing =
        staff(3L, "target@kizuna.test", Set.of(HQ_BUNDLE), StoreScopeType.ALL_STORES, Set.of());
    when(repository.findById(3L)).thenReturn(Optional.of(existing));
    GrantHistory entry =
        GrantHistory.builder()
            .platformUserId(3L)
            .actorEmail(ACTOR)
            .action(GrantAction.GRANT)
            .detail("{}")
            .build();
    when(grantHistoryRepository.findByPlatformUserIdOrderByCreatedAtDesc(3L))
        .thenReturn(List.of(entry));

    var res = service.grantHistory(3L);

    assertThat(res).isPresent();
    assertThat(res.get()).hasSize(1);
    assertThat(res.get().get(0).actorEmail()).isEqualTo(ACTOR);
    assertThat(res.get().get(0).action()).isEqualTo(GrantAction.GRANT);
  }

  @Test
  void grantHistory_unknownOrNonStaffTarget_returnsEmpty() {
    when(repository.findById(404L)).thenReturn(Optional.empty());
    when(repository.findById(8L)).thenReturn(Optional.of(castUser(8L, "cast@kizuna.test")));

    assertThat(service.grantHistory(404L)).isEmpty();
    assertThat(service.grantHistory(8L)).isEmpty();
  }
}
