package com.rodminjo.commerce.order.adapter.out.persistence.jpa.entity;

import com.rodminjo.commerce.common.infra.persistence.BaseEntity;
import com.rodminjo.commerce.order.domain.model.Order;
import com.rodminjo.commerce.order.domain.model.OrderLineItem;
import com.rodminjo.commerce.order.domain.model.OrderStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * JPA aggregate-root entity for the order header.
 *
 * <p>{@code created_at} is a <b>business</b> field the domain owns ({@code Order.place()} time):
 * declared here, set in {@link #fromDomain(Order)}, restored in {@link #toDomain(List)}. The
 * separate {@code audit_*} columns (creation/modification metadata) are inherited from
 * {@link BaseEntity} and filled automatically — they never collide with this field.
 *
 * <p>Line items are NOT mapped as a JPA association — there is no {@code @OneToMany}/
 * {@code @JoinColumn}. The adapter persists/loads {@link OrderItemJpaEntity} separately via its
 * own repository and recombines them in {@link #toDomain(List)}, so the aggregate boundary lives
 * in the adapter, not in JPA cascade mappings.
 */
@NoArgsConstructor
@Getter
@Entity
@Table(name = "orders")
public class OrderJpaEntity extends BaseEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false)
    private long totalAmountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;


    public static OrderJpaEntity fromDomain(Order order) {
        OrderJpaEntity entity = new OrderJpaEntity();
        entity.id = order.getId();
        entity.customerId = order.getCustomerId();
        entity.status = order.getStatus();
        entity.totalAmountMinor = order.getTotalAmountMinor();
        entity.currency = order.getCurrency();
        entity.createdAt = order.getCreatedAt();
        return entity;
    }

    public Order toDomain(List<OrderItemJpaEntity> items) {
        List<OrderLineItem> domainItems = items.stream()
                .map(OrderItemJpaEntity::toDomain)
                .toList();
        return Order.reconstitute(id, customerId, status, domainItems, totalAmountMinor, currency, createdAt);
    }

}
