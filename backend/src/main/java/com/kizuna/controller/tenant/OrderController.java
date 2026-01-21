package com.kizuna.controller.tenant;

import com.kizuna.model.dto.tenant.order.OrderCreateRequest;
import com.kizuna.model.dto.tenant.order.OrderResponse;
import com.kizuna.model.dto.tenant.order.OrderUpdateRequest;
import com.kizuna.service.tenant.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant/orders")
@RequiredArgsConstructor
public class OrderController {

  private final OrderService orderService;

  @GetMapping
  public ResponseEntity<Page<OrderResponse>> list(
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    return ResponseEntity.ok(orderService.list(pageable));
  }

  @GetMapping("/{id}")
  public ResponseEntity<OrderResponse> get(@PathVariable String id) {
    return ResponseEntity.ok(orderService.get(id));
  }

  @PostMapping
  public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderCreateRequest request) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
        .body(orderService.create(request));
  }

  @PutMapping("/{id}")
  public ResponseEntity<OrderResponse> update(
      @PathVariable String id, @Valid @RequestBody OrderUpdateRequest request) {
    return ResponseEntity.ok(orderService.update(id, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    orderService.delete(id);
    return ResponseEntity.ok().build();
  }
}
