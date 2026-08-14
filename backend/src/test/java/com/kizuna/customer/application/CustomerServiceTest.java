package com.kizuna.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.customer.api.dto.CustomerCreateRequest;
import com.kizuna.customer.api.dto.CustomerMapper;
import com.kizuna.customer.api.dto.CustomerResponse;
import com.kizuna.customer.api.dto.CustomerUpdateRequest;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerMergeRepository;
import com.kizuna.customer.domain.CustomerPatch;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkReason;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

  @Mock private CustomerRepository customerRepository;
  @Mock private CustomerMemberLinkRepository customerMemberLinkRepository;
  @Mock private CustomerMergeRepository customerMergeRepository;
  @Mock private CustomerMapper customerMapper;

  @InjectMocks private CustomerService customerService;

  @Test
  void list_returnsPage() {
    Customer c = Customer.builder().name("Test").build();
    Page<Customer> page = new PageImpl<>(List.of(c));

    CustomerResponse resp = new CustomerResponse();
    resp.setName("Test");

    when(customerRepository.findAll(
            ArgumentMatchers.<Specification<Customer>>any(), any(PageRequest.class)))
        .thenReturn(page);
    when(customerMapper.toResponse(c)).thenReturn(resp);

    Page<CustomerResponse> result =
        customerService.list("test", "GOLD", "VIP", PageRequest.of(0, 10));
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getName()).isEqualTo("Test");
  }

  @Test
  void list_withoutFilters_returnsAll() {
    Customer c = Customer.builder().name("All").build();
    Page<Customer> page = new PageImpl<>(List.of(c));

    CustomerResponse resp = new CustomerResponse();
    resp.setName("All");

    when(customerRepository.findAll(
            ArgumentMatchers.<Specification<Customer>>any(), any(PageRequest.class)))
        .thenReturn(page);
    when(customerMapper.toResponse(c)).thenReturn(resp);

    Page<CustomerResponse> result = customerService.list(null, null, null, PageRequest.of(0, 10));
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getName()).isEqualTo("All");
  }

  @Test
  void get_returnsResponse() {
    Customer c = new Customer();
    c.setId("c1");

    CustomerResponse resp = new CustomerResponse();
    resp.setId("c1");

    when(customerRepository.findById("c1")).thenReturn(Optional.of(c));
    when(customerMapper.toResponse(c)).thenReturn(resp);

    assertThat(customerService.get("c1").getId()).isEqualTo("c1");
  }

  @Test
  void get_throwsWhenNotFound() {
    when(customerRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> customerService.get("missing"))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("顧客が見つかりません");
  }

  @Test
  void create_savesAndReturns() {
    CustomerCreateRequest req = new CustomerCreateRequest();
    req.setName("New");

    Customer customerEntity = Customer.builder().name("New").build();

    when(customerMapper.toEntity(req)).thenReturn(customerEntity);

    when(customerRepository.save(any()))
        .thenAnswer(
            i -> {
              Customer saved = i.getArgument(0);
              saved.setId("new_id");
              return saved;
            });

    CustomerResponse resp = new CustomerResponse();
    resp.setId("new_id");
    resp.setName("New");
    when(customerMapper.toResponse(any())).thenReturn(resp);

    CustomerResponse res = customerService.create(req);
    assertThat(res.getId()).isEqualTo("new_id");
    assertThat(res.getName()).isEqualTo("New");
  }

  @Test
  void update_modifiesFields() {
    Customer c = new Customer();
    c.setId("c1");

    when(customerRepository.findById("c1")).thenReturn(Optional.of(c));
    when(customerRepository.save(any())).thenReturn(c);

    CustomerUpdateRequest req = new CustomerUpdateRequest();
    req.setName("Updated");

    when(customerMapper.toPatch(req))
        .thenReturn(
            new CustomerPatch(
                "Updated", null, null, null, null, null, null, null, null, null, null, null));

    CustomerResponse resp = new CustomerResponse();
    resp.setName("Updated");
    when(customerMapper.toResponse(c)).thenReturn(resp);

    CustomerResponse res = customerService.update("c1", req);
    assertThat(c.getName()).isEqualTo("Updated");
    assertThat(res.getName()).isEqualTo("Updated");
  }

  @Test
  void update_throwsWhenNotFound() {
    when(customerRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> customerService.update("missing", new CustomerUpdateRequest()))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("顧客が見つかりません");
  }

  @Test
  @DisplayName("一覧は本ページ分の紐づけを 1 回で引き、紐づけ済みだけに会員コードを載せること")
  void list_decoratesMemberLink() {
    Customer linked = new Customer();
    linked.setId("c1");
    Customer unlinked = new Customer();
    unlinked.setId("c2");
    Page<Customer> page = new PageImpl<>(List.of(linked, unlinked));

    CustomerResponse linkedResponse = new CustomerResponse();
    linkedResponse.setId("c1");
    CustomerResponse unlinkedResponse = new CustomerResponse();
    unlinkedResponse.setId("c2");

    when(customerRepository.findAll(
            ArgumentMatchers.<Specification<Customer>>any(), any(PageRequest.class)))
        .thenReturn(page);
    when(customerMapper.toResponse(linked)).thenReturn(linkedResponse);
    when(customerMapper.toResponse(unlinked)).thenReturn(unlinkedResponse);
    when(customerMemberLinkRepository.findByCustomerIdInAndStatus(
            List.of("c1", "c2"), LinkStatus.ACTIVE))
        .thenReturn(List.of(activeLink("c1", "123456789012")));

    List<CustomerResponse> result =
        customerService.list(null, null, null, PageRequest.of(0, 10)).getContent();

    assertThat(result.get(0).getMemberLinked()).isTrue();
    assertThat(result.get(0).getLinkedMemberCode()).isEqualTo("123456789012");
    assertThat(result.get(1).getMemberLinked()).isFalse();
    assertThat(result.get(1).getLinkedMemberCode()).isNull();
    verify(customerMemberLinkRepository).findByCustomerIdInAndStatus(any(), any());
  }

  @Test
  @DisplayName("詳細は紐づけ済みなら会員コードを載せ、未紐づけでも member_linked が真偽値になること")
  void get_decoratesMemberLink() {
    Customer c = new Customer();
    c.setId("c1");
    CustomerResponse resp = new CustomerResponse();
    resp.setId("c1");

    when(customerRepository.findById("c1")).thenReturn(Optional.of(c));
    when(customerMapper.toResponse(c)).thenReturn(resp);
    when(customerMemberLinkRepository.findByCustomerIdAndStatus("c1", LinkStatus.ACTIVE))
        .thenReturn(Optional.of(activeLink("c1", "123456789012")));

    CustomerResponse linked = customerService.get("c1");
    assertThat(linked.getMemberLinked()).isTrue();
    assertThat(linked.getLinkedMemberCode()).isEqualTo("123456789012");

    when(customerMemberLinkRepository.findByCustomerIdAndStatus("c1", LinkStatus.ACTIVE))
        .thenReturn(Optional.empty());

    CustomerResponse unlinked = customerService.get("c1");
    assertThat(unlinked.getMemberLinked()).isFalse();
    assertThat(unlinked.getLinkedMemberCode()).isNull();
  }

  private static CustomerMemberLink activeLink(String customerId, String memberCode) {
    return CustomerMemberLink.builder()
        .customerId(customerId)
        .memberId(7L)
        .memberCode(memberCode)
        .reason(LinkReason.MEMBER_CODE)
        .linkedBy(1L)
        .linkedAt(OffsetDateTime.parse("2026-07-01T10:00:00+09:00"))
        .build();
  }

  @Test
  void delete_removesIfExists() {
    when(customerRepository.existsById("c1")).thenReturn(true);
    customerService.delete("c1");
    verify(customerRepository).deleteById("c1");
  }

  @Test
  @DisplayName("統合に関与した顧客の削除は 409 で撥ねられ、行が消えないこと")
  void delete_rejectsCustomersInvolvedInAMerge() {
    when(customerRepository.existsById("c1")).thenReturn(true);
    when(customerMergeRepository.existsInvolving("c1")).thenReturn(true);

    assertThatThrownBy(() -> customerService.delete("c1"))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("統合");
    verify(customerRepository, never()).deleteById("c1");
  }

  @Test
  void delete_throwsWhenNotFound() {
    when(customerRepository.existsById("missing")).thenReturn(false);

    assertThatThrownBy(() -> customerService.delete("missing"))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("顧客が見つかりません");
  }
}
