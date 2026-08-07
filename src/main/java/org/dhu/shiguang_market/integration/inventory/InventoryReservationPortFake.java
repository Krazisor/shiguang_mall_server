package org.dhu.shiguang_market.integration.inventory;

import java.util.HashMap;
import java.util.Map;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.integration.inventory.InventoryReservationPort.InventoryBalance;

/**
 * 库存端口内存实现，供 B 线独立测试下单、取消、发货和退货流程。
 * 方法使用 synchronized 保证测试中并发调用不会破坏可用量与锁定量之和。
 */
public class InventoryReservationPortFake implements InventoryReservationPort {
    private final Map<Long, MutableBalance> balances = new HashMap<>();
    private final Map<ExecutionKey, Execution> executions = new HashMap<>();

    /** 预设 SKU 的初始库存。 */
    public synchronized void seed(long skuId, int availableQuantity, int lockedQuantity) {
        if (availableQuantity < 0 || lockedQuantity < 0) {
            throw BusinessException.badRequest("INVENTORY_OPERATION_INVALID", "库存数量不能为负数");
        }
        balances.put(skuId, new MutableBalance(availableQuantity, lockedQuantity));
    }

    @Override
    public synchronized InventoryBalance lock(String businessKey, long skuId, int quantity) {
        return execute(Operation.LOCK, businessKey, skuId, quantity);
    }

    @Override
    public synchronized InventoryBalance release(String businessKey, long skuId, int quantity) {
        return execute(Operation.RELEASE, businessKey, skuId, quantity);
    }

    @Override
    public synchronized InventoryBalance deduct(String businessKey, long skuId, int quantity) {
        return execute(Operation.DEDUCT, businessKey, skuId, quantity);
    }

    @Override
    public synchronized InventoryBalance returnStock(String businessKey, long skuId, int quantity) {
        return execute(Operation.RETURN, businessKey, skuId, quantity);
    }

    private InventoryBalance execute(Operation operation, String businessKey, long skuId, int quantity) {
        validate(businessKey, skuId, quantity);
        ExecutionKey key = new ExecutionKey(operation, businessKey.trim());
        Execution previous = executions.get(key);
        if (previous != null) {
            if (previous.skuId() != skuId || previous.quantity() != quantity) {
                throw BusinessException.conflict(
                        "IDEMPOTENCY_KEY_REUSED", "同一库存业务键不能用于不同参数");
            }
            return previous.result();
        }

        MutableBalance balance = balances.get(skuId);
        if (balance == null) {
            throw BusinessException.notFound("INVENTORY_NOT_FOUND", "库存记录不存在");
        }
        apply(operation, balance, quantity);
        InventoryBalance result = new InventoryBalance(skuId, balance.available, balance.locked);
        executions.put(key, new Execution(skuId, quantity, result));
        return result;
    }

    /** 按库存状态机执行一次变更，异常时不会记录幂等结果。 */
    private void apply(Operation operation, MutableBalance balance, int quantity) {
        switch (operation) {
            case LOCK -> {
                if (balance.available < quantity) {
                    throw BusinessException.unprocessable("INVENTORY_NOT_ENOUGH", "可用库存不足");
                }
                balance.available -= quantity;
                balance.locked += quantity;
            }
            case RELEASE -> {
                requireLocked(balance, quantity);
                balance.available += quantity;
                balance.locked -= quantity;
            }
            case DEDUCT -> {
                requireLocked(balance, quantity);
                balance.locked -= quantity;
            }
            case RETURN -> balance.available += quantity;
        }
    }

    private void requireLocked(MutableBalance balance, int quantity) {
        if (balance.locked < quantity) {
            throw BusinessException.conflict("LOCKED_INVENTORY_INCONSISTENT", "锁定库存不足");
        }
    }

    private void validate(String businessKey, long skuId, int quantity) {
        if (businessKey == null || businessKey.isBlank() || skuId <= 0 || quantity <= 0) {
            throw BusinessException.badRequest(
                    "INVENTORY_OPERATION_INVALID", "业务键、SKU 和数量必须有效");
        }
    }

    private enum Operation { LOCK, RELEASE, DEDUCT, RETURN }

    private record ExecutionKey(Operation operation, String businessKey) {
    }

    private record Execution(long skuId, int quantity, InventoryBalance result) {
    }

    private static final class MutableBalance {
        private int available;
        private int locked;

        private MutableBalance(int available, int locked) {
            this.available = available;
            this.locked = locked;
        }
    }
}
