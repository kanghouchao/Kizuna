package com.kizuna.customer.application;

import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkReason;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScopeExempt;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NamedInterface("application")
public class CustomerProvisioningService {
  private final CustomerRepository customerRepository;
  private final CustomerMemberLinkRepository customerMemberLinkRepository;
  private final CustomerReferenceResolver customerReferenceResolver;
  private final StoreContext storeContext;

  @StoreScopeExempt(reason = "MANDATORY で呼出元の店舗境界とトランザクションに参加する")
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<String> resolveStoreCustomer(StoreCustomerInput input) {
    if (input.customerId() != null && !input.customerId().isEmpty()) {
      return Optional.of(customerReferenceResolver.resolveForWrite(input.customerId()));
    }
    if (input.phoneNumber() == null || input.phoneNumber().isEmpty()) {
      return Optional.empty();
    }
    List<String> matched =
        customerRepository.findAliveIdsByPhoneNumberAndStoreId(
            input.phoneNumber(), storeContext.getStoreId());
    if (matched.size() == 1) {
      return Optional.of(customerReferenceResolver.resolveForWrite(matched.getFirst()));
    }
    // 同店同号は正規に存在するため、複数一致から一人を選ぶと誤帰属になる。
    if (!matched.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        customerRepository
            .save(
                Customer.builder()
                    .name(input.name())
                    .phoneNumber(input.phoneNumber())
                    .phoneNumber2(input.phoneNumber2())
                    .address(input.address())
                    .buildingName(input.buildingName())
                    .landmark(input.landmark())
                    .classification(input.classification())
                    .hasPet(input.hasPet())
                    .ngType(input.ngType())
                    .ngContent(input.ngContent())
                    .build())
            .getId());
  }

  @StoreScopeExempt(reason = "MANDATORY で呼出元の店舗境界とトランザクションに参加する")
  @Transactional(propagation = Propagation.MANDATORY)
  public String ensureMemberRequestCustomer(MemberRequestCustomerInput input) {
    Optional<CustomerMemberLink> established =
        customerMemberLinkRepository.findByStoreIdAndMemberIdAndStatus(
            storeContext.getStoreId(), input.memberId(), LinkStatus.ACTIVE);
    if (established.isPresent()) {
      return customerReferenceResolver.resolveForWrite(established.get().getCustomerId());
    }
    // 店舗が知るのは申請時の名乗りだけで、プラットフォームのプロフィールは参照しない。
    Customer customer =
        customerRepository.save(Customer.builder().name(input.declaredName()).build());
    try {
      customerMemberLinkRepository.saveAndFlush(
          CustomerMemberLink.builder()
              .customerId(customer.getId())
              .memberId(input.memberId())
              .memberCode(input.memberCode())
              .reason(LinkReason.MEMBER_REQUEST)
              .linkedBy(input.actorId())
              .linkedAt(OffsetDateTime.now())
              .build());
    } catch (DataIntegrityViolationException ex) {
      // flush で敗者の全更新を巻き戻し、呼出元が新しいトランザクションで再試行する。
      if (IntegrityViolations.violates(ex, DbConstraint.UQ_T_CUSTOMER_MEMBER_LINKS_ACTIVE_MEMBER)) {
        throw new MemberCustomerConflictException();
      }
      throw ex;
    }
    return customer.getId();
  }

  @StoreScopeExempt(reason = "MANDATORY で呼出元の店舗境界とトランザクションに参加する")
  @Transactional(propagation = Propagation.MANDATORY)
  public String createCustomer(NewCustomerInput input) {
    return customerRepository
        .save(Customer.builder().name(input.name()).phoneNumber(input.phoneNumber()).build())
        .getId();
  }
}
