package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.customer.application.MemberCustomerConflictException;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.order.api.dto.OrderApplicationConfirmationRequest;
import com.kizuna.order.application.OrderService;
import com.kizuna.order.domain.OrderApplicationRepository;
import com.kizuna.order.domain.OrderApplicationStatus;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.shared.storescope.StoreContext;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

class CustomerProvisioningRollbackIT extends CrossStoreTestSupport {
  @Autowired OrderService orders;
  @Autowired OrderRepository orderRepository;
  @Autowired OrderApplicationRepository applications;
  @Autowired CustomerRepository customers;
  @Autowired CustomerMemberLinkRepository links;
  @Autowired StoreContext storeContext;
  @Autowired PlatformTransactionManager transactionManager;

  @Test
  void failureRollsBackCustomerLinkOrderAndApplicationTogether() {
    String nonce = UUID.randomUUID().toString();
    String email = nonce + "@kizuna.test";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    var registered =
        rest.postForEntity(
            "/platform/members",
            new HttpEntity<>(
                "{\"email\":\""
                    + email
                    + "\",\"password\":\""
                    + nonce
                    + "\",\"display_name\":\"巻戻し\"}",
                headers),
            JsonNode.class);
    assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    var login =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                "{\"email\":\"" + email + "\",\"password\":\"" + nonce + "\"}", headers),
            JsonNode.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    headers.setBearerAuth(login.getBody().path("token").asString());
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Tokyo"));
    var requested =
        rest.postForEntity(
            "/platform/me/order-applications",
            new HttpEntity<>(
                "{\"store_id\":1,\"business_date\":\""
                    + today
                    + "\",\"pax\":1,\"declared_name\":\"巻戻し"
                    + nonce
                    + "\"}",
                headers),
            JsonNode.class);
    assertThat(requested.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String id = requested.getBody().path("id").asString();
    long customerCount = customers.count();
    long linkCount = links.count();
    long orderCount = orderRepository.count();
    OrderApplicationConfirmationRequest input = new OrderApplicationConfirmationRequest();
    input.setBusinessDate(today);
    input.setPax(1);
    storeContext.setStoreId(STORE_A);
    try {
      assertThatThrownBy(
              () ->
                  new TransactionTemplate(transactionManager)
                      .executeWithoutResult(
                          status -> {
                            orders.confirmApplication(id, input, "yamada.jiro@kizuna.test");
                            throw new MemberCustomerConflictException();
                          }))
          .isInstanceOf(MemberCustomerConflictException.class);
    } finally {
      storeContext.clear();
    }
    assertThat(customers.count()).isEqualTo(customerCount);
    assertThat(links.count()).isEqualTo(linkCount);
    assertThat(orderRepository.count()).isEqualTo(orderCount);
    var application = applications.findById(id).orElseThrow();
    assertThat(application.getStatus()).isEqualTo(OrderApplicationStatus.PENDING);
    assertThat(application.getOrderId()).isNull();
    storeContext.setStoreId(STORE_A);
    try {
      assertThat(orders.confirmApplication(id, input, "yamada.jiro@kizuna.test").getId())
          .isNotBlank();
    } finally {
      storeContext.clear();
    }
    assertThat(customers.count()).isEqualTo(customerCount + 1);
    assertThat(links.count()).isEqualTo(linkCount + 1);
    assertThat(orderRepository.count()).isEqualTo(orderCount + 1);
    assertThat(applications.findById(id).orElseThrow().getStatus())
        .isEqualTo(OrderApplicationStatus.CONFIRMED);
  }
}
