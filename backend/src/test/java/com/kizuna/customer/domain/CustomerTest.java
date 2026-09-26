package com.kizuna.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CustomerTest {

  @Test
  void apply_updatesAllFields() {
    Customer customer =
        Customer.builder()
            .name("旧名")
            .address("旧住所")
            .buildingName("旧ビル")
            .classification("旧区分")
            .hasPet(false)
            .usageAreas("旧エリア")
            .ngType("注意")
            .ngContent("旧NG")
            .build();

    customer.apply(new CustomerPatch("新名", "新住所", "新ビル", null, "新区分", true, "新エリア", "禁止", "新NG"));

    assertThat(customer.getName()).isEqualTo("新名");
    assertThat(customer.getAddress()).isEqualTo("新住所");
    assertThat(customer.getBuildingName()).isEqualTo("新ビル");
    assertThat(customer.getClassification()).isEqualTo("新区分");
    assertThat(customer.getHasPet()).isTrue();
    assertThat(customer.getUsageAreas()).isEqualTo("新エリア");
    assertThat(customer.getNgType()).isEqualTo("禁止");
    assertThat(customer.getNgContent()).isEqualTo("新NG");
  }

  @Test
  void apply_nullFieldsKeepCurrentValues() {
    Customer customer = Customer.builder().name("名前").hasPet(true).build();

    customer.apply(new CustomerPatch(null, null, null, null, null, null, null, null, null));

    assertThat(customer.getName()).isEqualTo("名前");
    assertThat(customer.getHasPet()).isTrue();
  }
}
