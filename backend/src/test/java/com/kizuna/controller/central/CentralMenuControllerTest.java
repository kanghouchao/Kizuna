package com.kizuna.controller.central;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.model.dto.menu.MenuVO;
import com.kizuna.service.central.menu.CentralMenuService;
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
class CentralMenuControllerTest {

  private MockMvc mockMvc;

  @Mock private CentralMenuService menuService;

  @InjectMocks private CentralMenuController controller;

  @BeforeEach
  void setUp() {
    // Standalone setup: NO Spring Context is started
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void getMyMenus_returnsMenus() throws Exception {
    MenuVO menu = new MenuVO("Dashboard", "/dashboard", "icon", List.of());
    when(menuService.getMyMenus()).thenReturn(List.of(menu));

    mockMvc
        .perform(get("/central/menus/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Dashboard"));
  }
}
