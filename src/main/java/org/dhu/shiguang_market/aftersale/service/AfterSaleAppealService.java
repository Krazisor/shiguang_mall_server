package org.dhu.shiguang_market.aftersale.service;

import static org.dhu.shiguang_market.common.util.Formatters.id;
import static org.dhu.shiguang_market.common.util.Formatters.money;
import static org.dhu.shiguang_market.common.util.Formatters.time;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleAppealAfterSaleView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleAppealDetailView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleAppealSummaryView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.CreateAfterSaleAppealRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.DecideAfterSaleAppealRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.MerchantNotificationView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.PlatformAfterSaleAppealDetailView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.PlatformAfterSaleAppealSummaryView;
import org.dhu.shiguang_market.aftersale.mapper.AfterSaleAppealMapper;
import org.dhu.shiguang_market.aftersale.mapper.AfterSaleRequestMapper;
import org.dhu.shiguang_market.aftersale.mapper.MerchantNotificationMapper;
import org.dhu.shiguang_market.aftersale.model.AfterSaleAppeal;
import org.dhu.shiguang_market.aftersale.model.AfterSaleRequest;
import org.dhu.shiguang_market.aftersale.model.MerchantNotification;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleAppealDecision;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleAppealStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleAppealTriggerType;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleType;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantNotificationType;
import org.dhu.shiguang_market.common.model.MarketEnums.RefundStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.security.ShopAccessService;
import org.dhu.shiguang_market.common.service.IdempotencyService;
import org.dhu.shiguang_market.common.util.ContentSafety;
import org.dhu.shiguang_market.common.util.Formatters;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.identity.model.SysUser;
import org.dhu.shiguang_market.identity.service.IdentityViewMapper;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.mapper.OrderItemMapper;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.dhu.shiguang_market.order.model.OrderItem;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.OperatorBrief;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.dhu.shiguang_market.shop.mapper.ShopUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AfterSaleAppealService {
    private static final String MONEY_PATTERN = "^(0|[1-9][0-9]{0,15})\\.[0-9]{2}$";
    private final AfterSaleAppealMapper appealMapper;
    private final AfterSaleRequestMapper afterSaleMapper;
    private final MerchantNotificationMapper notificationMapper;
    private final OrderInfoMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final ShopMapper shopMapper;
    private final ShopUserMapper shopUserMapper;
    private final SysUserMapper userMapper;
    private final CurrentUserService currentUser;
    private final ShopAccessService shopAccess;
    private final IdempotencyService idempotency;
    private final NumberGenerator numbers;
    private final ContentSafety contentSafety;
    private final ShopAfterSaleService shopAfterSaleService;

    public AfterSaleAppealService(AfterSaleAppealMapper appealMapper, AfterSaleRequestMapper afterSaleMapper,
                                  MerchantNotificationMapper notificationMapper, OrderInfoMapper orderMapper,
                                  OrderItemMapper itemMapper, ShopMapper shopMapper, ShopUserMapper shopUserMapper,
                                  SysUserMapper userMapper, CurrentUserService currentUser,
                                  ShopAccessService shopAccess, IdempotencyService idempotency,
                                  NumberGenerator numbers, ContentSafety contentSafety,
                                  ShopAfterSaleService shopAfterSaleService) {
        this.appealMapper = appealMapper;
        this.afterSaleMapper = afterSaleMapper;
        this.notificationMapper = notificationMapper;
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.shopMapper = shopMapper;
        this.shopUserMapper = shopUserMapper;
        this.userMapper = userMapper;
        this.currentUser = currentUser;
        this.shopAccess = shopAccess;
        this.idempotency = idempotency;
        this.numbers = numbers;
        this.contentSafety = contentSafety;
        this.shopAfterSaleService = shopAfterSaleService;
    }

    @Transactional
    public AfterSaleAppealDetailView create(long afterSaleId, CreateAfterSaleAppealRequest request, String key) {
        long userId = currentUser.id();
        return idempotency.execute(userId, "POST", "/api/after-sales/" + afterSaleId + "/appeal", key,
                request, AfterSaleAppealDetailView.class,
                () -> createInternal(afterSaleId, request, userId));
    }

    public AfterSaleAppealDetailView buyerDetail(long afterSaleId) {
        AfterSaleAppeal appeal = appealMapper.selectOne(new LambdaQueryWrapper<AfterSaleAppeal>()
                .eq(AfterSaleAppeal::getAfterSaleId, afterSaleId));
        if (appeal == null || appeal.getAppellantUserId() != currentUser.id()) {
            throw BusinessException.notFound("RESOURCE_NOT_FOUND", "申诉不存在");
        }
        return detail(appeal);
    }

    @Transactional(readOnly = true)
    public PageView<PlatformAfterSaleAppealSummaryView> list(AfterSaleAppealStatus status,
                                                              AfterSaleAppealTriggerType triggerType,
                                                              Long shopId, String afterSaleNo,
                                                              LocalDateTime createdFrom, LocalDateTime createdTo,
                                                              long page, long pageSize) {
        currentUser.requirePermission("platform:after-sale:manage");
        validatePage(page, pageSize);
        Page<AfterSaleAppeal> result = appealMapper.selectPlatformPage(Page.of(page, pageSize), status,
                triggerType, shopId, afterSaleNo == null ? null : afterSaleNo.trim(), createdFrom, createdTo);
        return PageView.of(result, result.getRecords().stream().map(this::platformSummary).toList());
    }

    @Transactional(readOnly = true)
    public PlatformAfterSaleAppealDetailView platformDetail(long appealId) {
        currentUser.requirePermission("platform:after-sale:manage");
        AfterSaleAppeal appeal = appealMapper.selectById(appealId);
        if (appeal == null) throw BusinessException.notFound("APPEAL_NOT_FOUND", "申诉不存在");
        return platformDetailView(appeal);
    }

    @Transactional
    public PlatformAfterSaleAppealDetailView decide(long appealId, DecideAfterSaleAppealRequest request, String key) {
        currentUser.requirePermission("platform:after-sale:manage");
        long operatorId = currentUser.id();
        return idempotency.execute(operatorId, "POST", "/api/platform/after-sale-appeals/" + appealId + "/decide",
                key, request, PlatformAfterSaleAppealDetailView.class,
                () -> decideInternal(appealId, request, operatorId));
    }

    @Transactional(readOnly = true)
    public PageView<MerchantNotificationView> notifications(long shopId, boolean unreadOnly,
                                                             MerchantNotificationType type,
                                                             long page, long pageSize) {
        shopAccess.require(shopId, "shop:notification:read");
        validatePage(page, pageSize);
        long userId = currentUser.id();
        Page<MerchantNotification> result = notificationMapper.selectPage(Page.of(page, pageSize),
                new LambdaQueryWrapper<MerchantNotification>()
                        .eq(MerchantNotification::getShopId, shopId)
                        .eq(MerchantNotification::getRecipientUserId, userId)
                        .eq(unreadOnly, MerchantNotification::getReadAt, null)
                        .eq(type != null, MerchantNotification::getNotificationType, type)
                        .orderByDesc(MerchantNotification::getCreatedAt).orderByDesc(MerchantNotification::getId));
        return PageView.of(result, result.getRecords().stream().map(this::notificationView).toList());
    }

    @Transactional
    public MerchantNotificationView markRead(long shopId, long notificationId) {
        shopAccess.require(shopId, "shop:notification:read");
        MerchantNotification notification = notificationMapper.selectOne(new LambdaQueryWrapper<MerchantNotification>()
                .eq(MerchantNotification::getId, notificationId)
                .eq(MerchantNotification::getShopId, shopId)
                .eq(MerchantNotification::getRecipientUserId, currentUser.id()));
        if (notification == null) throw BusinessException.notFound("RESOURCE_NOT_FOUND", "通知不存在");
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notificationMapper.updateById(notification);
        }
        return notificationView(notification);
    }

    private AfterSaleAppealDetailView createInternal(long afterSaleId, CreateAfterSaleAppealRequest request,
                                                       long userId) {
        AfterSaleRequest afterSale = afterSaleMapper.selectOne(new LambdaQueryWrapper<AfterSaleRequest>()
                .eq(AfterSaleRequest::getId, afterSaleId).eq(AfterSaleRequest::getUserId, userId).last("FOR UPDATE"));
        if (afterSale == null) throw BusinessException.notFound("RESOURCE_NOT_FOUND", "售后申请不存在");
        if (afterSale.getStatus() != AfterSaleStatus.REJECTED
                && !(afterSale.getStatus() == AfterSaleStatus.PENDING
                    && afterSale.getCreatedAt() != null
                    && afterSale.getCreatedAt().plusHours(48).isBefore(LocalDateTime.now()))) {
            throw BusinessException.conflict("APPEAL_WINDOW_NOT_OPEN", "当前售后不满足申诉条件");
        }
        if (appealMapper.selectOne(new LambdaQueryWrapper<AfterSaleAppeal>()
                .eq(AfterSaleAppeal::getAfterSaleId, afterSaleId)) != null) {
            throw BusinessException.conflict("APPEAL_ALREADY_EXISTS", "同一售后只能提交一次申诉");
        }
        if (!request.version().equals(afterSale.getVersion())) {
            throw BusinessException.conflict("APPEAL_VERSION_CONFLICT", "售后版本已变化");
        }
        String reason = requireText(request.reasonDescription(), 500, "reasonDescription");
        List<String> evidence = request.evidenceUrls() == null ? List.of()
                : contentSafety.imageUrls("evidenceUrls", request.evidenceUrls(), 9);
        AfterSaleAppeal appeal = new AfterSaleAppeal();
        appeal.setAppealNo(numbers.next("AP"));
        appeal.setAfterSaleId(afterSaleId);
        OrderInfo order = orderMapper.selectById(afterSale.getOrderId());
        appeal.setShopId(order.getShopId());
        appeal.setAppellantUserId(userId);
        appeal.setTriggerType(afterSale.getStatus() == AfterSaleStatus.REJECTED
                ? AfterSaleAppealTriggerType.MERCHANT_REJECTED : AfterSaleAppealTriggerType.MERCHANT_TIMEOUT);
        appeal.setStatus(AfterSaleAppealStatus.PENDING);
        appeal.setReasonCode(requireText(request.reasonCode(), 30, "reasonCode"));
        appeal.setReasonDescription(reason);
        appeal.setEvidenceJson(evidence);
        if (appeal.getTriggerType() == AfterSaleAppealTriggerType.MERCHANT_REJECTED) {
            appeal.setMerchantReviewerId(afterSale.getReviewerId());
            appeal.setMerchantReviewComment(afterSale.getReviewComment());
            appeal.setMerchantReviewedAt(afterSale.getReviewedAt());
        }
        appealMapper.insert(appeal);
        notify(appeal, MerchantNotificationType.AFTER_SALE_APPEAL_SUBMITTED);
        return detail(appealMapper.selectById(appeal.getId()));
    }

    private PlatformAfterSaleAppealDetailView decideInternal(long appealId, DecideAfterSaleAppealRequest request,
                                                               long operatorId) {
        AfterSaleAppeal appeal = appealMapper.selectOne(new LambdaQueryWrapper<AfterSaleAppeal>()
                .eq(AfterSaleAppeal::getId, appealId).last("FOR UPDATE"));
        if (appeal == null) throw BusinessException.notFound("APPEAL_NOT_FOUND", "申诉不存在");
        if (appeal.getStatus() != AfterSaleAppealStatus.PENDING) {
            throw BusinessException.conflict("APPEAL_NOT_DECIDABLE", "申诉已经裁决");
        }
        if (!request.version().equals(appeal.getVersion())) {
            throw BusinessException.conflict("APPEAL_VERSION_CONFLICT", "申诉版本已变化");
        }
        AfterSaleRequest afterSale = afterSaleMapper.selectOne(new LambdaQueryWrapper<AfterSaleRequest>()
                .eq(AfterSaleRequest::getId, appeal.getAfterSaleId()).last("FOR UPDATE"));
        if (afterSale == null || afterSale.getStatus() == AfterSaleStatus.COMPLETED
                || afterSale.getStatus() == AfterSaleStatus.CANCELLED) {
            throw BusinessException.conflict("AFTER_SALE_ALREADY_SETTLED", "售后已完成或撤销");
        }
        String comment = requireText(request.reviewComment(), 500, "reviewComment");
        appeal.setDecision(request.decision());
        appeal.setDecidedBy(operatorId);
        appeal.setDecisionComment(comment);
        appeal.setDecidedAt(LocalDateTime.now());
        if (request.decision() == AfterSaleAppealDecision.REJECT) {
            if (request.approvedQuantity() != null || request.approvedAmount() != null) {
                throw BusinessException.badRequest("VALIDATION_FAILED", "驳回时不能提交批准数量或金额");
            }
            appeal.setStatus(AfterSaleAppealStatus.REJECTED);
            afterSale.setStatus(AfterSaleStatus.REJECTED);
        } else {
            BigDecimal amount = parsePositiveMoney(request.approvedAmount(), "approvedAmount");
            if (request.approvedQuantity() == null || request.approvedQuantity() > afterSale.getQuantity()
                    || amount.compareTo(afterSale.getRequestedAmount()) > 0) {
                throw BusinessException.conflict("AFTER_SALE_APPROVAL_EXCEEDED", "平台批准额度超过申请上限");
            }
            appeal.setApprovedQuantity(request.approvedQuantity());
            appeal.setApprovedAmount(amount);
            appeal.setStatus(AfterSaleAppealStatus.APPROVED);
            afterSale.setApprovedQuantity(request.approvedQuantity());
            afterSale.setApprovedAmount(amount);
            afterSale.setReviewerId(operatorId);
            afterSale.setReviewComment(comment);
            afterSale.setReviewedAt(LocalDateTime.now());
            afterSale.setStatus(afterSale.getRequestType() == AfterSaleType.RETURN_REFUND
                    ? AfterSaleStatus.WAITING_RETURN : AfterSaleStatus.REFUNDING);
            afterSale.setRefundStatus(afterSale.getRequestType() == AfterSaleType.RETURN_REFUND
                    ? RefundStatus.NOT_STARTED : RefundStatus.PROCESSING);
        }
        appealMapper.updateById(appeal);
        afterSaleMapper.updateById(afterSale);
        if (request.decision() == AfterSaleAppealDecision.APPROVE
                && afterSale.getRequestType() == AfterSaleType.REFUND_ONLY) {
            shopAfterSaleService.executePlatformRefund(appeal.getShopId(), afterSale.getId(), operatorId);
        }
        notify(appeal, MerchantNotificationType.AFTER_SALE_APPEAL_DECIDED);
        return platformDetailView(appealMapper.selectById(appeal.getId()));
    }

    private void notify(AfterSaleAppeal appeal, MerchantNotificationType type) {
        for (Long recipient : shopUserMapper.selectActiveUserIdsByPermission(appeal.getShopId(), "shop:after-sale:manage")) {
            if (notificationMapper.selectOne(new LambdaQueryWrapper<MerchantNotification>()
                    .eq(MerchantNotification::getAppealId, appeal.getId())
                    .eq(MerchantNotification::getNotificationType, type)
                    .eq(MerchantNotification::getRecipientUserId, recipient)) != null) continue;
            AfterSaleRequest request = afterSaleMapper.selectById(appeal.getAfterSaleId());
            MerchantNotification notification = new MerchantNotification();
            notification.setShopId(appeal.getShopId());
            notification.setRecipientUserId(recipient);
            notification.setAppealId(appeal.getId());
            notification.setAfterSaleId(appeal.getAfterSaleId());
            notification.setNotificationType(type);
            notification.setTitle(type == MerchantNotificationType.AFTER_SALE_APPEAL_SUBMITTED
                    ? "收到新的售后申诉" : "售后申诉已有平台裁决");
            notification.setContent(type == MerchantNotificationType.AFTER_SALE_APPEAL_SUBMITTED
                    ? "申诉 " + appeal.getAppealNo() + " 待平台裁决。"
                    : "申诉 " + appeal.getAppealNo() + " 的平台裁决结果为 " + appeal.getDecision() + "。" );
            notificationMapper.insert(notification);
        }
    }

    private AfterSaleAppealDetailView detail(AfterSaleAppeal appeal) {
        AfterSaleRequest request = afterSaleMapper.selectById(appeal.getAfterSaleId());
        OrderInfo order = orderMapper.selectById(request.getOrderId());
        SysUser decidedBy = appeal.getDecidedBy() == null ? null : userMapper.selectById(appeal.getDecidedBy());
        return new AfterSaleAppealDetailView(id(appeal.getId()), appeal.getAppealNo(),
                new AfterSaleAppealAfterSaleView(id(request.getId()), request.getAfterSaleNo(), request.getRequestType(),
                        request.getStatus(), request.getRefundStatus(),
                        new org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleOrderSnapshot(
                                id(order.getId()), order.getOrderNo(), order.getOrderStatus()),
                        money(request.getRequestedAmount()), request.getApprovedAmount() == null ? null : money(request.getApprovedAmount())),
                appeal.getTriggerType(), appeal.getStatus(), appeal.getReasonCode(), appeal.getReasonDescription(),
                appeal.getEvidenceJson(), appeal.getMerchantReviewerId() == null ? null
                        : new org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleReviewView(
                                id(appeal.getMerchantReviewerId()), appeal.getMerchantReviewComment(), time(appeal.getMerchantReviewedAt())),
                appeal.getDecision(), appeal.getApprovedQuantity(), appeal.getApprovedAmount() == null ? null : money(appeal.getApprovedAmount()),
                decidedBy == null ? null : new OperatorBrief(id(decidedBy.getId()), decidedBy.getUsername(), decidedBy.getNickname()),
                appeal.getDecisionComment(), time(appeal.getDecidedAt()), appeal.getVersion(), time(appeal.getCreatedAt()), time(appeal.getUpdatedAt()));
    }

    private PlatformAfterSaleAppealSummaryView platformSummary(AfterSaleAppeal appeal) {
        AfterSaleRequest request = afterSaleMapper.selectById(appeal.getAfterSaleId());
        OrderInfo order = orderMapper.selectById(request.getOrderId());
        return new PlatformAfterSaleAppealSummaryView(id(appeal.getId()), appeal.getAppealNo(), id(request.getId()),
                request.getAfterSaleNo(), appeal.getTriggerType(), appeal.getStatus(),
                IdentityViewMapper.shop(shopMapper.selectById(appeal.getShopId())),
                IdentityViewMapper.user(userMapper.selectById(appeal.getAppellantUserId())), request.getRequestType(),
                money(request.getRequestedAmount()), time(appeal.getCreatedAt()), time(appeal.getDecidedAt()));
    }

    private PlatformAfterSaleAppealDetailView platformDetailView(AfterSaleAppeal appeal) {
        AfterSaleRequest request = afterSaleMapper.selectById(appeal.getAfterSaleId());
        OrderInfo order = orderMapper.selectById(request.getOrderId());
        OrderItem item = itemMapper.selectById(request.getOrderItemId());
        AfterSaleAppealDetailView base = detail(appeal);
        return new PlatformAfterSaleAppealDetailView(base.id(), base.appealNo(), base.afterSale(), base.triggerType(),
                base.status(), base.reasonCode(), base.reasonDescription(), base.evidenceUrls(), base.merchantReview(),
                base.decision(), base.approvedQuantity(), base.approvedAmount(), base.decidedBy(), base.decisionComment(),
                base.decidedAt(), base.version(), base.createdAt(), base.updatedAt(),
                IdentityViewMapper.shop(shopMapper.selectById(appeal.getShopId())),
                IdentityViewMapper.user(userMapper.selectById(appeal.getAppellantUserId())),
                new org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleOrderSnapshot(id(order.getId()), order.getOrderNo(), order.getOrderStatus()),
                new org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleItemSnapshot(id(item.getId()), item.getProductName(), item.getSkuName(), item.getSpecJson(), item.getImageUrl(), money(item.getUnitPrice()), item.getQuantity()));
    }

    private MerchantNotificationView notificationView(MerchantNotification notification) {
        AfterSaleAppeal appeal = appealMapper.selectById(notification.getAppealId());
        AfterSaleRequest request = afterSaleMapper.selectById(notification.getAfterSaleId());
        return new MerchantNotificationView(id(notification.getId()), notification.getNotificationType(),
                id(notification.getAppealId()), appeal == null ? null : appeal.getAppealNo(), id(notification.getAfterSaleId()),
                request == null ? null : request.getAfterSaleNo(), notification.getTitle(), notification.getContent(),
                time(notification.getReadAt()), time(notification.getCreatedAt()));
    }

    private String requireText(String value, int max, String field) {
        String normalized = Formatters.trimToNull(value);
        if (normalized == null || normalized.length() > max) {
            throw BusinessException.badRequest("VALIDATION_FAILED", field + " 长度无效");
        }
        return normalized;
    }

    private BigDecimal parsePositiveMoney(String value, String field) {
        if (value == null || !value.matches(MONEY_PATTERN) || new BigDecimal(value).signum() <= 0) {
            throw BusinessException.badRequest("VALIDATION_FAILED", field + "必须为正的两位小数");
        }
        return new BigDecimal(value);
    }

    private void validatePage(long page, long pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw BusinessException.badRequest("BAD_REQUEST", "分页参数超出范围");
        }
    }
}
