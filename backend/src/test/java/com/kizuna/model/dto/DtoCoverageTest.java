package com.kizuna.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.model.dto.auth.Token;
import com.kizuna.model.dto.menu.MenuVO;
import com.kizuna.model.dto.tenant.order.OrderResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class DtoCoverageTest {

  @Test
  void testRecordsAndBuilders() {
    Token token = new Token("t", 1L);
    assertThat(token.token()).isEqualTo("t");
    assertThat(token.expiresAt()).isEqualTo(1L);

    MenuVO menu = new MenuVO("n", "p", "i", List.of());
    assertThat(menu.getName()).isEqualTo("n");
    assertThat(menu.getPath()).isEqualTo("p");
    assertThat(menu.getIcon()).isEqualTo("i");
    assertThat(menu.getItems()).isEmpty();

    OrderResponse order =
        OrderResponse.builder().id("id").locationAddress("addr").locationBuilding("bld").build();
    assertThat(order.getId()).isEqualTo("id");
    assertThat(order.getLocationAddress()).isEqualTo("addr");
    assertThat(order.getLocationBuilding()).isEqualTo("bld");
  }
}
