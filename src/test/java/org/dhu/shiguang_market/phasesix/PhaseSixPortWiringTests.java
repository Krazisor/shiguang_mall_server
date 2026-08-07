package org.dhu.shiguang_market.phasesix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.ActiveStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ScopeType;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.security.PasswordService;
import org.dhu.shiguang_market.common.util.ContentSafety;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.identity.dto.IdentityDtos.RegisterRequest;
import org.dhu.shiguang_market.identity.mapper.SysRoleMapper;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.identity.mapper.SysUserRoleMapper;
import org.dhu.shiguang_market.identity.model.SysRole;
import org.dhu.shiguang_market.identity.model.SysUser;
import org.dhu.shiguang_market.identity.service.IdentityService;
import org.dhu.shiguang_market.integration.order.ActiveShopBusinessPort;
import org.dhu.shiguang_market.integration.payment.WalletProvisionPort;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.ChangeShopStatusRequest;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.dhu.shiguang_market.shop.mapper.ShopUserMapper;
import org.dhu.shiguang_market.shop.model.Shop;
import org.dhu.shiguang_market.shop.service.PlatformShopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 验证 A 线用例已经通过阶段六端口调用 B 线能力，不再直接依赖 B 线 Mapper。 */
class PhaseSixPortWiringTests {
    @BeforeEach
    void initializeMybatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "phase-six-wiring-test");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
        TableInfoHelper.initTableInfo(assistant, SysRole.class);
        TableInfoHelper.initTableInfo(assistant, Shop.class);
    }

    /** 注册用户、分配角色后调用 WalletProvisionPort，使钱包加入同一注册事务。 */
    @Test
    void registrationUsesWalletProvisionPort() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        WalletProvisionPort walletPort = mock(WalletProvisionPort.class);
        PasswordService passwords = mock(PasswordService.class);
        SysRole customer = new SysRole();
        customer.setId(2L);
        customer.setRoleCode("CUSTOMER");
        customer.setScopeType(ScopeType.PLATFORM);
        customer.setStatus(ActiveStatus.ACTIVE);
        when(roleMapper.selectOne(any())).thenReturn(customer);
        when(passwords.hash("Password1")).thenReturn("hash");
        doAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(9L);
            return 1;
        }).when(userMapper).insert(any(SysUser.class));

        IdentityService service = new IdentityService(userMapper, roleMapper, userRoleMapper, walletPort,
                mock(ShopMapper.class), mock(ShopUserMapper.class), mock(CurrentUserService.class),
                passwords, mock(ContentSafety.class));

        service.register(new RegisterRequest("buyer01", "Password1", "买家", null, null));

        verify(walletPort).provision(9L);
    }

    /** 关闭店铺前通过 ActiveShopBusinessPort 检查；存在业务时必须阻止关闭。 */
    @Test
    void closingShopUsesActiveBusinessPort() {
        ShopMapper shopMapper = mock(ShopMapper.class);
        ActiveShopBusinessPort activeBusiness = mock(ActiveShopBusinessPort.class);
        Shop shop = new Shop();
        shop.setId(3L);
        shop.setStatus(ShopStatus.ACTIVE);
        when(shopMapper.selectOne(any())).thenReturn(shop);
        when(activeBusiness.hasActiveBusiness(3L)).thenReturn(true);

        PlatformShopService service = new PlatformShopService(shopMapper, mock(ShopUserMapper.class),
                mock(SysUserMapper.class), mock(SysRoleMapper.class), activeBusiness,
                mock(CurrentUserService.class), mock(NumberGenerator.class), mock(ContentSafety.class));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.changeStatus(3L, new ChangeShopStatusRequest(ShopStatus.CLOSED, "停止经营")));

        assertEquals("SHOP_HAS_ACTIVE_BUSINESS", exception.getCode());
        verify(activeBusiness).hasActiveBusiness(3L);
    }
}
