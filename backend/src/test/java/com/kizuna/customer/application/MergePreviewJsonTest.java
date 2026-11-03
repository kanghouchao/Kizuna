package com.kizuna.customer.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.customer.api.dto.CustomerMergePreviewResponse;
import com.kizuna.customer.application.CustomerViews.MergePreview;
import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.LinkReason;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.customer.domain.MergePreferences;
import com.kizuna.customer.domain.MergeProfile;
import com.kizuna.customer.domain.MergeSnapshot;
import com.kizuna.shared.config.AppProperties;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.json.JsonMapper;

/** 統合の確認値は確認内容の JSON への署名なので、application の記録型と応答 DTO が同じ JSON を書き出すことを固定する。 */
@JsonTest
class MergePreviewJsonTest {
  private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-03T01:02:03.123456Z");
  private static final MergeProfile PROFILE =
      new MergeProfile("山田", "住所", "建物", "目印", "VIP", true, "北区", "NG", "内容");
  private static final MergeSnapshot SNAPSHOT =
      new MergeSnapshot(
          "c1",
          PROFILE,
          List.of(
              new MergeSnapshot.Contact(
                  "ct1",
                  "c1",
                  "c2",
                  ContactType.EMAIL,
                  "a@example.test",
                  true,
                  false,
                  ContactPermissionStatus.ALLOWED,
                  ContactPermissionStatus.DENIED,
                  ContactPermissionStatus.ALLOWED,
                  ContactPermissionStatus.DENIED,
                  AT,
                  AT)),
          List.of(
              new MergeSnapshot.Link(
                  "l1",
                  "c1",
                  "123456789012",
                  LinkStatus.ACTIVE,
                  LinkReason.MEMBER_CODE,
                  "理由",
                  null,
                  1L,
                  null,
                  AT,
                  "田中",
                  null,
                  null)));
  private static final MergePreview PREVIEW =
      new MergePreview(
          SNAPSHOT,
          SNAPSHOT,
          PROFILE,
          new MergePreferences(null, "ct1", null),
          List.of(ContactType.PHONE),
          true,
          "123456789012",
          120L,
          2L,
          3,
          1,
          1,
          null);
  private static final String PROFILE_JSON =
      "{\"name\":\"山田\",\"address\":\"住所\",\"building_name\":\"建物\",\"landmark\":\"目印\","
          + "\"classification\":\"VIP\",\"has_pet\":true,\"usage_areas\":\"北区\",\"ng_type\":\"NG\","
          + "\"ng_content\":\"内容\"}";
  private static final String SNAPSHOT_JSON =
      "{\"id\":\"c1\",\"profile\":"
          + PROFILE_JSON
          + ",\"contacts\":[{\"id\":\"ct1\",\"customer_id\":\"c1\",\"origin_customer_id\":\"c2\","
          + "\"type\":\"EMAIL\",\"value\":\"a@example.test\",\"preferred\":true,\"deleted\":false,"
          + "\"business_status\":\"ALLOWED\",\"marketing_status\":\"DENIED\","
          + "\"effective_business_status\":\"ALLOWED\",\"effective_marketing_status\":\"DENIED\","
          + "\"created_at\":\"2026-11-03T01:02:03.123456Z\",\"updated_at\":\"2026-11-03T01:02:03.123456Z\"}],"
          + "\"member_links\":[{\"id\":\"l1\",\"customer_id\":\"c1\",\"member_code\":\"123456789012\","
          + "\"status\":\"ACTIVE\",\"reason\":\"MEMBER_CODE\",\"operation_reason\":\"理由\",\"linked_by\":1,"
          + "\"linked_at\":\"2026-11-03T01:02:03.123456Z\",\"linked_by_name\":\"田中\"}]}";
  private static final String SIGNED =
      "{\"surviving\":"
          + SNAPSHOT_JSON
          + ",\"merged\":"
          + SNAPSHOT_JSON
          + ",\"profile\":"
          + PROFILE_JSON
          + ",\"preferred_contacts\":{\"phone\":null,\"email\":\"ct1\",\"line\":null},"
          + "\"preference_conflicts\":[\"PHONE\"],\"member_linked\":true,"
          + "\"final_member_code\":\"123456789012\",\"point_balance\":120,\"unfinished_order_count\":2,"
          + "\"moved_order_count\":3,\"moved_contact_count\":1,\"moved_link_count\":1}";

  @Autowired JsonMapper mapper;

  @Test
  void writesTheSameJsonAsTheWireResponse() {
    assertThat(mapper.writeValueAsString(PREVIEW))
        .isEqualTo(mapper.writeValueAsString(CustomerMergePreviewResponse.of(PREVIEW)));
  }

  @Test
  void keepsTheShapeTheConfirmationSigns() {
    assertThat(mapper.writeValueAsString(PREVIEW)).isEqualTo(SIGNED);
  }

  @Test
  void keepsTheConfirmationValue() {
    var properties = new AppProperties();
    var jwt = new AppProperties.Jwt();
    jwt.setSecret("merge-confirmation-test-secret-0123456789");
    properties.setJwt(jwt);
    assertThat(new MergeConfirmation(mapper, properties).sign(7L, List.of(PREVIEW, List.of())))
        .isEqualTo("Y5WrJP5zAoIpKbCSLy1nOQrATIJlTZG510h48yEELts");
  }
}
