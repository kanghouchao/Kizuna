package com.kizuna.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CustomerTest {

  @Test
  void apply_updatesAllFields() {
    Customer customer =
        Customer.builder()
            .name("旧名")
            .phoneNumber("090-0000-0000")
            .phoneNumber2("080-0000-0000")
            .address("旧住所")
            .buildingName("旧ビル")
            .classification("旧区分")
            .hasPet(false)
            .rank("SILVER")
            .lineId("old_line")
            .usageAreas("旧エリア")
            .ngType("注意")
            .ngContent("旧NG")
            .build();

    customer.apply(
        new CustomerPatch(
            "新名",
            "090-1111-1111",
            "080-1111-1111",
            "新住所",
            "新ビル",
            "新区分",
            true,
            "GOLD",
            "new_line",
            "新エリア",
            "禁止",
            "新NG"));

    assertThat(customer.getName()).isEqualTo("新名");
    assertThat(customer.getPhoneNumber()).isEqualTo("090-1111-1111");
    assertThat(customer.getPhoneNumber2()).isEqualTo("080-1111-1111");
    assertThat(customer.getAddress()).isEqualTo("新住所");
    assertThat(customer.getBuildingName()).isEqualTo("新ビル");
    assertThat(customer.getClassification()).isEqualTo("新区分");
    assertThat(customer.getHasPet()).isTrue();
    assertThat(customer.getRank()).isEqualTo("GOLD");
    assertThat(customer.getLineId()).isEqualTo("new_line");
    assertThat(customer.getUsageAreas()).isEqualTo("新エリア");
    assertThat(customer.getNgType()).isEqualTo("禁止");
    assertThat(customer.getNgContent()).isEqualTo("新NG");
  }

  @Test
  void apply_nullFieldsKeepCurrentValues() {
    Customer customer =
        Customer.builder().name("名前").rank("GOLD").lineId("line").hasPet(true).build();

    customer.apply(
        new CustomerPatch(null, null, null, null, null, null, null, null, null, null, null, null));

    assertThat(customer.getName()).isEqualTo("名前");
    assertThat(customer.getRank()).isEqualTo("GOLD");
    assertThat(customer.getLineId()).isEqualTo("line");
    assertThat(customer.getHasPet()).isTrue();
  }
}
