package com.kizuna.shared.publication;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** 公開一覧の並びと半開区間を、種別を持たない試験用の表で実 PostgreSQL に対して一度だけ検証する。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublicationIntegrationTest {
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-06-01T00:00:00Z");

  private ConfigurableApplicationContext context;
  private Posters posters;

  @BeforeAll
  void connect() {
    context =
        new SpringApplicationBuilder(Config.class)
            .web(WebApplicationType.NONE)
            .properties(
                IntegrationTestDatabase.springDataSourceProperties(
                    PublicationIntegrationTest.class))
            .properties(
                Map.of(
                    "spring.config.name", "publication-test",
                    "spring.liquibase.enabled", "false",
                    "spring.jpa.hibernate.ddl-auto", "create",
                    "spring.jpa.mapping-resources", "publication-test-orm.xml",
                    "logging.level.root", "WARN"))
            .run();
    posters = context.getBean(Posters.class);
    var older = NOW.minusDays(2);
    var newer = NOW.minusDays(1);
    posters.saveAll(
        List.of(
            new Poster(1L, 1, new Publication(true, null, null, null)),
            new Poster(2L, 1, new Publication(true, older, null, null)),
            new Poster(3L, 1, new Publication(true, newer, null, null)),
            new Poster(4L, 1, new Publication(true, null, null, 2)),
            new Poster(5L, 1, new Publication(true, older, null, 1)),
            new Poster(6L, 1, new Publication(true, null, null, 1)),
            new Poster(7L, 1, new Publication(false, null, null, 0)),
            new Poster(8L, 1, new Publication(true, NOW, null, 0)),
            new Poster(9L, 1, new Publication(true, null, NOW, 0)),
            new Poster(10L, 1, new Publication(true, NOW.plusMinutes(1), null, 0)),
            new Poster(11L, 1, new Publication(true, null, null, null)),
            new Poster(12L, 2, new Publication(true, NOW, null, 0))));
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @Test
  void listingKeepsStartExcludesEndAndOrdersTotallyUnderThePrerequisites() {
    var all = list(PageRequest.of(0, 100));
    assertThat(all.getContent())
        .extracting(Poster::getId)
        .containsExactly(8L, 5L, 6L, 4L, 3L, 2L, 1L, 11L);

    var second = list(PageRequest.of(1, 3));
    assertThat(second.getContent()).extracting(Poster::getId).containsExactly(4L, 3L, 2L);
    assertThat(second.getTotalElements()).isEqualTo(8);
  }

  @Test
  void listingIgnoresTheCallersSort() {
    assertThat(list(PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, Poster::getId))).getContent())
        .extracting(Poster::getId)
        .containsExactly(8L, 5L, 6L);
  }

  private Page<Poster> list(PageRequest pageable) {
    Specification<Poster> shelf = (root, q, cb) -> cb.equal(root.get("shelf"), 1);
    var listing = Publication.listing(Poster::getPublication, Poster::getId, shelf, NOW, pageable);
    return posters.findAll(listing.specification(), listing.pageable());
  }

  /** 他の統合テストの実体走査に拾われないよう、注釈ではなく publication-test-orm.xml で写像する。 */
  @Getter
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  static class Poster {
    private Long id;
    private int shelf;
    private Publication publication;

    Poster(Long id, int shelf, Publication publication) {
      this.id = id;
      this.shelf = shelf;
      this.publication = publication;
    }
  }

  interface Posters extends JpaRepository<Poster, Long>, JpaSpecificationExecutor<Poster> {}

  @Configuration
  @EnableAutoConfiguration
  @EnableJpaRepositories(
      basePackageClasses = PublicationIntegrationTest.class,
      considerNestedRepositories = true)
  static class Config {}
}
