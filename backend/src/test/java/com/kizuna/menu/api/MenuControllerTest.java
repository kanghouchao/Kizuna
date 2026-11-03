package com.kizuna.menu.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.menu.application.MenuNode;
import com.kizuna.menu.application.MenuService;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MenuController.class)
@Import(StoreScopeHolder.class)
class MenuControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MenuService menuService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreScopeResolver storeScopeResolver;

  // 見出し節は path を持たない。応答は null の項目を書かないため、節の JSON に path が現れないことまで固定する。
  @Test
  @DisplayName("GET /menus/me はメニュー木を name・path・icon・items の入れ子で返すこと")
  @WithMockUser
  void returnsMenuTree() throws Exception {
    when(menuService.getMyMenus())
        .thenReturn(
            List.of(
                new MenuNode(
                    "CRM",
                    null,
                    "users",
                    List.of(new MenuNode("顧客", "/customers", "user", List.of())))));

    mockMvc
        .perform(get("/menus/me"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    [{"name":"CRM","icon":"users","items":[
                      {"name":"顧客","path":"/customers","icon":"user","items":[]}]}]
                    """,
                    JsonCompareMode.STRICT));
  }
}
