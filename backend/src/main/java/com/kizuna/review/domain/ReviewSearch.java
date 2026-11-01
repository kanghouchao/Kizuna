package com.kizuna.review.domain;

import com.kizuna.review.domain.ReviewValues.PermissionStatus;
import com.kizuna.review.domain.ReviewValues.Status;
import com.kizuna.shared.exception.ServiceException;
import java.util.Locale;
import org.springframework.data.core.TypedPropertyPath;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/** 要求の sort 引数は order として読むため、Pageable からは並びを使わず page と size だけを取る。 */
public record ReviewSearch(
    Pageable page,
    String query,
    String reviewId,
    Status status,
    PermissionStatus permissionStatus,
    Order order) {
  public enum Order {
    RECEIVED_DESC,
    RECEIVED_ASC,
    CREATED_DESC,
    CREATED_ASC
  }

  public ReviewSearch {
    if (order == null) throw new ServiceException("一覧の並び順を確認してください");
    if (query != null) {
      query = query.strip();
      if (query.isEmpty()) query = null;
      else query = ReviewInput.clean(query, 60);
    }
    if (reviewId != null) reviewId = ReviewInput.id(reviewId);
  }

  public PageRequest pageable() {
    boolean asc = order == Order.RECEIVED_ASC || order == Order.CREATED_ASC;
    TypedPropertyPath<ReviewRecord, ?> field =
        order == Order.RECEIVED_ASC || order == Order.RECEIVED_DESC
            ? ReviewRecord::getReceivedAt
            : ReviewRecord::getCreatedAt;
    return PageRequest.of(
        page.getPageNumber(),
        page.getPageSize(),
        Sort.by(asc ? Sort.Direction.ASC : Sort.Direction.DESC, field, ReviewRecord::getId));
  }

  public Specification<ReviewRecord> specification(Long storeId) {
    Specification<ReviewRecord> spec = (root, q, cb) -> cb.equal(root.get("storeId"), storeId);
    if (query != null) {
      String pattern =
          "%"
              + query
                  .toLowerCase(Locale.ROOT)
                  .replace("\\", "\\\\")
                  .replace("%", "\\%")
                  .replace("_", "\\_")
              + "%";
      spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("displayName")), pattern, '\\'));
    }
    if (reviewId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("id"), reviewId));
    if (status != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
    if (permissionStatus != null)
      spec = spec.and((root, q, cb) -> cb.equal(root.get("permissionStatus"), permissionStatus));
    return spec;
  }
}
