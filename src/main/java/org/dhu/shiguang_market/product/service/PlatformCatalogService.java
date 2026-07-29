package org.dhu.shiguang_market.product.service;

import static org.dhu.shiguang_market.common.util.Formatters.id;
import static org.dhu.shiguang_market.common.util.Formatters.time;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.AttributeValueType;
import org.dhu.shiguang_market.common.model.MarketEnums.EnabledStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.util.ContentSafety;
import org.dhu.shiguang_market.product.dto.ProductDtos.BrandView;
import org.dhu.shiguang_market.product.dto.ProductDtos.CategoryAttributeView;
import org.dhu.shiguang_market.product.mapper.ProductAttributeValueMapper;
import org.dhu.shiguang_market.product.mapper.ProductBrandMapper;
import org.dhu.shiguang_market.product.mapper.ProductCategoryAttributeMapper;
import org.dhu.shiguang_market.product.mapper.ProductCategoryMapper;
import org.dhu.shiguang_market.product.mapper.ProductSpuMapper;
import org.dhu.shiguang_market.product.model.ProductAttributeValue;
import org.dhu.shiguang_market.product.model.ProductBrand;
import org.dhu.shiguang_market.product.model.ProductCategory;
import org.dhu.shiguang_market.product.model.ProductCategoryAttribute;
import org.dhu.shiguang_market.product.model.ProductSpu;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.BrandRequest;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.CategoryAttributeRequest;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.CategoryUpsertRequest;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.PlatformCategoryNode;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.PlatformCategoryView;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.StatusRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformCatalogService {
    private static final Pattern STABLE_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");
    private final ProductCategoryMapper categoryMapper;
    private final ProductCategoryAttributeMapper attributeMapper;
    private final ProductAttributeValueMapper valueMapper;
    private final ProductBrandMapper brandMapper;
    private final ProductSpuMapper spuMapper;
    private final PublicCatalogService publicCatalog;
    private final CurrentUserService currentUser;
    private final ContentSafety contentSafety;

    public PlatformCatalogService(ProductCategoryMapper categoryMapper,
                                  ProductCategoryAttributeMapper attributeMapper,
                                  ProductAttributeValueMapper valueMapper,
                                  ProductBrandMapper brandMapper, ProductSpuMapper spuMapper,
                                  PublicCatalogService publicCatalog, CurrentUserService currentUser,
                                  ContentSafety contentSafety) {
        this.categoryMapper = categoryMapper;
        this.attributeMapper = attributeMapper;
        this.valueMapper = valueMapper;
        this.brandMapper = brandMapper;
        this.spuMapper = spuMapper;
        this.publicCatalog = publicCatalog;
        this.currentUser = currentUser;
        this.contentSafety = contentSafety;
    }

    public List<PlatformCategoryNode> categoryTree(EnabledStatus status) {
        currentUser.requirePermission("platform:catalog:manage");
        LambdaQueryWrapper<ProductCategory> query = new LambdaQueryWrapper<ProductCategory>()
                .orderByAsc(ProductCategory::getSortOrder).orderByAsc(ProductCategory::getId);
        if (status != null) query.eq(ProductCategory::getStatus, status);
        List<ProductCategory> all = categoryMapper.selectList(query);
        Map<Long, List<ProductCategory>> children = all.stream().filter(value -> value.getParentId() != null)
                .collect(Collectors.groupingBy(ProductCategory::getParentId));
        return all.stream().filter(value -> value.getParentId() == null)
                .map(value -> node(value, children)).toList();
    }

    @Transactional
    public PlatformCategoryView createCategory(CategoryUpsertRequest request) {
        currentUser.requirePermission("platform:catalog:manage");
        String categoryCode = stableCode(request.categoryCode(), "categoryCode");
        if (categoryMapper.exists(new LambdaQueryWrapper<ProductCategory>()
                .eq(ProductCategory::getCategoryCode, categoryCode))) {
            throw BusinessException.conflict("CATEGORY_CODE_ALREADY_EXISTS", "类目代码已存在");
        }
        Long parentId = parseNullableId(request.parentId());
        validateParent(parentId, null);
        ProductCategory category = new ProductCategory();
        category.setParentId(parentId);
        category.setCategoryName(request.categoryName().trim());
        category.setCategoryCode(categoryCode);
        category.setSortOrder(request.sortOrder());
        category.setStatus(EnabledStatus.ENABLED);
        categoryMapper.insert(category);
        return view(categoryMapper.selectById(category.getId()));
    }

    @Transactional
    public PlatformCategoryView updateCategory(long categoryId, CategoryUpsertRequest request) {
        currentUser.requirePermission("platform:catalog:manage");
        ProductCategory category = requireCategory(categoryId);
        String categoryCode = stableCode(request.categoryCode(), "categoryCode");
        if (!category.getCategoryCode().equals(categoryCode)) {
            throw BusinessException.conflict("IMMUTABLE_FIELD_CHANGED", "categoryCode 创建后不可修改");
        }
        Long parentId = parseNullableId(request.parentId());
        validateParent(parentId, categoryId);
        category.setParentId(parentId);
        category.setCategoryName(request.categoryName().trim());
        category.setSortOrder(request.sortOrder());
        categoryMapper.updateById(category);
        return view(categoryMapper.selectById(categoryId));
    }

    @Transactional
    public PlatformCategoryView categoryStatus(long categoryId, StatusRequest request) {
        currentUser.requirePermission("platform:catalog:manage");
        ProductCategory category = requireCategory(categoryId);
        if (request.targetStatus() == EnabledStatus.DISABLED) {
            if (categoryMapper.exists(new LambdaQueryWrapper<ProductCategory>()
                    .eq(ProductCategory::getParentId, categoryId).eq(ProductCategory::getStatus, EnabledStatus.ENABLED))) {
                throw BusinessException.unprocessable("CATEGORY_HAS_ENABLED_CHILDREN", "请先禁用子类目");
            }
            if (spuMapper.exists(new LambdaQueryWrapper<ProductSpu>().eq(ProductSpu::getCategoryId, categoryId))) {
                throw BusinessException.unprocessable("CATEGORY_IN_USE", "类目正在使用");
            }
        }
        category.setStatus(request.targetStatus());
        categoryMapper.updateById(category);
        return view(categoryMapper.selectById(categoryId));
    }

    public List<CategoryAttributeView> attributes(long categoryId) {
        currentUser.requirePermission("platform:catalog:manage");
        requireCategory(categoryId);
        return publicCatalog.attributes(categoryId, false);
    }

    @Transactional
    public CategoryAttributeView createAttribute(long categoryId, CategoryAttributeRequest request) {
        currentUser.requirePermission("platform:catalog:manage");
        requireLeaf(categoryId);
        validateOptions(request);
        ProductCategoryAttribute attribute = new ProductCategoryAttribute();
        attribute.setCategoryId(categoryId);
        apply(attribute, request);
        attribute.setStatus(EnabledStatus.ENABLED);
        attributeMapper.insert(attribute);
        return publicCatalog.attributes(categoryId, false).stream()
                .filter(view -> view.id().equals(id(attribute.getId()))).findFirst().orElseThrow();
    }

    @Transactional
    public CategoryAttributeView updateAttribute(long categoryId, long attributeId,
                                                  CategoryAttributeRequest request) {
        currentUser.requirePermission("platform:catalog:manage");
        ProductCategoryAttribute attribute = scopedAttribute(categoryId, attributeId);
        validateOptions(request);
        if (attribute.getValueType() != request.valueType()
                && valueMapper.exists(new LambdaQueryWrapper<ProductAttributeValue>()
                .eq(ProductAttributeValue::getAttributeId, attributeId))) {
            throw BusinessException.unprocessable("CATALOG_RESOURCE_IN_USE", "使用中的属性不可更改值类型");
        }
        if (attribute.getValueType() == AttributeValueType.OPTION && request.valueType() == AttributeValueType.OPTION
                && attribute.getOptionsJson() != null && request.options() != null) {
            Set<String> requestedOptions = request.options().stream().map(String::trim).collect(Collectors.toSet());
            Set<String> removed = attribute.getOptionsJson().stream()
                    .filter(option -> !requestedOptions.contains(option)).collect(Collectors.toSet());
            if (!removed.isEmpty() && valueMapper.selectList(new LambdaQueryWrapper<ProductAttributeValue>()
                    .eq(ProductAttributeValue::getAttributeId, attributeId)).stream()
                    .anyMatch(value -> removed.contains(value.getAttributeValue()))) {
                throw BusinessException.unprocessable("CATALOG_RESOURCE_IN_USE", "选项正在使用，不能移除");
            }
        }
        apply(attribute, request);
        attributeMapper.updateById(attribute);
        return publicCatalog.attributes(categoryId, false).stream()
                .filter(view -> view.id().equals(id(attributeId))).findFirst().orElseThrow();
    }

    @Transactional
    public CategoryAttributeView attributeStatus(long categoryId, long attributeId, StatusRequest request) {
        currentUser.requirePermission("platform:catalog:manage");
        ProductCategoryAttribute attribute = scopedAttribute(categoryId, attributeId);
        attribute.setStatus(request.targetStatus());
        attributeMapper.updateById(attribute);
        return publicCatalog.attributes(categoryId, false).stream()
                .filter(view -> view.id().equals(id(attributeId))).findFirst().orElseThrow();
    }

    public PageView<BrandView> brands(EnabledStatus status, String keyword, long page, long pageSize) {
        currentUser.requirePermission("platform:catalog:manage");
        return publicCatalog.brands(keyword, status, page, pageSize, "brandName,asc", false);
    }

    @Transactional
    public BrandView createBrand(BrandRequest request) {
        currentUser.requirePermission("platform:catalog:manage");
        ensureBrandCode(stableCode(request.brandCode(), "brandCode"), null);
        ProductBrand brand = new ProductBrand();
        apply(brand, request);
        brand.setStatus(EnabledStatus.ENABLED);
        brandMapper.insert(brand);
        return brandView(brandMapper.selectById(brand.getId()));
    }

    @Transactional
    public BrandView updateBrand(long brandId, BrandRequest request) {
        currentUser.requirePermission("platform:catalog:manage");
        ProductBrand brand = requireBrand(brandId);
        String brandCode = stableCode(request.brandCode(), "brandCode");
        if (!brand.getBrandCode().equals(brandCode)) {
            throw BusinessException.conflict("IMMUTABLE_FIELD_CHANGED", "brandCode 创建后不可修改");
        }
        apply(brand, request);
        brandMapper.updateById(brand);
        return brandView(brandMapper.selectById(brandId));
    }

    @Transactional
    public BrandView brandStatus(long brandId, StatusRequest request) {
        currentUser.requirePermission("platform:catalog:manage");
        ProductBrand brand = requireBrand(brandId);
        brand.setStatus(request.targetStatus());
        brandMapper.updateById(brand);
        return brandView(brandMapper.selectById(brandId));
    }

    private PlatformCategoryNode node(ProductCategory category, Map<Long, List<ProductCategory>> children) {
        PlatformCategoryView view = view(category);
        return new PlatformCategoryNode(view.id(), view.parentId(), view.categoryCode(), view.categoryName(),
                view.sortOrder(), view.status(), view.leaf(), view.childrenCount(), view.attributeCount(),
                view.createdAt(), view.updatedAt(), children.getOrDefault(category.getId(), List.of()).stream()
                .map(child -> node(child, children)).toList());
    }

    private PlatformCategoryView view(ProductCategory category) {
        int children = Math.toIntExact(categoryMapper.selectCount(new LambdaQueryWrapper<ProductCategory>()
                .eq(ProductCategory::getParentId, category.getId())));
        int attributes = Math.toIntExact(attributeMapper.selectCount(new LambdaQueryWrapper<ProductCategoryAttribute>()
                .eq(ProductCategoryAttribute::getCategoryId, category.getId())));
        return new PlatformCategoryView(id(category.getId()), id(category.getParentId()), category.getCategoryCode(),
                category.getCategoryName(), category.getSortOrder(), category.getStatus(), children == 0,
                children, attributes, time(category.getCreatedAt()), time(category.getUpdatedAt()));
    }

    private void validateParent(Long parentId, Long movingId) {
        if (parentId == null) return;
        if (Objects.equals(parentId, movingId)) throw BusinessException.conflict("CATEGORY_CYCLE_DETECTED", "类目不能以自身为父类目");
        ProductCategory parent = requireCategory(parentId);
        if (attributeMapper.exists(new LambdaQueryWrapper<ProductCategoryAttribute>()
                .eq(ProductCategoryAttribute::getCategoryId, parentId))
                || spuMapper.exists(new LambdaQueryWrapper<ProductSpu>().eq(ProductSpu::getCategoryId, parentId))) {
            throw BusinessException.unprocessable("CATEGORY_PARENT_NOT_EXTENSIBLE", "该类目不能继续扩展子类目");
        }
        Long cursor = parent.getParentId();
        while (cursor != null) {
            if (Objects.equals(cursor, movingId)) throw BusinessException.conflict("CATEGORY_CYCLE_DETECTED", "类目层级形成环");
            cursor = requireCategory(cursor).getParentId();
        }
    }

    private void requireLeaf(long categoryId) {
        requireCategory(categoryId);
        if (categoryMapper.exists(new LambdaQueryWrapper<ProductCategory>()
                .eq(ProductCategory::getParentId, categoryId))) {
            throw BusinessException.unprocessable("CATEGORY_NOT_LEAF", "只有叶子类目允许属性模板");
        }
    }

    private void validateOptions(CategoryAttributeRequest request) {
        if (request.valueType() == AttributeValueType.OPTION) {
            if (request.options() == null || request.options().isEmpty()) {
                throw BusinessException.badRequest("VALIDATION_FAILED", "OPTION 属性必须提供不重复选项");
            }
            Set<String> normalized = new LinkedHashSet<>();
            for (String option : request.options()) {
                if (option == null || option.trim().isEmpty() || option.trim().length() > 64
                        || !normalized.add(option.trim())) {
                    throw BusinessException.badRequest("VALIDATION_FAILED", "OPTION 属性必须提供不重复的非空选项");
                }
            }
        } else if (request.options() != null) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "非 OPTION 属性不得提交 options");
        }
    }

    private void apply(ProductCategoryAttribute attribute, CategoryAttributeRequest request) {
        attribute.setAttributeName(request.attributeName().trim());
        attribute.setValueType(request.valueType());
        attribute.setUnit(org.dhu.shiguang_market.common.util.Formatters.trimToNull(request.unit()));
        attribute.setIsRequired(request.required());
        attribute.setIsFilterable(request.filterable());
        attribute.setOptionsJson(request.options() == null ? null : request.options().stream()
                .map(String::trim).toList());
        attribute.setSortOrder(request.sortOrder());
    }

    private void apply(ProductBrand brand, BrandRequest request) {
        brand.setBrandName(request.brandName().trim());
        brand.setBrandCode(request.brandCode().trim());
        brand.setLogoUrl(contentSafety.imageUrl("logoUrl", request.logoUrl()));
    }

    private ProductCategory requireCategory(long id) {
        ProductCategory value = categoryMapper.selectById(id);
        if (value == null) throw BusinessException.notFound("CATEGORY_NOT_FOUND", "类目不存在");
        return value;
    }

    private ProductCategoryAttribute scopedAttribute(long categoryId, long attributeId) {
        ProductCategoryAttribute value = attributeMapper.selectOne(new LambdaQueryWrapper<ProductCategoryAttribute>()
                .eq(ProductCategoryAttribute::getId, attributeId)
                .eq(ProductCategoryAttribute::getCategoryId, categoryId));
        if (value == null) throw BusinessException.notFound("RESOURCE_NOT_FOUND", "属性模板不存在");
        return value;
    }

    private ProductBrand requireBrand(long id) {
        ProductBrand value = brandMapper.selectById(id);
        if (value == null) throw BusinessException.notFound("BRAND_NOT_FOUND", "品牌不存在");
        return value;
    }

    private void ensureBrandCode(String code, Long excludedId) {
        LambdaQueryWrapper<ProductBrand> query = new LambdaQueryWrapper<ProductBrand>()
                .eq(ProductBrand::getBrandCode, code.trim());
        if (excludedId != null) query.ne(ProductBrand::getId, excludedId);
        if (brandMapper.exists(query)) throw BusinessException.conflict("BRAND_CODE_ALREADY_EXISTS", "品牌代码已存在");
    }

    private BrandView brandView(ProductBrand brand) {
        return new BrandView(id(brand.getId()), brand.getBrandCode(), brand.getBrandName(), brand.getLogoUrl(), brand.getStatus());
    }

    private Long parseNullableId(String value) {
        if (value == null) return null;
        try { long id = Long.parseLong(value); if (id <= 0) throw new NumberFormatException(); return id; }
        catch (NumberFormatException ex) { throw BusinessException.badRequest("BAD_REQUEST", "ID 格式错误"); }
    }

    private String stableCode(String rawCode, String field) {
        String code = rawCode == null ? "" : rawCode.trim();
        if (!STABLE_CODE.matcher(code).matches()) {
            throw BusinessException.badRequest("VALIDATION_FAILED",
                    field + " 必须符合 ^[A-Z][A-Z0-9_]{1,63}$");
        }
        return code;
    }
}
