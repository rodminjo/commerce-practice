package com.rodminjo.commerce.order.adapter.out.persistence.mybatis;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class OrderItemRow {

    private String productId;
    private int quantity;
    private long unitPriceMinor;

}
