package com.kizuna.recruitment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.storescope.StoreContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AttachmentCacheFilterTest {
  @Test
  void encodedMvcPathsAndContextPathShareTheSameCleanupBoundary() throws Exception {
    for (String path :
        new String[] {
          "/store/applicants/a/%61ttachments",
          "/%73tore/%61pplicants/a/attachment-%75ploads/u/content",
          "/store/applicants/attachment-%70olicy"
        }) {
      for (DispatcherType dispatcher :
          new DispatcherType[] {DispatcherType.REQUEST, DispatcherType.ASYNC}) {
        var context = new StoreContext();
        var request = new MockHttpServletRequest("POST", "/api" + path);
        request.setContextPath("/api");
        request.setDispatcherType(dispatcher);
        var response = new MockHttpServletResponse();
        new AttachmentCacheFilter(context)
            .doFilter(request, response, (incoming, outgoing) -> context.setStoreId(1L));
        assertThat(context.hasStoreId()).isFalse();
        assertThat(response.getHeader("Cache-Control")).isEqualTo("private, no-store");
      }
    }
  }

  @Test
  void unrelatedRequestContextIsNotOwnedByAttachmentFilter() throws Exception {
    var context = new StoreContext();
    var request = new MockHttpServletRequest("GET", "/store/applicants/a");
    var response = new MockHttpServletResponse();
    try {
      new AttachmentCacheFilter(context)
          .doFilter(request, response, (incoming, outgoing) -> context.setStoreId(2L));
      assertThat(context.getStoreId()).isEqualTo(2L);
      assertThat(response.getHeader("Cache-Control")).isNull();
    } finally {
      context.clear();
    }
  }

  @Test
  void initialAndAsyncDispatchReleaseTheirOwnThreadStoreContext() throws Exception {
    for (DispatcherType dispatcher :
        new DispatcherType[] {DispatcherType.REQUEST, DispatcherType.ASYNC}) {
      var context = new StoreContext();
      var request = new MockHttpServletRequest("POST", "/store/applicants/a/attachments");
      request.setDispatcherType(dispatcher);
      request.setAsyncSupported(true);
      var response = new MockHttpServletResponse();
      new AttachmentCacheFilter(context)
          .doFilter(
              request,
              response,
              (incoming, outgoing) -> {
                context.setStoreId(1L);
                if (dispatcher == DispatcherType.REQUEST) incoming.startAsync();
              });
      assertThat(context.hasStoreId()).isFalse();
      assertThat(response.getHeader("Cache-Control")).isEqualTo("private, no-store");
    }
  }

  @Test
  void failedDispatchAlsoClearsContext() {
    var context = new StoreContext();
    var request = new MockHttpServletRequest("GET", "/store/applicants/a/attachments/x/content");
    assertThatThrownBy(
            () ->
                new AttachmentCacheFilter(context)
                    .doFilter(
                        request,
                        new MockHttpServletResponse(),
                        (incoming, outgoing) -> {
                          context.setStoreId(2L);
                          throw new ServletException("検証用失敗");
                        }))
        .isInstanceOf(ServletException.class);
    assertThat(context.hasStoreId()).isFalse();
  }
}
