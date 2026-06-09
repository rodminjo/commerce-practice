package com.rodminjo.commerce.order.adapter.out.persistence.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderQueryMapper {

  OrderRow findOrderById(@Param("id") String id);
}
