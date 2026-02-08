package com.kizuna.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.model.entity.central.menu.CentralMenu;
import com.kizuna.model.entity.tenant.Cast;
import com.kizuna.model.entity.tenant.Customer;
import com.kizuna.model.entity.tenant.Order;
import com.kizuna.model.entity.tenant.menu.TenantMenu;
import com.kizuna.model.entity.tenant.security.TenantUser;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class EntityCoverageTest {

  @Test
  void testEntities() {
    // CentralMenu
    CentralMenu cm = new CentralMenu();
    cm.setId(1L);
    cm.setLabel("L");
    cm.setPath("/p");
    cm.setIcon("i");
    cm.setPermission("P");
    cm.setSortOrder(1);
    cm.setChildren(new ArrayList<>());
    assertThat(cm.getLabel()).isEqualTo("L");
    assertThat(cm.getPath()).isEqualTo("/p");
    assertThat(cm.getIcon()).isEqualTo("i");
    assertThat(cm.getPermission()).isEqualTo("P");
    assertThat(cm.getSortOrder()).isEqualTo(1);
    assertThat(cm.getChildren()).isEmpty();

    // TenantMenu
    TenantMenu tm = new TenantMenu();
    tm.setId("id");
    tm.setLabel("L");
    tm.setTenantId(1L);
    tm.setChildren(new ArrayList<>());
    assertThat(tm.getLabel()).isEqualTo("L");
    assertThat(tm.getTenantId()).isEqualTo(1L);

    // Customer
    Customer cust = new Customer();
    cust.setName("N");
    cust.setPhoneNumber("123");
    cust.setPoints(10);
    assertThat(cust.getName()).isEqualTo("N");
    assertThat(cust.getPhoneNumber()).isEqualTo("123");
    assertThat(cust.getPoints()).isEqualTo(10);

    // Order
    Order o = new Order();
    o.setStoreName("S");
    o.setRemarks("R");
    o.setStatus("S");
    assertThat(o.getStoreName()).isEqualTo("S");
    assertThat(o.getRemarks()).isEqualTo("R");
    assertThat(o.getStatus()).isEqualTo("S");

    // Cast
    Cast g = new Cast();
    g.setName("G");
    g.setStatus("A");
    assertThat(g.getName()).isEqualTo("G");
    assertThat(g.getStatus()).isEqualTo("A");

    // TenantUser
    TenantUser tu = new TenantUser();
    tu.setEmail("e");
    tu.setNickname("n");
    assertThat(tu.getEmail()).isEqualTo("e");
    assertThat(tu.getNickname()).isEqualTo("n");
  }
}
