package com.kizuna.recruitment.api.store;

import com.kizuna.recruitment.api.dto.AttachmentPolicyResponse;
import com.kizuna.recruitment.api.dto.AttachmentSummaryResponse;
import com.kizuna.recruitment.api.dto.AttachmentUploadResponse;
import com.kizuna.recruitment.application.AttachmentService;
import com.kizuna.recruitment.infrastructure.AttachmentBodyReceiver;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScopeExecutor;
import com.kizuna.shared.web.CursorPage;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.security.Principal;
import java.util.UUID;
import java.util.concurrent.Callable;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.CloseableThreadContext;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.concurrent.DelegatingSecurityContextCallable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.WebAsyncTask;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/store/applicants")
@RequiredArgsConstructor
public class AttachmentController {
  private final AttachmentService attachments;
  private final AttachmentBodyReceiver receiver;
  private final StoreContext storeContext;
  private final StoreScopeExecutor scope;
  private final AppProperties properties;

  @GetMapping("/attachment-policy")
  @PreAuthorize(AttachmentService.READ)
  public AttachmentPolicyResponse policy() {
    return attachments.policy();
  }

  @GetMapping("/{id}/attachments")
  @PreAuthorize(AttachmentService.READ)
  public CursorPage<AttachmentSummaryResponse> list(
      @PathVariable String id,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return attachments.list(id, cursor, size);
  }

  @GetMapping("/{id}/attachment-uploads")
  @PreAuthorize(AttachmentService.WRITE)
  public CursorPage<AttachmentUploadResponse> uploads(
      @PathVariable String id,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return attachments.uploads(id, cursor, size);
  }

  @PostMapping("/{id}/attachments")
  @PreAuthorize(AttachmentService.WRITE)
  public WebAsyncTask<ResponseEntity<AttachmentSummaryResponse>> upload(
      @PathVariable String id,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      HttpServletRequest request,
      Principal principal) {
    return receive(id, null, key, request, principal.getName());
  }

  @PutMapping("/{id}/attachment-uploads/{uploadId}/content")
  @PreAuthorize(AttachmentService.WRITE)
  public WebAsyncTask<ResponseEntity<AttachmentSummaryResponse>> recover(
      @PathVariable String id,
      @PathVariable String uploadId,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      HttpServletRequest request,
      Principal principal) {
    return receive(id, uploadId, key, request, principal.getName());
  }

  private WebAsyncTask<ResponseEntity<AttachmentSummaryResponse>> receive(
      String id, String uploadId, String rawKey, HttpServletRequest request, String actorEmail) {
    UUID key = attachments.preflight(id, uploadId, rawKey);
    var admission = receiver.admit(request);
    Long storeId = storeContext.getStoreId();
    var loggingContext = ThreadContext.getImmutableContext();
    Callable<ResponseEntity<AttachmentSummaryResponse>> callable =
        new DelegatingSecurityContextCallable<>(
            () -> {
              try (var ignored = CloseableThreadContext.putAll(loggingContext)) {
                return scope.runInStore(
                    storeId,
                    () -> {
                      try (admission) {
                        var received = admission.receive(request);
                        var completed =
                            attachments.upload(
                                id,
                                uploadId,
                                key,
                                received.path(),
                                received.originalSha256(),
                                received.mediaType(),
                                actorEmail);
                        int status = uploadId == null && completed.created() ? 201 : 200;
                        return ResponseEntity.status(status).body(completed.attachment());
                      }
                    });
              }
            });
    var task =
        new WebAsyncTask<ResponseEntity<AttachmentSummaryResponse>>(
            requestTimeoutMillis(), callable);
    task.onCompletion(admission::close);
    task.onTimeout(
        () -> {
          admission.close();
          throw new ServiceUnavailableException("画像の処理が制限時間を超えました");
        });
    return task;
  }

  private long requestTimeoutMillis() {
    var settings = properties.getPrivateAttachments();
    return 1000L
        * (settings.getReceiveTimeoutSeconds()
            + settings.getImageProcessingTimeoutSeconds()
            + 3L * settings.getStorageTimeoutSeconds()
            + settings.getStorageAttemptTimeoutSeconds());
  }

  @GetMapping("/{id}/attachments/{attachmentId}/content")
  @PreAuthorize(AttachmentService.READ)
  public ResponseEntity<StreamingResponseBody> download(
      @PathVariable String id, @PathVariable String attachmentId, HttpServletRequest request) {
    var download = attachments.download(id, attachmentId, "HEAD".equals(request.getMethod()));
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType(download.mediaType()));
    headers.setContentLength(download.sizeBytes());
    headers.setContentDisposition(
        ContentDisposition.attachment()
            .filename(
                "attachment-"
                    + attachmentId
                    + (download.mediaType().equals("image/png") ? ".png" : ".jpg"))
            .build());
    headers.set("X-Content-Type-Options", "nosniff");
    headers.set("Accept-Ranges", "none");
    if (download.file() == null) return ResponseEntity.ok().headers(headers).build();
    WebAsyncUtils.getAsyncManager(request)
        .registerCallableInterceptor(
            "attachment-file",
            new CallableProcessingInterceptor() {
              @Override
              public <T> void afterCompletion(NativeWebRequest webRequest, Callable<T> task)
                  throws Exception {
                download.file().close();
              }
            });
    return ResponseEntity.ok()
        .headers(headers)
        .body(
            output -> {
              try (var file = download.file()) {
                Files.copy(file.path(), output);
              }
            });
  }
}
