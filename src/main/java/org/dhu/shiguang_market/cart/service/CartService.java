package org.dhu.shiguang_market.cart.service;

import static org.dhu.shiguang_market.common.util.Formatters.id;
import static org.dhu.shiguang_market.common.util.Formatters.money;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.dhu.shiguang_market.cart.dto.CartDtos.AddCartItemRequest;
import org.dhu.shiguang_market.cart.dto.CartDtos.CartItemView;
import org.dhu.shiguang_market.cart.dto.CartDtos.CartShopGroupView;
import org.dhu.shiguang_market.cart.dto.CartDtos.CartView;
import org.dhu.shiguang_market.cart.dto.CartDtos.CheckoutItemView;
import org.dhu.shiguang_market.cart.dto.CartDtos.CheckoutPreviewRequest;
import org.dhu.shiguang_market.cart.dto.CartDtos.CheckoutPreviewView;
import org.dhu.shiguang_market.cart.dto.CartDtos.CheckoutShopGroupView;
import org.dhu.shiguang_market.cart.dto.CartDtos.InvalidCheckoutItemView;
import org.dhu.shiguang_market.cart.dto.CartDtos.UpdateCartItemRequest;
import org.dhu.shiguang_market.cart.dto.CartDtos.UpdateCartSelectionRequest;
import org.dhu.shiguang_market.cart.mapper.CartItemMapper;
import org.dhu.shiguang_market.cart.model.CartItem;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.EnabledStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ProductStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.address.model.UserAddress;
import org.dhu.shiguang_market.address.service.AddressService;
import org.dhu.shiguang_market.identity.service.IdentityViewMapper;
import org.dhu.shiguang_market.inventory.mapper.InventoryStockMapper;
import org.dhu.shiguang_market.inventory.model.InventoryStock;
import org.dhu.shiguang_market.product.mapper.ProductSkuMapper;
import org.dhu.shiguang_market.product.mapper.ProductSpuMapper;
import org.dhu.shiguang_market.product.model.ProductSku;
import org.dhu.shiguang_market.product.model.ProductSpu;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.dhu.shiguang_market.shop.model.Shop;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {
    private final CartItemMapper cartMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductSpuMapper spuMapper;
    private final InventoryStockMapper stockMapper;
    private final ShopMapper shopMapper;
    private final AddressService addressService;
    private final CurrentUserService currentUser;

    public CartService(CartItemMapper cartMapper, ProductSkuMapper skuMapper,
                       ProductSpuMapper spuMapper, InventoryStockMapper stockMapper,
                       ShopMapper shopMapper, AddressService addressService,
                       CurrentUserService currentUser) {
        this.cartMapper = cartMapper;
        this.skuMapper = skuMapper;
        this.spuMapper = spuMapper;
        this.stockMapper = stockMapper;
        this.shopMapper = shopMapper;
        this.addressService = addressService;
        this.currentUser = currentUser;
    }

    public CartView view() {
        currentUser.requirePermission("cart:manage");
        List<CartItemView> items = cartMapper.selectList(new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, currentUser.id())
                        .orderByDesc(CartItem::getUpdatedAt).orderByDesc(CartItem::getId))
                .stream().map(this::itemView).toList();
        Map<Long, List<CartItemView>> grouped = new LinkedHashMap<>();
        Map<Long, Shop> shops = new LinkedHashMap<>();
        for (CartItemView item : items) {
            ProductSku sku = skuMapper.selectById(Long.parseLong(item.skuId()));
            if (sku == null) continue;
            shops.putIfAbsent(sku.getShopId(), shopMapper.selectById(sku.getShopId()));
            grouped.computeIfAbsent(sku.getShopId(), ignored -> new ArrayList<>()).add(item);
        }
        List<CartShopGroupView> groups = grouped.entrySet().stream()
                .filter(entry -> shops.get(entry.getKey()) != null)
                .map(entry -> new CartShopGroupView(IdentityViewMapper.shop(shops.get(entry.getKey())), entry.getValue()))
                .toList();
        int selectedItems = (int) items.stream().filter(CartItemView::selected).count();
        int selectedQuantity = items.stream().filter(CartItemView::selected)
                .mapToInt(CartItemView::quantity).sum();
        BigDecimal selectedAmount = items.stream().filter(item -> item.selected() && item.valid())
                .map(item -> new BigDecimal(item.currentSalePrice()).multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartView(groups, selectedItems, selectedQuantity, money(selectedAmount));
    }

    @Transactional
    public CartItemView add(AddCartItemRequest request) {
        currentUser.requirePermission("cart:manage");
        long userId = currentUser.id();
        long skuId = parseId(request.skuId());
        ProductSku sku = skuMapper.selectById(skuId);
        ProductSpu spu = sku == null ? null : spuMapper.selectById(sku.getSpuId());
        Shop shop = sku == null ? null : shopMapper.selectById(sku.getShopId());
        InventoryStock stock = sku == null ? null : stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>()
                .eq(InventoryStock::getSkuId, skuId));
        if (sku == null || spu == null || shop == null || sku.getStatus() != EnabledStatus.ENABLED
                || spu.getStatus() != ProductStatus.ON_SHELF || shop.getStatus() != ShopStatus.ACTIVE
                || stock == null || stock.getAvailableQuantity() < request.quantity()) {
            throw BusinessException.unprocessable("SKU_NOT_PURCHASABLE", "SKU 当前不可购买");
        }
        CartItem item = cartMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId).eq(CartItem::getSkuId, skuId));
        if (item == null) {
            item = new CartItem();
            item.setUserId(userId);
            item.setSkuId(skuId);
            item.setQuantity(request.quantity());
            item.setSelected(true);
            cartMapper.insert(item);
        } else {
            int quantity = item.getQuantity() + request.quantity();
            if (quantity > 999) {
                throw BusinessException.unprocessable("CART_QUANTITY_LIMIT_EXCEEDED", "购物车数量超过 999");
            }
            if (quantity > stock.getAvailableQuantity()) {
                throw BusinessException.unprocessable("SKU_NOT_PURCHASABLE", "SKU 库存不足");
            }
            item.setQuantity(quantity);
            cartMapper.updateById(item);
        }
        return itemView(cartMapper.selectById(item.getId()));
    }

    @Transactional
    public CartItemView update(long cartItemId, UpdateCartItemRequest request) {
        if (!request.hasQuantity() && !request.hasSelected()) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "至少提交一个修改字段");
        }
        if (request.hasQuantity() && request.quantity() == null) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "quantity 不允许为 null");
        }
        if (request.hasSelected() && request.selected() == null) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "selected 不允许为 null");
        }
        CartItem item = owned(cartItemId);
        if (request.hasQuantity()) {
            if (request.quantity() < 1 || request.quantity() > 999) {
                throw BusinessException.badRequest("VALIDATION_FAILED", "quantity 必须为 1..999");
            }
            item.setQuantity(request.quantity());
            if (Boolean.TRUE.equals(item.getSelected()) && !checkoutLine(item).valid()) {
                item.setSelected(false);
            }
        }
        if (request.hasSelected()) {
            if (request.selected() && !checkoutLine(item).valid()) {
                throw BusinessException.unprocessable("CHECKOUT_ITEMS_INVALID", "失效购物车项不可选中");
            }
            item.setSelected(request.selected());
        }
        cartMapper.updateById(item);
        return itemView(cartMapper.selectById(cartItemId));
    }

    public void delete(long cartItemId) {
        cartMapper.deleteById(owned(cartItemId));
    }

    @Transactional
    public CartView updateSelection(UpdateCartSelectionRequest request) {
        if (request.cartItemIds().isEmpty()) return view();
        Set<Long> ids = request.cartItemIds().stream().map(this::parseId).collect(Collectors.toSet());
        if (ids.size() != request.cartItemIds().size()) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "cartItemIds 不可重复");
        }
        List<CartItem> items = cartMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .in(CartItem::getId, ids).eq(CartItem::getUserId, currentUser.id()));
        if (items.size() != ids.size()) {
            throw BusinessException.notFound("CART_ITEM_NOT_FOUND", "购物车项不存在");
        }
        if (request.selected() && items.stream().anyMatch(item -> !checkoutLine(item).valid())) {
            throw BusinessException.unprocessable("CHECKOUT_ITEMS_INVALID", "失效购物车项不可选中");
        }
        items.forEach(item -> {
            item.setSelected(request.selected());
            cartMapper.updateById(item);
        });
        return view();
    }

    public CheckoutPreviewView preview(CheckoutPreviewRequest request) {
        currentUser.requirePermission("trade:create");
        long userId = currentUser.id();
        UserAddress address = addressService.ownedEntity(parseNullableId(request.addressId()), userId);
        List<CartItem> items = resolveItems(userId, request.cartItemIds());
        List<CheckoutLine> lines = items.stream().map(this::checkoutLine).toList();
        Map<Long, List<CheckoutLine>> grouped = lines.stream()
                .filter(line -> line.shop() != null)
                .collect(Collectors.groupingBy(line -> line.shop().getId(), LinkedHashMap::new, Collectors.toList()));
        List<CheckoutShopGroupView> shopGroups = grouped.entrySet().stream().map(entry -> {
            List<CheckoutItemView> views = entry.getValue().stream().map(CheckoutLine::view).toList();
            BigDecimal amount = entry.getValue().stream().filter(CheckoutLine::valid)
                    .map(CheckoutLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            String remark = request.shopRemarks() == null ? null : request.shopRemarks().get(entry.getKey().toString());
            if (remark != null && remark.length() > 500) {
                throw BusinessException.badRequest("VALIDATION_FAILED", "店铺备注最多 500 字");
            }
            return new CheckoutShopGroupView(IdentityViewMapper.shop(entry.getValue().getFirst().shop()),
                    views, money(amount), "0.00", money(amount), remark);
        }).toList();
        List<InvalidCheckoutItemView> invalid = lines.stream().filter(line -> !line.valid())
                .map(line -> new InvalidCheckoutItemView(id(line.cart().getId()), id(line.cart().getSkuId()),
                        line.reason(), line.message())).toList();
        BigDecimal total = lines.stream().filter(CheckoutLine::valid).map(CheckoutLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CheckoutPreviewView(address == null ? null : IdentityViewMapper.address(address),
                shopGroups, money(total), "0.00", money(total), !lines.isEmpty() && invalid.isEmpty(), invalid);
    }

    public List<CartItem> resolveItems(long userId, List<String> requestedIds) {
        LambdaQueryWrapper<CartItem> query = new LambdaQueryWrapper<CartItem>().eq(CartItem::getUserId, userId);
        if (requestedIds == null) {
            query.eq(CartItem::getSelected, true);
        } else {
            if (requestedIds.isEmpty()) {
                throw BusinessException.badRequest("VALIDATION_FAILED", "cartItemIds 显式提交时不可为空");
            }
            Set<Long> ids = requestedIds.stream().map(this::parseId).collect(Collectors.toCollection(LinkedHashSet::new));
            if (ids.size() != requestedIds.size()) {
                throw BusinessException.badRequest("VALIDATION_FAILED", "cartItemIds 不可重复");
            }
            query.in(CartItem::getId, ids);
        }
        List<CartItem> items = cartMapper.selectList(query.orderByAsc(CartItem::getSkuId));
        if (items.isEmpty() || (requestedIds != null && items.size() != requestedIds.size())) {
            throw BusinessException.unprocessable("CHECKOUT_ITEMS_INVALID", "没有可结算的购物车项");
        }
        return items;
    }

    public CheckoutLine checkoutLine(CartItem cart) {
        ProductSku sku = skuMapper.selectById(cart.getSkuId());
        ProductSpu spu = sku == null ? null : spuMapper.selectById(sku.getSpuId());
        Shop shop = sku == null ? null : shopMapper.selectById(sku.getShopId());
        InventoryStock stock = sku == null ? null : stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>()
                .eq(InventoryStock::getSkuId, sku.getId()));
        String reason = null;
        String message = null;
        if (sku == null) {
            reason = "SKU_DELETED"; message = "SKU 不存在或已删除";
        } else if (spu == null) {
            reason = "PRODUCT_DELETED"; message = "商品不存在或已删除";
        } else if (shop == null) {
            reason = "SHOP_UNAVAILABLE"; message = "店铺不可购买";
        } else if (shop.getStatus() != ShopStatus.ACTIVE) {
            reason = "SHOP_UNAVAILABLE"; message = "店铺不可购买";
        } else if (spu.getStatus() != ProductStatus.ON_SHELF) {
            reason = "PRODUCT_OFF_SHELF"; message = "商品已下架";
        } else if (sku.getStatus() != EnabledStatus.ENABLED) {
            reason = "SKU_DISABLED"; message = "SKU 已停用";
        } else if (stock == null || stock.getAvailableQuantity() <= 0) {
            reason = "OUT_OF_STOCK"; message = "商品无库存";
        } else if (stock.getAvailableQuantity() < cart.getQuantity()) {
            reason = "QUANTITY_EXCEEDS_STOCK"; message = "购物数量超过可用库存";
        }
        BigDecimal amount = sku == null ? BigDecimal.ZERO
                : sku.getSalePrice().multiply(BigDecimal.valueOf(cart.getQuantity()));
        CheckoutItemView view = new CheckoutItemView(id(cart.getId()), id(cart.getSkuId()),
                spu == null ? "" : spu.getProductName(), sku == null ? "" : sku.getSkuName(),
                sku == null ? "0.00" : money(sku.getSalePrice()), cart.getQuantity(), money(amount),
                "0.00", money(amount), reason == null, reason);
        return new CheckoutLine(cart, sku, spu, shop, stock, view, amount, reason == null, reason, message);
    }

    private CartItemView itemView(CartItem cart) {
        CheckoutLine line = checkoutLine(cart);
        ProductSku sku = line.sku();
        ProductSpu spu = line.spu();
        return new CartItemView(id(cart.getId()), id(cart.getSkuId()), spu == null ? null : id(spu.getId()),
                spu == null ? "" : spu.getProductName(), sku == null ? "" : sku.getSkuName(),
                sku == null ? Map.of() : sku.getSpecJson(), sku == null ? null : sku.getImageUrl(),
                cart.getQuantity(), Boolean.TRUE.equals(cart.getSelected()) && line.valid(),
                sku == null ? "0.00" : money(sku.getSalePrice()),
                line.stock() == null ? 0 : line.stock().getAvailableQuantity(),
                line.valid(), line.reason(), org.dhu.shiguang_market.common.util.Formatters.time(cart.getUpdatedAt()));
    }

    private CartItem owned(long cartItemId) {
        CartItem item = cartMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getId, cartItemId).eq(CartItem::getUserId, currentUser.id()));
        if (item == null) throw BusinessException.notFound("CART_ITEM_NOT_FOUND", "购物车项不存在");
        return item;
    }

    private Long parseNullableId(String value) {
        return value == null ? null : parseId(value);
    }

    private long parseId(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException ex) {
            throw BusinessException.badRequest("BAD_REQUEST", "ID 格式错误");
        }
    }

    public record CheckoutLine(
            CartItem cart, ProductSku sku, ProductSpu spu, Shop shop, InventoryStock stock,
            CheckoutItemView view, BigDecimal amount, boolean valid, String reason, String message) {
    }
}
