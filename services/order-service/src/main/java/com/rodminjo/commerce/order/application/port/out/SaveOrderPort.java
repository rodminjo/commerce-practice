package com.rodminjo.commerce.order.application.port.out;

import com.rodminjo.commerce.order.domain.model.Order;

public interface SaveOrderPort {

    Order save(Order order);
}
