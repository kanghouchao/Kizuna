package com.kizuna.customer.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class ContactPreferenceRequest {
  @Size(min = 1, max = 64)
  private String contactId;

  @JsonIgnore private boolean contactIdSpecified;

  public void setContactId(String contactId) {
    this.contactId = contactId;
    contactIdSpecified = true;
  }

  @AssertTrue(message = "contact_id に連絡先 ID または null を指定してください")
  @JsonIgnore
  public boolean isContactIdSpecified() {
    return contactIdSpecified;
  }
}
