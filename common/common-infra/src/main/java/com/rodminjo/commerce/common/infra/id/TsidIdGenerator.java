package com.rodminjo.commerce.common.infra.id;

import com.rodminjo.commerce.common.id.IdGenerator;
import io.hypersistence.tsid.TSID;
import org.springframework.stereotype.Component;

/** TSID 기반 Long ID 전략. UUID 불필요, 정렬·인덱스 친화적 키가 필요한 엔티티용. 64비트, 단조 증가, 분산 환경 안전. */
@Component
public class TsidIdGenerator implements IdGenerator<Long> {

  @Override
  public Long newId() {
    return TSID.Factory.getTsid().toLong();
  }
}
