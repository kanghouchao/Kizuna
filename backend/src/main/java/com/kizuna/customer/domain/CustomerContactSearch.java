package com.kizuna.customer.domain;

import com.kizuna.shared.validation.ContactValues;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Locale;
import org.springframework.data.jpa.repository.query.EscapeCharacter;

/** 検索結果と一致理由に同じ種類別の部分一致を適用する。 */
public final class CustomerContactSearch {
  private CustomerContactSearch() {}

  public static Predicate matches(
      Root<CustomerContact> contact, CriteriaBuilder cb, String search) {
    String term = search.strip();
    int at = term.lastIndexOf('@');
    String email =
        at < 0 ? term : term.substring(0, at + 1) + term.substring(at + 1).toLowerCase(Locale.ROOT);
    Predicate emailMatch = matchesType(contact, cb, ContactType.EMAIL, email);
    if (at < 0) {
      var escape = EscapeCharacter.DEFAULT;
      // 登録時に小文字化される最後の @ より後だけを比較し、引用されたローカル部の @ は区切りにしない。
      var domain =
          cb.function(
              "regexp_replace",
              String.class,
              contact.get("value"),
              cb.literal("^.*@"),
              cb.literal(""));
      emailMatch =
          cb.or(
              emailMatch,
              cb.and(
                  cb.equal(contact.get("type"), ContactType.EMAIL),
                  cb.like(
                      domain,
                      "%" + escape.escape(term.toLowerCase(Locale.ROOT)) + "%",
                      escape.getEscapeCharacter())));
    }
    return cb.or(
        matchesType(contact, cb, ContactType.PHONE, term),
        matchesType(contact, cb, ContactType.PHONE, ContactValues.search(term)),
        emailMatch,
        matchesType(contact, cb, ContactType.LINE, term));
  }

  private static Predicate matchesType(
      Root<CustomerContact> contact, CriteriaBuilder cb, ContactType type, String term) {
    var escape = EscapeCharacter.DEFAULT;
    return cb.and(
        cb.equal(contact.get("type"), type),
        cb.like(
            contact.get("value"), "%" + escape.escape(term) + "%", escape.getEscapeCharacter()));
  }
}
