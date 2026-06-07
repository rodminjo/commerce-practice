package com.rodminjo.commerce.order.adapter.out.persistence.mybatis;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class OrderRow {

    private String id;
    private String status;
    private long totalAmountMinor;
    private String currency;
    private List<OrderItemRow> items = new ArrayList<>();

}
