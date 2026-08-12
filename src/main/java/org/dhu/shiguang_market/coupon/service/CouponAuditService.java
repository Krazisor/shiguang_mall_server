package org.dhu.shiguang_market.coupon.service;

import java.util.Map;
import org.dhu.shiguang_market.common.model.MarketEnums.OperatorType;
import org.dhu.shiguang_market.common.util.RequestContext;
import org.dhu.shiguang_market.coupon.mapper.CouponOperationLogMapper;
import org.dhu.shiguang_market.coupon.model.CouponModels.OperationLog;
import org.springframework.stereotype.Service;

@Service
public class CouponAuditService {
    private final CouponOperationLogMapper logs;

    public CouponAuditService(CouponOperationLogMapper logs) {
        this.logs = logs;
    }

    public void log(String resourceType, long resourceId, String operationType,
                    OperatorType operatorType, Long operatorId, Long shopId,
                    String fromStatus, String toStatus, Map<String, Object> changes, String reason) {
        OperationLog log = new OperationLog();
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setOperationType(operationType);
        log.setOperatorType(operatorType);
        log.setOperatorId(operatorId);
        log.setShopId(shopId);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setChangeSummaryJson(changes == null || changes.isEmpty() ? null : Map.copyOf(changes));
        log.setReason(reason);
        log.setRequestId(RequestContext.requestId());
        logs.insert(log);
    }
}
