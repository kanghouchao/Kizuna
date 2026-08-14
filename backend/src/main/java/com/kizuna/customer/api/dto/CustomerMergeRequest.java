package com.kizuna.customer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 顧客統合の要求。受け取るのは被統合行の ID だけで、存続行はパスが名指す。
 *
 * <p>どのフィールドを残すかは受け取らない。フィールド値の自動合併を持たないのが ADR 0010 の裁定で、存続行へ移したい値は 人が見て転記する。
 */
@Data
public class CustomerMergeRequest {

  @NotBlank(message = "被統合の顧客 ID は必須です")
  @Size(max = 64, message = "被統合の顧客 ID は64文字以内です")
  private String mergedCustomerId;
}
