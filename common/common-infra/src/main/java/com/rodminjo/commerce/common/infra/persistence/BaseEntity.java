package com.rodminjo.commerce.common.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

  @CreatedDate
  @Column(name = "audit_created_at", nullable = false, updatable = false)
  private Instant auditCreatedAt;

  @CreatedBy
  @Column(name = "audit_created_by", nullable = false, updatable = false, length = 64)
  private String auditCreatedBy;

  @LastModifiedDate
  @Column(name = "audit_updated_at", nullable = false)
  private Instant auditUpdatedAt;

  @LastModifiedBy
  @Column(name = "audit_updated_by", nullable = false, length = 64)
  private String auditUpdatedBy;
}
