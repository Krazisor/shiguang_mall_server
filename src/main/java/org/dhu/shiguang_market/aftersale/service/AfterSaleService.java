package org.dhu.shiguang_market.aftersale.service;

import static org.dhu.shiguang_market.common.util.Formatters.id;
import static org.dhu.shiguang_market.common.util.Formatters.money;
import static org.dhu.shiguang_market.common.util.Formatters.time;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.dhu.shiguang_market.common.api.CommonViews.ShopSummary;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleDetailView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleEligibilityView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleItemSnapshot;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleOrderSnapshot;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleReviewView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleSummaryView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.CreateAfterSaleRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.ReturnShipmentRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.ReturnShipmentView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.UpdateReturnShipmentRequest;
import org.dhu.shiguang_market.aftersale.mapper.AfterSaleRequestMapper;
import org.dhu.shiguang_market.aftersale.mapper.AfterSaleAppealMapper;
import org.dhu.shiguang_market.aftersale.model.AfterSaleAppeal;
import org.dhu.shiguang_market.aftersale.model.AfterSaleRequest;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleType;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.RefundStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.service.IdempotencyService;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.common.util.ContentSafety;
import org.dhu.shiguang_market.common.util.Formatters;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.mapper.OrderItemMapper;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.dhu.shiguang_market.order.model.OrderItem;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.dhu.shiguang_market.shop.model.Shop;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * 买家端售后 Service。
 * <p>
 * 负责售后资格查询、创建申请、列表/详情、撤销以及退货物流提交/更正。
 * 所有写操作在用例边界使用 {@link Transactional}，并发关键路径使用数据库行锁(FOR UPDATE)保护。
 * 创建、提交物流等写操作为幂等接口，通过 {@link IdempotencyService} 包裹。
 * <p>
 * 售后状态机：PENDING → REJECTED/CANCELLED/WAITING_RETURN/REFUNDING → COMPLETED
 * <p>
 * 售后额度占用口径（与 {@link ShopAfterSaleService} 的批准口径共同构成双重校验）：
 * <ul>
 *   <li>PENDING 申请：按申请数量/金额占用</li>
 *   <li>WAITING_RETURN、REFUNDING 申请：按批准数量/金额占用</li>
 *   <li>REJECTED、CANCELLED：不占用</li>
 *   <li>COMPLETED：已计入订单明细累计退款（refundedQuantity/refundedAmount）</li>
 * </ul>
 */
@Service
public class AfterSaleService {
    private static final Set<String> REASON_CODES = Set.of(
            "NOT_WANTED", "WRONG_ITEM", "DAMAGED", "QUALITY_PROBLEM", "MISSING_PARTS", "OTHER");
    private static final String MONEY_PATTERN = "^(0|[1-9][0-9]{0,15})\\.[0-9]{2}$";
    private final AfterSaleRequestMapper afterSaleMapper;
    private final AfterSaleAppealMapper appealMapper;
    private final OrderItemMapper itemMapper;
    private final OrderInfoMapper orderMapper;
    private final ShopMapper shopMapper;
    private final CurrentUserService currentUser;
    private final IdempotencyService idempotency;
    private final NumberGenerator numbers;
    private final ContentSafety contentSafety;

    @Autowired
    public AfterSaleService(AfterSaleRequestMapper afterSaleMapper, AfterSaleAppealMapper appealMapper,
                            OrderItemMapper itemMapper,
                            OrderInfoMapper orderMapper, ShopMapper shopMapper, CurrentUserService currentUser,
                            IdempotencyService idempotency, NumberGenerator numbers,
                            ContentSafety contentSafety) {
        this.afterSaleMapper = afterSaleMapper;
        this.appealMapper = appealMapper;
        this.itemMapper = itemMapper;
        this.orderMapper = orderMapper;
        this.shopMapper = shopMapper;
        this.currentUser = currentUser;
        this.idempotency = idempotency;
        this.numbers = numbers;
        this.contentSafety = contentSafety;
    }

    /** Backward-compatible constructor used by focused unit tests and legacy adapters. */
    public AfterSaleService(AfterSaleRequestMapper afterSaleMapper, OrderItemMapper itemMapper,
                            OrderInfoMapper orderMapper, ShopMapper shopMapper, CurrentUserService currentUser,
                            IdempotencyService idempotency, NumberGenerator numbers,
                            ContentSafety contentSafety) {
        this(afterSaleMapper, null, itemMapper, orderMapper, shopMapper, currentUser,
                idempotency, numbers, contentSafety);
    }

    // ══════════════════════════════════════════════════════════════
    // 资格查询
    // ══════════════════════════════════════════════════════════════

    /**
     * 查询订单明细的售后可申请额度。
     * <p>
     * 根据订单明细的购买数量/金额、已退数量/金额、当前活跃售后占用数量/金额，
     * 计算本次最大可申请数量和金额，并根据子订单状态返回支持的售后类型。
     * <p>
     * 占用口径：PENDING 按申请量占用，WAITING_RETURN/REFUNDING 按批准量占用；
     * REJECTED/CANCELLED 不占用；COMPLETED 已计入累计退款。
     *
     * @param orderId     子订单 ID
     * @param orderItemId 订单明细 ID
     * @return 售后资格视图，包含最大可申请数量/金额、支持类型、是否可申请
     */
    public AfterSaleEligibilityView eligibility(long orderId, long orderItemId) {
        return eligibility(orderId, orderItemId, currentUser.id(), null);
    }

    /**
     * 商家审核视图使用申请人的身份重新计算额度，并排除当前申请本身。
     */
    AfterSaleEligibilityView eligibilityAtReview(AfterSaleRequest current) {
        return eligibility(current.getOrderId(), current.getOrderItemId(), current.getUserId(), current.getId());
    }

    private AfterSaleEligibilityView eligibility(long orderId, long orderItemId,
                                                   long ownerId, Long excludedAfterSaleId) {
        // 先校验订单属于目标买家，再读取明细，避免通过连续 ID 探测他人订单。
        OrderInfo order = order(orderId, ownerId);
        OrderItem item = orderItem(orderId, orderItemId);

        // 查询该订单明细下所有活跃售后（PENDING/WAITING_RETURN/REFUNDING），用于计算占用量
        LambdaQueryWrapper<AfterSaleRequest> activeQuery = new LambdaQueryWrapper<AfterSaleRequest>()
                .eq(AfterSaleRequest::getOrderItemId, orderItemId)
                .in(AfterSaleRequest::getStatus, List.of(
                        AfterSaleStatus.PENDING, AfterSaleStatus.WAITING_RETURN, AfterSaleStatus.REFUNDING));
        if (excludedAfterSaleId != null) {
            activeQuery.ne(AfterSaleRequest::getId, excludedAfterSaleId);
        }
        List<AfterSaleRequest> activeRequests = afterSaleMapper.selectList(activeQuery);

        // 已退数量/金额（来自 COMPLETED 售后累加到订单明细上的值）
        int refundedQuantity = item.getRefundedQuantity() == null ? 0 : item.getRefundedQuantity();
        BigDecimal refundedAmount = item.getRefundedAmount() == null ? BigDecimal.ZERO : item.getRefundedAmount();

        // 计算活跃售后占用量：PENDING 按申请量，WAITING_RETURN/REFUNDING 按批准量
        int occupiedQuantity = 0;
        BigDecimal occupiedAmount = BigDecimal.ZERO;
        for (AfterSaleRequest r : activeRequests) {
            if (r.getStatus() == AfterSaleStatus.PENDING) {
                occupiedQuantity += r.getQuantity();
                occupiedAmount = occupiedAmount.add(r.getRequestedAmount());
            } else {
                occupiedQuantity += r.getApprovedQuantity() == null ? 0 : r.getApprovedQuantity();
                occupiedAmount = occupiedAmount.add(r.getApprovedAmount() == null ? BigDecimal.ZERO : r.getApprovedAmount());
            }
        }

        // 最大可申请 = 购买量 - 已退量 - 占用量
        int maxQty = Math.max(0, item.getQuantity() - refundedQuantity - occupiedQuantity);
        BigDecimal maxAmt = item.getPayableAmount().subtract(refundedAmount).subtract(occupiedAmount)
                .max(BigDecimal.ZERO);

        // 根据子订单状态决定支持的售后类型
        // PENDING_SHIPMENT：未发货，仅支持仅退款
        // PENDING_RECEIPT/COMPLETED：已发货，支持仅退款和退货退款
        // PENDING_PAYMENT/CANCELLED：不支持任何售后
        OffsetDateTime eligibleUntil = eligibleUntil(order);
        boolean expired = order.getOrderStatus() == OrderStatus.COMPLETED
                && (order.getCompletedAt() == null
                || !LocalDateTime.now().isBefore(order.getCompletedAt().plusDays(7)));
        List<AfterSaleType> supportedTypes = expired ? List.of() : supportedTypes(order.getOrderStatus());

        boolean eligible = maxQty > 0 && maxAmt.compareTo(BigDecimal.ZERO) > 0 && !supportedTypes.isEmpty();
        String ineligibleReason = null;
        if (!eligible) {
            if (maxQty <= 0) ineligibleReason = "已无可申请数量";
            else if (maxAmt.compareTo(BigDecimal.ZERO) <= 0) ineligibleReason = "已无可申请金额";
            else if (expired) ineligibleReason = "已超过售后申请期限";
            else ineligibleReason = "当前订单状态不支持售后";
        }

        return new AfterSaleEligibilityView(id(orderId), id(orderItemId), order.getOrderStatus(),
                item.getQuantity(), refundedQuantity, occupiedQuantity,
                maxQty, money(item.getPayableAmount()), money(refundedAmount),
                money(occupiedAmount), money(maxAmt),
                supportedTypes, eligibleUntil, eligible, ineligibleReason);
    }

    // ══════════════════════════════════════════════════════════════
    // 创建售后申请
    // ══════════════════════════════════════════════════════════════

    /**
     * 创建售后申请（幂等）。
     * <p>
     * 使用 {@link IdempotencyService} 包裹，重复请求返回已存在的结果。
     * 创建时对订单明细加 FOR UPDATE 行锁，防止并发超额申请。
     * afterSaleNo 通过 {@code IdempotencyService.businessNo("AS", ...)} 生成，保证幂等键相同的请求获取同一编号。
     * <p>
     * 校验流程：
     * 1. 锁定订单明细(FOR UPDATE)，重算额度
     * 2. 校验申请数量/金额不超过上限
     * 3. 校验售后类型在当前订单状态下合法
     * 4. 写入 after_sale_request 表，初始状态 PENDING/NOT_STARTED
     *
     * @param request 创建申请请求
     * @param key     Idempotency-Key 请求头值
     * @return 创建后的售后详情
     */
    @Transactional
    public AfterSaleDetailView create(CreateAfterSaleRequest request, String key) {
        // 创建售后是买家侧受控能力，登录校验和权限校验统一在 Service 边界完成。
        currentUser.requirePermission("after-sale:create");
        long userId = currentUser.id();
        String path = "/api/after-sales";
        return idempotency.execute(userId, "POST", path, key, request,
                AfterSaleDetailView.class, () -> createRequest(request, key, userId));
    }

    /**
     * 创建售后的核心业务逻辑（幂等 action 回调）。
     */
    private AfterSaleDetailView createRequest(CreateAfterSaleRequest request, String key, long userId) {
        long orderId = parseId(request.orderId());
        long orderItemId = parseId(request.orderItemId());

        // 服务层必须重复执行关键校验，避免绕过 Controller 直接调用时产生脏数据。
        String reasonCode = requireReasonCode(request.reasonCode());
        String reasonDescription = Formatters.trimToNull(request.reasonDescription());
        if ("OTHER".equals(reasonCode) && reasonDescription == null) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "选择其他原因时必须填写说明");
        }
        List<String> evidenceUrls = contentSafety.imageUrls(
                "evidenceUrls", request.evidenceUrls() == null ? List.of() : request.evidenceUrls(), 9);
        BigDecimal requestAmt = parsePositiveMoney(request.requestedAmount(), "申请退款金额");

        // 幂等：相同幂等键返回同一售后编号，查重后直接返回已有记录
        String afterSaleNo = idempotency.businessNo("AS", userId, key);
        AfterSaleRequest existing = afterSaleMapper.selectOne(new LambdaQueryWrapper<AfterSaleRequest>()
                .eq(AfterSaleRequest::getAfterSaleNo, afterSaleNo));
        if (existing != null) return detail(existing);

        // 先校验订单归属，再锁订单明细并重算额度，防止越权和并发超额。
        order(orderId, userId);
        OrderItem item = orderItem(orderId, orderItemId, true);
        AfterSaleEligibilityView eligibility = eligibility(orderId, orderItemId, userId, null);
        if (!eligibility.eligible()) {
            throw BusinessException.unprocessable("AFTER_SALE_NOT_ELIGIBLE", eligibility.ineligibleReason());
        }
        if (request.quantity() > eligibility.maximumRequestQuantity()) {
            throw BusinessException.unprocessable("AFTER_SALE_QUANTITY_EXCEEDED", "申请数量超过可申请上限");
        }
        if (requestAmt.compareTo(new BigDecimal(eligibility.maximumRequestAmount())) > 0) {
            throw BusinessException.unprocessable("AFTER_SALE_AMOUNT_EXCEEDED", "申请金额超过可申请上限");
        }
        BigDecimal quantityLimit = amountLimitForQuantity(
                item, request.quantity(), eligibility.maximumRequestQuantity(),
                new BigDecimal(eligibility.maximumRequestAmount()));
        if (requestAmt.compareTo(quantityLimit) > 0) {
            throw BusinessException.unprocessable(
                    "AFTER_SALE_AMOUNT_EXCEEDED", "申请金额超过本次申请数量对应的退款上限");
        }
        if (!eligibility.supportedTypes().contains(request.requestType())) {
            throw BusinessException.unprocessable("AFTER_SALE_NOT_ELIGIBLE", "当前订单状态不支持该售后类型");
        }

        // 构建售后实体，初始状态 PENDING
        AfterSaleRequest ar = new AfterSaleRequest();
        ar.setAfterSaleNo(afterSaleNo);
        ar.setOrderId(orderId);
        ar.setOrderItemId(orderItemId);
        ar.setUserId(userId);
        ar.setRequestType(request.requestType());
        ar.setQuantity(request.quantity());
        ar.setReasonCode(reasonCode);
        ar.setReasonDescription(reasonDescription);
        ar.setEvidenceJson(evidenceUrls);
        ar.setRequestedAmount(requestAmt);
        ar.setStatus(AfterSaleStatus.PENDING);
        ar.setRefundStatus(RefundStatus.NOT_STARTED);
        afterSaleMapper.insert(ar);
        return detail(afterSaleMapper.selectById(ar.getId()));
    }

    // ══════════════════════════════════════════════════════════════
    // 列表 / 详情
    // ══════════════════════════════════════════════════════════════

    /**
     * 查询当前用户的售后列表（分页）。
     * <p>
     * 支持按状态、售后类型、订单号（需要联表查 order_info）、时间范围筛选。
     * 默认按创建时间倒序排列。
     *
     * @param status      售后状态筛选（可选）
     * @param requestType 售后类型筛选（可选）
     * @param orderNo     订单号筛选（可选，需联表查 order_info 获取 orderId）
     * @param createdFrom 创建时间起始（可选，含）
     * @param createdTo   创建时间截止（可选，不含）
     * @param page        页码（从 1 开始）
     * @param pageSize    每页条数（1-100）
     * @return 分页售后摘要列表
     */
    public PageView<AfterSaleSummaryView> list(AfterSaleStatus status, AfterSaleType requestType,
                                                String orderNo, LocalDateTime createdFrom, LocalDateTime createdTo,
                                                long page, long pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw BusinessException.badRequest("BAD_REQUEST", "分页参数超出范围");
        }
        LambdaQueryWrapper<AfterSaleRequest> query = new LambdaQueryWrapper<AfterSaleRequest>()
                .eq(AfterSaleRequest::getUserId, currentUser.id())
                .eq(status != null, AfterSaleRequest::getStatus, status)
                .eq(requestType != null, AfterSaleRequest::getRequestType, requestType)
                .ge(createdFrom != null, AfterSaleRequest::getCreatedAt, createdFrom)
                .lt(createdTo != null, AfterSaleRequest::getCreatedAt, createdTo)
                .orderByDesc(AfterSaleRequest::getCreatedAt).orderByDesc(AfterSaleRequest::getId);

        // 按订单号筛选：先通过 order_info 表查出对应的订单 ID，再限定售后查询范围
        if (orderNo != null && !orderNo.isBlank()) {
            List<OrderInfo> orders = orderMapper.selectList(new LambdaQueryWrapper<OrderInfo>()
                    .eq(OrderInfo::getOrderNo, orderNo.trim()));
            if (orders.isEmpty()) return PageView.of(new Page<>(page, pageSize), List.of());
            query.in(AfterSaleRequest::getOrderId, orders.stream().map(OrderInfo::getId).toList());
        }

        Page<AfterSaleRequest> result = afterSaleMapper.selectPage(Page.of(page, pageSize), query);
        return PageView.of(result, result.getRecords().stream().map(this::summary).toList());
    }

    /**
     * 查询售后详情。
     * <p>
     * 仅返回当前用户本人的售后申请，其他人不可见（按 RESOURCE_NOT_FOUND 处理）。
     *
     * @param afterSaleId 售后申请 ID
     * @return 完整售后详情，含订单/店铺/商品快照、审核/物流/退款进度、可用操作列表
     */
    public AfterSaleDetailView detail(long afterSaleId) {
        return detail(owned(afterSaleId));
    }

    // ══════════════════════════════════════════════════════════════
    // 撤销申请
    // ══════════════════════════════════════════════════════════════

    /**
     * 撤销售后申请。
     * <p>
     * 仅允许撤销状态为 PENDING 的申请。撤销后不可恢复。
     * 使用 FOR UPDATE 锁售后行，确保并发撤销不冲突。
     *
     * @param afterSaleId 售后申请 ID
     * @return 更新后的售后详情
     */
    @Transactional
    public AfterSaleDetailView cancel(long afterSaleId) {
        AfterSaleRequest ar = owned(afterSaleId, true);
        if (ar.getStatus() != AfterSaleStatus.PENDING) {
            throw BusinessException.conflict("AFTER_SALE_NOT_CANCELLABLE", "只有待处理的申请可以撤销");
        }
        ar.setStatus(AfterSaleStatus.CANCELLED);
        ar.setCancelledAt(LocalDateTime.now());
        afterSaleMapper.updateById(ar);
        return detail(ar);
    }

    // ══════════════════════════════════════════════════════════════
    // 退货物流
    // ══════════════════════════════════════════════════════════════

    /**
     * 提交退货物流信息（幂等）。
     * <p>
     * 仅允许对状态为 WAITING_RETURN 且尚未提交物流的退货退款申请提交。
     * 首次提交后写入 returnedAt，商家确认收货前可更正（见 {@link #updateReturnShipment}）。
     * 幂等包裹保证重复提交返回相同结果。
     *
     * @param afterSaleId 售后申请 ID
     * @param request     物流信息（承运商代码、名称、运单号）
     * @param key         Idempotency-Key 请求头值
     * @return 更新后的售后详情
     */
    @Transactional
    public AfterSaleDetailView submitReturnShipment(long afterSaleId, ReturnShipmentRequest request, String key) {
        long userId = currentUser.id();
        String path = "/api/after-sales/" + afterSaleId + "/return-shipment";
        return idempotency.execute(userId, "POST", path, key, request,
                AfterSaleDetailView.class, () -> submitShipment(afterSaleId, request));
    }

    /** 提交物流的核心业务逻辑（幂等 action 回调）。 */
    private AfterSaleDetailView submitShipment(long afterSaleId, ReturnShipmentRequest request) {
        AfterSaleRequest ar = owned(afterSaleId, true);
        // 校验：仅退货退款类型可提交物流
        if (ar.getRequestType() != AfterSaleType.RETURN_REFUND) {
            throw BusinessException.conflict("RETURN_SHIPMENT_NOT_ALLOWED", "仅退货退款申请可提交物流");
        }
        // 校验：仅 WAITING_RETURN 状态可提交
        if (ar.getStatus() != AfterSaleStatus.WAITING_RETURN) {
            throw BusinessException.conflict("RETURN_SHIPMENT_NOT_ALLOWED", "当前状态不允许提交物流");
        }
        // 校验：物流信息不可重复提交
        if (ar.getReturnTrackingNo() != null) {
            throw BusinessException.conflict("RETURN_SHIPMENT_ALREADY_SUBMITTED", "物流信息已提交");
        }
        ar.setReturnCarrierCode(requireText(request.carrierCode(), 64, "carrierCode"));
        ar.setReturnCarrierName(requireText(request.carrierName(), 128, "carrierName"));
        ar.setReturnTrackingNo(requireText(request.trackingNo(), 128, "trackingNo"));
        ar.setReturnedAt(LocalDateTime.now());
        afterSaleMapper.updateById(ar);
        return detail(ar);
    }

    /**
     * 更正退货物流信息（PATCH 三态语义）。
     * <p>
     * 仅允许在 WAITING_RETURN 状态、已提交物流、商家尚未确认收货时更正。
     * 使用 version 乐观锁防止并发冲突。
     * 三态语义：字段缺失不修改，字段有值更新，字段为 null 不处理（物流字段不可清空）。
     *
     * @param afterSaleId 售后申请 ID
     * @param request     要更新的字段（含 version）
     * @return 更新后的售后详情
     */
    @Transactional
    public AfterSaleDetailView updateReturnShipment(long afterSaleId, UpdateReturnShipmentRequest request) {
        AfterSaleRequest ar = owned(afterSaleId, true);
        if (ar.getStatus() != AfterSaleStatus.WAITING_RETURN) {
            throw BusinessException.conflict("RETURN_SHIPMENT_NOT_ALLOWED", "当前状态不允许修改物流");
        }
        if (ar.getReturnReceivedAt() != null) {
            throw BusinessException.conflict("RETURN_SHIPMENT_NOT_ALLOWED", "商家已确认收货，不可修改物流");
        }
        if (ar.getReturnTrackingNo() == null) {
            throw BusinessException.conflict("RETURN_SHIPMENT_NOT_ALLOWED", "请先提交物流信息");
        }
        if (request.version() == null) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "version 不能为空");
        }
        // version 乐观锁
        if (!request.version().equals(ar.getVersion())) {
            throw BusinessException.conflict("VERSION_CONFLICT", "售后版本已变化");
        }
        // API 契约是 PUT：三个物流字段与 version 都必须完整提交。
        ar.setReturnCarrierCode(requireText(request.carrierCode(), 64, "carrierCode"));
        ar.setReturnCarrierName(requireText(request.carrierName(), 128, "carrierName"));
        ar.setReturnTrackingNo(requireText(request.trackingNo(), 128, "trackingNo"));
        afterSaleMapper.updateById(ar);
        return detail(ar);
    }

    // ══════════════════════════════════════════════════════════════
    // 视图映射（包级别可见，供商家端 ShopAfterSaleService 复用）
    // ══════════════════════════════════════════════════════════════

    /**
     * 将售后实体映射为列表摘要视图。
     */
    AfterSaleSummaryView summary(AfterSaleRequest ar) {
        OrderInfo order = orderMapper.selectById(ar.getOrderId());
        Shop shop = shopMapper.selectById(order.getShopId());
        OrderItem item = itemMapper.selectById(ar.getOrderItemId());
        return new AfterSaleSummaryView(id(ar.getId()), ar.getAfterSaleNo(), ar.getRequestType(), ar.getStatus(),
                ar.getRefundStatus(), new AfterSaleOrderSnapshot(id(order.getId()), order.getOrderNo(), order.getOrderStatus()),
                shopSummary(shop), itemSnapshot(item), ar.getQuantity(), money(ar.getRequestedAmount()),
                ar.getApprovedAmount() == null ? null : money(ar.getApprovedAmount()),
                time(ar.getCreatedAt()), time(ar.getUpdatedAt()));
    }

    /**
     * 为平台运营只读查询复用售后摘要映射。
     * 该方法只组装展示数据，不执行买家身份校验，也不修改售后状态。
     */
    public AfterSaleSummaryView summaryForOperation(AfterSaleRequest afterSale) {
        return summary(afterSale);
    }

    /**
     * 将售后实体映射为完整详情视图。
     * <p>
     * 组装订单快照、店铺快照、商品快照、审核信息、退货物流、退款进度和可用操作列表。
     * 商家端与买家端共享此视图构建逻辑，商家端在此基础上附加买家信息。
     */
    AfterSaleDetailView detail(AfterSaleRequest ar) {
        OrderInfo order = orderMapper.selectById(ar.getOrderId());
        Shop shop = shopMapper.selectById(order.getShopId());
        OrderItem item = itemMapper.selectById(ar.getOrderItemId());
        return new AfterSaleDetailView(
                id(ar.getId()), ar.getAfterSaleNo(), ar.getRequestType(), ar.getStatus(), ar.getRefundStatus(),
                new AfterSaleOrderSnapshot(id(order.getId()), order.getOrderNo(), order.getOrderStatus()),
                shopSummary(shop), itemSnapshot(item),
                ar.getQuantity(), ar.getReasonCode(), ar.getReasonDescription(), ar.getEvidenceJson(),
                money(ar.getRequestedAmount()), ar.getApprovedQuantity(),
                ar.getApprovedAmount() == null ? null : money(ar.getApprovedAmount()),
                review(ar), shipment(ar), appealSummary(ar), ar.getRefundNo(), ar.getRefundFailureReason(),
                time(ar.getRefundedAt()), time(ar.getCompletedAt()), time(ar.getCancelledAt()),
                ar.getVersion(), time(ar.getCreatedAt()), time(ar.getUpdatedAt()),
                availableActions(ar));
    }

    // ══════════════════════════════════════════════════════════════
    // 辅助方法
    // ══════════════════════════════════════════════════════════════

    /**
     * 查询当前用户的售后申请（带所有权校验）。
     * <p>
     * 通过 userId 范围限定保证数据安全。lock=true 时加 FOR UPDATE 行锁。
     *
     * @param afterSaleId 售后申请 ID
     * @param lock        是否加行锁（用于写操作前的并发保护）
     * @return 售后实体，不存在或不属于当前用户时抛 RESOURCE_NOT_FOUND
     */
    AfterSaleRequest owned(long afterSaleId, boolean lock) {
        LambdaQueryWrapper<AfterSaleRequest> query = new LambdaQueryWrapper<AfterSaleRequest>()
                .eq(AfterSaleRequest::getId, afterSaleId)
                .eq(AfterSaleRequest::getUserId, currentUser.id());
        if (lock) query.last("FOR UPDATE");
        AfterSaleRequest ar = afterSaleMapper.selectOne(query);
        if (ar == null) throw BusinessException.notFound("RESOURCE_NOT_FOUND", "售后申请不存在");
        return ar;
    }

    private AfterSaleRequest owned(long afterSaleId) {
        return owned(afterSaleId, false);
    }

    /**
     * 查询订单明细，同时校验 orderId 和 orderItemId 的归属关系。
     */
    private OrderItem orderItem(long orderId, long orderItemId, boolean lock) {
        LambdaQueryWrapper<OrderItem> query = new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getId, orderItemId).eq(OrderItem::getOrderId, orderId);
        if (lock) query.last("FOR UPDATE");
        OrderItem item = itemMapper.selectOne(query);
        if (item == null) throw BusinessException.notFound("RESOURCE_NOT_FOUND", "订单明细不存在");
        return item;
    }

    private OrderItem orderItem(long orderId, long orderItemId) {
        return orderItem(orderId, orderItemId, false);
    }

    private OrderInfo order(long orderId, long userId) {
        OrderInfo order = orderMapper.selectOne(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getId, orderId)
                .eq(OrderInfo::getUserId, userId));
        if (order == null || order.getUserId() == null || order.getUserId() != userId) {
            throw BusinessException.notFound("RESOURCE_NOT_FOUND", "订单不存在");
        }
        return order;
    }

    /**
     * 根据子订单状态返回支持的售后类型。
     * <ul>
     *   <li>PENDING_SHIPMENT（待发货）：仅支持仅退款 REFUND_ONLY</li>
     *   <li>PENDING_RECEIPT（待收货）/ COMPLETED（已完成）：支持仅退款和退货退款</li>
     *   <li>其他状态（PENDING_PAYMENT/CANCELLED）：不支持任何售后</li>
     * </ul>
     */
    private List<AfterSaleType> supportedTypes(OrderStatus status) {
        return switch (status) {
            case PENDING_SHIPMENT -> List.of(AfterSaleType.REFUND_ONLY);
            case PENDING_RECEIPT, COMPLETED -> List.of(AfterSaleType.REFUND_ONLY, AfterSaleType.RETURN_REFUND);
            default -> List.of();
        };
    }

    /**
     * 已完成订单的售后截止时间（确认收货后 7 天内）。
     *
     * @return 截止时间，非已完成订单返回 null
     */
    private OffsetDateTime eligibleUntil(OrderInfo order) {
        if (order.getOrderStatus() == OrderStatus.COMPLETED && order.getCompletedAt() != null) {
            return time(order.getCompletedAt().plusDays(7));
        }
        return null;
    }

    /**
     * 计算当前售后申请的可用操作列表，用于前端控制操作按钮的显隐。
     */
    List<String> availableActions(AfterSaleRequest ar) {
        List<String> actions = new ArrayList<>();
        if (ar.getStatus() == AfterSaleStatus.PENDING) {
            actions.add("CANCEL");
        }
        if (ar.getStatus() == AfterSaleStatus.WAITING_RETURN && ar.getReturnTrackingNo() == null) {
            actions.add("SUBMIT_RETURN_SHIPMENT");
        }
        if (ar.getStatus() == AfterSaleStatus.WAITING_RETURN && ar.getReturnTrackingNo() != null
                && ar.getReturnReceivedAt() == null) {
            actions.add("UPDATE_RETURN_SHIPMENT");
        }
        return actions;
    }

    /** 构建审核信息视图，未审核时返回 null。 */
    private AfterSaleReviewView review(AfterSaleRequest ar) {
        if (ar.getReviewerId() == null) return null;
        return new AfterSaleReviewView(id(ar.getReviewerId()), ar.getReviewComment(), time(ar.getReviewedAt()));
    }

    /** 构建退货物流视图，未提交物流时返回 null。 */
    private ReturnShipmentView shipment(AfterSaleRequest ar) {
        if (ar.getReturnTrackingNo() == null) return null;
        return new ReturnShipmentView(ar.getReturnCarrierCode(), ar.getReturnCarrierName(),
                ar.getReturnTrackingNo(), time(ar.getReturnedAt()), time(ar.getReturnReceivedAt()));
    }

    private org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleAppealSummaryView appealSummary(
            AfterSaleRequest request) {
        if (appealMapper == null) return null;
        AfterSaleAppeal appeal = appealMapper.selectOne(new LambdaQueryWrapper<AfterSaleAppeal>()
                .eq(AfterSaleAppeal::getAfterSaleId, request.getId()));
        if (appeal == null) return null;
        return new org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleAppealSummaryView(
                id(appeal.getId()), appeal.getAppealNo(), id(request.getId()), request.getAfterSaleNo(),
                appeal.getTriggerType(), appeal.getStatus(), time(appeal.getCreatedAt()),
                time(appeal.getDecidedAt()));
    }

    private ShopSummary shopSummary(Shop shop) {
        return new ShopSummary(id(shop.getId()), shop.getShopNo(), shop.getShopName(),
                shop.getLogoUrl(), shop.getStatus());
    }

    private AfterSaleItemSnapshot itemSnapshot(OrderItem item) {
        return new AfterSaleItemSnapshot(id(item.getId()), item.getProductName(), item.getSkuName(),
                item.getSpecJson(), item.getImageUrl(), money(item.getUnitPrice()), item.getQuantity());
    }

    private String requireReasonCode(String raw) {
        String value = Formatters.trimToNull(raw);
        if (value == null || !REASON_CODES.contains(value)) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "售后原因代码无效");
        }
        return value;
    }

    private String requireText(String raw, int maximum, String field) {
        String value = Formatters.trimToNull(raw);
        if (value == null || value.length() > maximum) {
            throw BusinessException.badRequest(
                    "VALIDATION_FAILED", field + " 长度必须为 1.." + maximum);
        }
        return value;
    }

    private BigDecimal parsePositiveMoney(String raw, String field) {
        if (raw == null || !raw.matches(MONEY_PATTERN)) {
            throw BusinessException.badRequest("VALIDATION_FAILED", field + "必须为两位小数字符串");
        }
        BigDecimal value = new BigDecimal(raw);
        if (value.signum() <= 0) {
            throw BusinessException.badRequest("VALIDATION_FAILED", field + "必须大于 0.00");
        }
        return value;
    }

    private BigDecimal amountLimitForQuantity(OrderItem item, int requestedQuantity,
                                               int maximumQuantity, BigDecimal maximumAmount) {
        // 最后一批剩余数量允许吸收分摊舍入差额，其余情况按明细实付金额等比例计算。
        if (requestedQuantity == maximumQuantity) {
            return maximumAmount;
        }
        BigDecimal proportional = item.getPayableAmount()
                .multiply(BigDecimal.valueOf(requestedQuantity))
                .divide(BigDecimal.valueOf(item.getQuantity()), 2, RoundingMode.HALF_UP);
        return proportional.min(maximumAmount);
    }

    /** 解析请求中的 ID 字符串为 long，格式非法时抛 BAD_REQUEST。 */
    private long parseId(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException ex) {
            throw BusinessException.badRequest("BAD_REQUEST", "ID 格式错误");
        }
    }
}
