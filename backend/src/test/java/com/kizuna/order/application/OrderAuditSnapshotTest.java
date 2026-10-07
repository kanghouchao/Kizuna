package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.audit.recording.AuditChange;
import com.kizuna.order.domain.ContactSnapshot;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderApplication;
import com.kizuna.order.domain.OrderApplicationStatus;
import com.kizuna.order.domain.OrderCourses;
import com.kizuna.order.domain.OrderFeeLineDraft;
import com.kizuna.order.domain.OrderFeeLineKind;
import com.kizuna.order.domain.OrderPatch;
import com.kizuna.order.domain.OrderStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderAuditSnapshotTest {
  @Test
  void retainsBusinessValuesAndOnlyNamesOfPrivateFieldsThatChanged() {
    var order =
        Order.builder()
            .status(OrderStatus.CONFIRMED)
            .remarks("前の自由記述")
            .locationAddress("私的な住所")
            .build();
    order.setId("o1");
    order.adoptCourse(OrderCourses.course("公開しない任意コース名", 60, 1000), List.of());
    order.replaceContact(new ContactSnapshot("前の氏名", null, "before@example.test", null));
    var before = OrderAuditSnapshot.of(order);
    order.replaceContact(new ContactSnapshot("後の氏名", null, "after@example.test", null));
    order.apply(new OrderPatch(null, null, null, 3, null, null, null, null, null, "後の自由記述", null));
    var after = OrderAuditSnapshot.of(order).after(before);

    assertThat(before.values()).containsEntry("pax", "").containsEntry("total_fee", "1000");
    assertThat(after)
        .containsEntry("pax", "3")
        .containsEntry("redacted_fields_changed", "contact,remarks");
    assertThat(before.values().toString() + after)
        .doesNotContain(
            "前の氏名",
            "後の氏名",
            "before@example.test",
            "after@example.test",
            "私的な住所",
            "前の自由記述",
            "後の自由記述",
            "公開しない任意コース名");
    var event =
        new AuditChange(
            new AuditActor(1L, "STAFF", "担当"),
            1L,
            "ORDER_UPDATED",
            "ORDER",
            "o1",
            null,
            null,
            before.values(),
            after);
    assertThat(event.afterValues()).hasSizeLessThan(32);
    assertThat(before.values()).doesNotContainKey("redacted_fields_changed");
  }

  @Test
  void recordsSameTotalChangesToFeeCompositionWithoutFreeTextNames() {
    var order = Order.builder().status(OrderStatus.CONFIRMED).build();
    order.adoptCourse(
        OrderCourses.course("コース名", 60, 1000),
        List.of(new OrderFeeLineDraft(OrderFeeLineKind.CREDIT_SURCHARGE, "前の明細名", 200)));
    var before = OrderAuditSnapshot.of(order);
    order.replaceStoreFeeLines(
        List.of(
            new OrderFeeLineDraft(OrderFeeLineKind.CREDIT_SURCHARGE, "非公開の加算名", 300),
            new OrderFeeLineDraft(OrderFeeLineKind.DISCOUNT, "非公開の割引名", -100)));
    var after = OrderAuditSnapshot.of(order).after(before);

    assertThat(before.values().get("total_fee")).isEqualTo(after.get("total_fee"));
    assertThat(before.values().get("fee_lines")).contains("amount=200");
    assertThat(after.get("fee_lines"))
        .contains("amount=300", "kind=DISCOUNT,amount=-100")
        .doesNotContain("明細名", "加算名", "割引名");
    assertThat(after).containsEntry("redacted_fields_changed", "fee_line_names");
  }

  @Test
  void detectsNamesExchangedBetweenDifferentFeeLinesWithoutRecordingTheirValues() {
    var order = Order.builder().status(OrderStatus.CONFIRMED).build();
    order.adoptCourse(
        OrderCourses.course("コース名", 60, 1000),
        List.of(
            new OrderFeeLineDraft(OrderFeeLineKind.CREDIT_SURCHARGE, "私的名称甲", 200),
            new OrderFeeLineDraft(OrderFeeLineKind.DISCOUNT, "私的名称乙", -100)));
    var before = OrderAuditSnapshot.of(order);
    order.replaceStoreFeeLines(
        List.of(
            new OrderFeeLineDraft(OrderFeeLineKind.CREDIT_SURCHARGE, "私的名称乙", 200),
            new OrderFeeLineDraft(OrderFeeLineKind.DISCOUNT, "私的名称甲", -100)));
    var after = OrderAuditSnapshot.of(order).after(before);

    assertThat(after).containsEntry("redacted_fields_changed", "fee_line_names");
    assertThat(after.toString()).doesNotContain("私的名称甲", "私的名称乙");
  }

  @Test
  void changingOnlyCourseAmountDoesNotReportANameChange() {
    var order = Order.builder().status(OrderStatus.CONFIRMED).build();
    order.adoptCourse(OrderCourses.course("同じコース名", 60, 1000), List.of());
    order.getFeeLines().getFirst().setId("course-line");
    var before = OrderAuditSnapshot.of(order);
    order.adoptCourse(OrderCourses.course("同じコース名", 60, 1500), List.of());
    var after = OrderAuditSnapshot.of(order).after(before);

    assertThat(after).containsEntry("total_fee", "1500");
    assertThat(after).doesNotContainKey("redacted_fields_changed");
  }

  @Test
  void applicationSnapshotDoesNotExposeTheOriginalRequestOrContact() {
    var application =
        OrderApplication.builder()
            .status(OrderApplicationStatus.PENDING)
            .businessDate(LocalDate.of(2026, 10, 7))
            .requesterMemberId(10L)
            .requesterDeclaredName("申請時の氏名")
            .remarks("申請の原文")
            .contactEmail("private@example.test")
            .contactName("私的な氏名")
            .build();
    var values = OrderAuditSnapshot.application(application);

    assertThat(values)
        .containsEntry("requester_member_id", "10")
        .containsEntry("status", "PENDING")
        .containsEntry("contact_import_count", "0");
    assertThat(values.toString())
        .doesNotContain("申請時の氏名", "申請の原文", "private@example.test", "私的な氏名");
    assertThat(OrderAuditSnapshot.of(Order.builder().build()).after(null))
        .doesNotContainKey("redacted_fields_changed");
  }
}
