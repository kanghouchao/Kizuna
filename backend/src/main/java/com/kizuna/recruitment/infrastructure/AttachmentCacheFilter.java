package com.kizuna.recruitment.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AttachmentCacheFilter extends OncePerRequestFilter {
  private static final List<PathPattern> ROUTES =
      Stream.of(
              "/store/applicants/attachment-policy",
              "/store/applicants/{id}/attachments",
              "/store/applicants/{id}/attachments/{attachmentId}/content",
              "/store/applicants/{id}/attachment-uploads",
              "/store/applicants/{id}/attachment-operations/{key}",
              "/store/applicants/{id}/attachment-uploads/{uploadId}/content")
          .map(PathPatternParser.defaultInstance::parse)
          .toList();

  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    return false;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain chain)
      throws IOException, ServletException {
    var path =
        PathContainer.parsePath(
            request.getRequestURI().substring(request.getContextPath().length()));
    if (ROUTES.stream().anyMatch(route -> route.matches(path))) {
      response.setHeader("Cache-Control", "private, no-store");
      response.setHeader("X-Content-Type-Options", "nosniff");
    }
    chain.doFilter(request, response);
  }
}
