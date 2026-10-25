package com.kizuna.customer.application;

import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkReason;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.storescope.StoreScopeExempt;
import java.time.OffsetDateTime;
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

  @StoreScopeExempt(reason = "MANDATORY で呼出元のトランザクションに参加し、店舗境界は呼出元が渡す storeId が引く")
  @Transactional(propagation = Propagation.MANDATORY)
  public String ensureMemberRequestCustomer(Long storeId, MemberRequestCustomerInput input) {
    Optional<CustomerMemberLink> established =
        customerMemberLinkRepository.findByStoreIdAndMemberIdAndStatus(
            storeId, input.memberId(), LinkStatus.ACTIVE);
    if (established.isPresent()) {
      return customerReferenceResolver.resolveForWrite(storeId, established.get().getCustomerId());
    }
    // 店舗が知るのは申請時の名乗りだけで、グループ共通プロフィールは参照しない。
    Customer newCustomer = Customer.builder().name(input.declaredName()).build();
    newCustomer.setStoreId(storeId);
    Customer customer = customerRepository.save(newCustomer);
    CustomerMemberLink link =
        CustomerMemberLink.builder()
            .customerId(customer.getId())
            .memberId(input.memberId())
            .memberCode(input.memberCode())
            .reason(LinkReason.MEMBER_REQUEST)
            .linkedBy(input.actorId())
            .linkedAt(OffsetDateTime.now())
            .build();
    link.setStoreId(storeId);
    try {
      customerMemberLinkRepository.saveAndFlush(link);
    } catch (DataIntegrityViolationException ex) {
      // flush で敗者の全更新を巻き戻し、呼出元が新しいトランザクションで再試行する。
      if (IntegrityViolations.violates(ex, DbConstraint.UQ_T_CUSTOMER_MEMBER_LINKS_ACTIVE_MEMBER)) {
        throw new MemberCustomerConflictException();
      }
      throw ex;
    }
    return customer.getId();
  }

  @StoreScopeExempt(reason = "MANDATORY で呼出元のトランザクションに参加し、店舗境界は呼出元が渡す storeId が引く")
  @Transactional(propagation = Propagation.MANDATORY)
  public String createCustomer(Long storeId, NewCustomerInput input) {
    Customer customer = Customer.builder().name(input.name()).build();
    customer.setStoreId(storeId);
    return customerRepository.save(customer).getId();
  }
}
