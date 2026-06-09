package com.rodminjo.commerce.order.adapter.in.web;

import com.rodminjo.commerce.order.application.port.in.GetOrderUseCase.OrderView;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrderWebMapper {

  PlaceOrderCommand toCommand(PlaceOrderRequest request);

  OrderDetailResponse toResponse(OrderView view);
}
