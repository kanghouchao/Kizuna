package com.kizuna.controller.tenant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.model.dto.menu.MenuVO;
import com.kizuna.service.tenant.menu.TenantMenuService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TenantMenuControllerTest {

  private MockMvc mockMvc;

  @Mock private TenantMenuService menuService;

  @InjectMocks private TenantMenuController controller;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void getMyMenus_returnsMenus() throws Exception {
    MenuVO menu = new MenuVO("Orders", "/orders", "icon", List.of());
    when(menuService.getMyMenus()).thenReturn(List.of(menu));

    mockMvc
        .perform(get("/tenant/menus/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Orders"));
  }
}
