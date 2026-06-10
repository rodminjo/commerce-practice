package com.rodminjo.commerce.inventory.adapter.out.persistence.jpa.entity;

import com.rodminjo.commerce.common.infra.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
@Entity
@Table(name = "inventory")
public class InventoryJpaEntity extends BaseEntity {

  @Id
  @Column(length = 64)
  private String productId;

  @Column(nullable = false)
  private int stock;

  @Column(nullable = false)
  private int reserved;
}
