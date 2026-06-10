package com.rodminjo.commerce.inventory.adapter.in.web;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.inventory.application.port.out.InventoryStockPort;
import com.rodminjo.commerce.inventory.application.port.out.InventoryStockPort.InventorySnapshot;
import com.rodminjo.commerce.inventory.domain.InventoryErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** E2E 수동 검증용 조회 엔드포인트. PK 단건 조회는 단순 읽기이므로 MyBatis 대신 JPA(stock port) 경유. */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/inventory")
public class InventoryQueryController {

  private final InventoryStockPort stockPort;

  @GetMapping("/{productId}")
  public ResponseEntity<InventoryResponse> get(@PathVariable String productId) {
    InventorySnapshot snapshot =
        stockPort
            .find(productId)
            .orElseThrow(() -> new DomainException(InventoryErrorCode.PRODUCT_NOT_FOUND));
    return ResponseEntity.ok(
        new InventoryResponse(
            snapshot.productId(), snapshot.stock(), snapshot.reserved(), snapshot.availableQty()));
  }

  public record InventoryResponse(String productId, int stock, int reserved, int available) {}
}
