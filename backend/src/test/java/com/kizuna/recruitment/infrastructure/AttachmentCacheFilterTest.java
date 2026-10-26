package com.kizuna.recruitment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AttachmentCacheFilterTest {
  @Test
  void operationOutcomesKeepCacheHeaders() throws Exception {
    for (int status : new int[] {200, 400, 401, 403, 404, 503}) {
      var request = new MockHttpServletRequest("GET", "/applicants/a/attachment-operations/key");
      var response = new MockHttpServletResponse();
      new AttachmentCacheFilter()
          .doFilter(request, response, (incoming, outgoing) -> response.setStatus(status));
      assertThat(response.getHeader("Cache-Control")).isEqualTo("private, no-store");
      assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }
  }

  @Test
  void encodedMvcPathsAndContextPathShareTheSameHeaderBoundary() throws Exception {
    for (String path :
        new String[] {
          "/applicants/a/%61ttachments",
          "/%61pplicants/a/attachment-%75ploads/u/content",
          "/applicants/attachment-%70olicy"
        }) {
      for (DispatcherType dispatcher :
          new DispatcherType[] {DispatcherType.REQUEST, DispatcherType.ASYNC}) {
        var request = new MockHttpServletRequest("POST", "/api" + path);
        request.setContextPath("/api");
        request.setDispatcherType(dispatcher);
        var response = new MockHttpServletResponse();
        new AttachmentCacheFilter().doFilter(request, response, (incoming, outgoing) -> {});
        assertThat(response.getHeader("Cache-Control")).isEqualTo("private, no-store");
      }
    }
  }

  @Test
  void unrelatedRequestGetsNoAttachmentHeaders() throws Exception {
    var request = new MockHttpServletRequest("GET", "/applicants/a");
    var response = new MockHttpServletResponse();
    new AttachmentCacheFilter().doFilter(request, response, (incoming, outgoing) -> {});
    assertThat(response.getHeader("Cache-Control")).isNull();
  }
}
