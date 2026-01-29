package com.kizuna.controller.central;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.model.dto.menu.MenuVO;
import com.kizuna.service.central.menu.CentralMenuService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CentralMenuController.class)
class CentralMenuControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockBean private CentralMenuService menuService;

  @Test
  @WithMockUser(roles = "ADMIN")
  void getMyMenus_returnsMenus() throws Exception {
    MenuVO menu = new MenuVO("Dashboard", "/dashboard", "icon", List.of());
    when(menuService.getMyMenus()).thenReturn(List.of(menu));

    mockMvc
        .perform(get("/central/menus/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Dashboard"));
  }
}
