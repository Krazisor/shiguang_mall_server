package org.dhu.shiguang_market.shopmember;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.UUID;
import org.dhu.shiguang_market.common.model.MarketEnums.ActiveStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ScopeType;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.identity.mapper.SysRoleMapper;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.identity.model.SysRole;
import org.dhu.shiguang_market.identity.model.SysUser;
import org.dhu.shiguang_market.shop.controller.ShopMemberController;
import org.dhu.shiguang_market.shop.dto.ShopMemberDtos.AddShopMemberRequest;
import org.dhu.shiguang_market.shop.dto.ShopMemberDtos.ChangeShopMemberRoleRequest;
import org.dhu.shiguang_market.shop.dto.ShopMemberDtos.StatusRequest;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.dhu.shiguang_market.shop.mapper.ShopUserMapper;
import org.dhu.shiguang_market.shop.model.Shop;
import org.dhu.shiguang_market.shop.model.ShopUser;
import org.dhu.shiguang_market.task.service.TaskExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * 店铺成员管理集成测试。
 *
 * <p>测试使用真实 Controller、Service、Mapper 和 MySQL；测试事务结束后自动回滚，
 * 可以直接运行，不会在开发数据库中留下测试成员。</p>
 */
@SpringBootTest
@Transactional
class ShopMemberIntegrationTests {
    @Autowired private ShopMemberController controller;
    @Autowired private ShopMapper shopMapper;
    @Autowired private ShopUserMapper shopUserMapper;
    @Autowired private SysUserMapper userMapper;
    @Autowired private SysRoleMapper roleMapper;

    @MockitoBean private CurrentUserService currentUser;
    /** 防止集成测试启动项目定时任务，避免访问与本功能无关的业务数据。 */
    @MockitoBean private TaskExecutionService taskExecutionService;

    private Shop shop;
    private SysRole adminRole;

    @BeforeEach
    void setUp() {
        adminRole = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, "SHOP_ADMIN")
                .eq(SysRole::getScopeType, ScopeType.SHOP));
        assertThat(adminRole).as("schema.sql 应初始化 SHOP_ADMIN 角色").isNotNull();

        shop = insertShop();
        SysUser caller = insertUser("caller");
        insertMember(caller.getId(), adminRole.getId());
        when(currentUser.id()).thenReturn(caller.getId());
    }

    /** 成员列表应通过店铺权限校验，并从真实 Mapper 返回用户和角色信息。 */
    @Test
    void memberListConnectsControllerServiceAndMapper() {
        SysUser target = insertUser("target");
        insertMember(target.getId(), adminRole.getId());

        var result = controller.list(shop.getId(), target.getUsername(),
                adminRole.getId(), ActiveStatus.ACTIVE, 1, 20).data();

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().user().username()).isEqualTo(target.getUsername());
        assertThat(result.items().getFirst().role().roleCode()).isEqualTo("SHOP_ADMIN");
    }

    /** 新增成员应按用户名找到 ACTIVE 用户，并写入一个有效的 SHOP 角色。 */
    @Test
    void addMemberFlowIsConnected() {
        SysUser target = insertUser("new_member");
        SysRole operatorRole = shopRole("SHOP_PRODUCT_OPERATOR");

        var response = controller.add(shop.getId(),
                new AddShopMemberRequest(target.getUsername(), operatorRole.getId().toString()));
        assertThat(response.getBody()).isNotNull();
        var created = response.getBody().data();

        ShopUser saved = shopUserMapper.selectMemberForUpdate(shop.getId(), target.getId());
        assertThat(created.user().username()).isEqualTo(target.getUsername());
        assertThat(created.role().roleCode()).isEqualTo("SHOP_PRODUCT_OPERATOR");
        assertThat(saved.getStatus()).isEqualTo(ActiveStatus.ACTIVE);

        assertThatThrownBy(() -> controller.add(shop.getId(),
                new AddShopMemberRequest(target.getUsername(), operatorRole.getId().toString())))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("SHOP_MEMBER_ALREADY_EXISTS"));
    }

    /** 成员角色和状态可以修改，但最后一名成员管理员不能移除自己的管理能力。 */
    @Test
    void roleAndStatusChangesProtectLastShopAdmin() {
        SysRole productRole = shopRole("SHOP_PRODUCT_OPERATOR");
        SysRole orderRole = shopRole("SHOP_ORDER_OPERATOR");
        SysUser target = insertUser("operator");
        insertMember(target.getId(), productRole.getId());

        var changedRole = controller.changeRole(shop.getId(), target.getId(),
                new ChangeShopMemberRoleRequest(orderRole.getId().toString())).data();
        var disabled = controller.changeStatus(shop.getId(), target.getId(),
                new StatusRequest(ActiveStatus.DISABLED)).data();

        assertThat(changedRole.role().roleCode()).isEqualTo("SHOP_ORDER_OPERATOR");
        assertThat(disabled.status()).isEqualTo(ActiveStatus.DISABLED);

        long callerId = currentUser.id();
        assertThatThrownBy(() -> controller.changeStatus(shop.getId(), callerId,
                new StatusRequest(ActiveStatus.DISABLED)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("CANNOT_DISABLE_SELF_WITHOUT_OTHER_ADMIN"));
    }

    private Shop insertShop() {
        Shop value = new Shop();
        value.setShopNo("SHOP-IT-" + suffix());
        value.setShopName("店铺成员集成测试");
        value.setStatus(ShopStatus.ACTIVE);
        assertThat(shopMapper.insert(value)).isOne();
        return value;
    }

    private SysUser insertUser(String prefix) {
        SysUser value = new SysUser();
        value.setUsername(prefix + "_" + suffix().toLowerCase());
        value.setPasswordHash("integration-test-only");
        value.setNickname("测试用户");
        value.setStatus(UserStatus.ACTIVE);
        assertThat(userMapper.insert(value)).isOne();
        return value;
    }

    private ShopUser insertMember(long userId, long roleId) {
        ShopUser value = new ShopUser();
        value.setShopId(shop.getId());
        value.setUserId(userId);
        value.setRoleId(roleId);
        value.setRoleScope(ScopeType.SHOP);
        value.setStatus(ActiveStatus.ACTIVE);
        assertThat(shopUserMapper.insert(value)).isOne();
        return value;
    }

    private SysRole shopRole(String roleCode) {
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode)
                .eq(SysRole::getScopeType, ScopeType.SHOP)
                .eq(SysRole::getStatus, ActiveStatus.ACTIVE));
        assertThat(role).as("schema.sql 应初始化 " + roleCode + " 角色").isNotNull();
        return role;
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
