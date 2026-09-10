package com.kizuna.cast.domain;

import com.kizuna.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "t_casts")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cast extends BaseEntity {
  @Column(name = "platform_user_id", nullable = false, unique = true, updatable = false)
  private Long platformUserId;

  @Column(name = "real_name")
  private String realName;

  @Column(name = "birth_date")
  private LocalDate birthDate;
}
