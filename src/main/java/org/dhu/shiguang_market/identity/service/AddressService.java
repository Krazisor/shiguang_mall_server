package org.dhu.shiguang_market.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.dhu.shiguang_market.common.api.CommonViews.AddressView;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.identity.dto.IdentityDtos.AddressUpsertRequest;
import org.dhu.shiguang_market.identity.mapper.UserAddressMapper;
import org.dhu.shiguang_market.identity.model.UserAddress;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddressService {
    private final UserAddressMapper addressMapper;
    private final CurrentUserService currentUser;

    public AddressService(UserAddressMapper addressMapper, CurrentUserService currentUser) {
        this.addressMapper = addressMapper;
        this.currentUser = currentUser;
    }

    public List<AddressView> list() {
        long userId = currentUser.id();
        return addressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                        .eq(UserAddress::getUserId, userId)
                        .orderByDesc(UserAddress::getIsDefault)
                        .orderByDesc(UserAddress::getUpdatedAt)
                        .orderByDesc(UserAddress::getId))
                .stream().map(IdentityViewMapper::address).toList();
    }

    @Transactional
    public AddressView create(AddressUpsertRequest request) {
        long userId = currentUser.id();
        boolean first = !addressMapper.exists(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId));
        boolean makeDefault = first || Boolean.TRUE.equals(request.isDefault());
        if (makeDefault) {
            clearDefault(userId);
        }
        UserAddress address = new UserAddress();
        address.setUserId(userId);
        apply(address, request);
        address.setIsDefault(makeDefault);
        addressMapper.insert(address);
        return IdentityViewMapper.address(addressMapper.selectById(address.getId()));
    }

    @Transactional
    public AddressView update(long addressId, AddressUpsertRequest request) {
        long userId = currentUser.id();
        UserAddress address = owned(addressId, userId);
        if (Boolean.TRUE.equals(request.isDefault())) {
            clearDefault(userId);
        }
        apply(address, request);
        address.setIsDefault(Boolean.TRUE.equals(request.isDefault()));
        addressMapper.updateById(address);
        return IdentityViewMapper.address(addressMapper.selectById(addressId));
    }

    @Transactional
    public void delete(long addressId) {
        long userId = currentUser.id();
        UserAddress address = owned(addressId, userId);
        addressMapper.deleteById(addressId);
    }

    @Transactional
    public AddressView makeDefault(long addressId) {
        long userId = currentUser.id();
        UserAddress address = owned(addressId, userId);
        clearDefault(userId);
        address.setIsDefault(true);
        addressMapper.updateById(address);
        return IdentityViewMapper.address(addressMapper.selectById(addressId));
    }

    public UserAddress ownedEntity(Long addressId, long userId) {
        if (addressId == null) {
            return addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                    .eq(UserAddress::getUserId, userId)
                    .eq(UserAddress::getIsDefault, true));
        }
        return owned(addressId, userId);
    }

    private UserAddress owned(long addressId, long userId) {
        UserAddress address = addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getId, addressId).eq(UserAddress::getUserId, userId));
        if (address == null) {
            throw BusinessException.notFound("RESOURCE_NOT_FOUND", "地址不存在");
        }
        return address;
    }

    private void clearDefault(long userId) {
        UserAddress patch = new UserAddress();
        patch.setIsDefault(false);
        addressMapper.update(patch, new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId).eq(UserAddress::getIsDefault, true));
    }

    private void apply(UserAddress address, AddressUpsertRequest request) {
        address.setRecipientName(request.recipientName().trim());
        address.setRecipientPhone(request.recipientPhone().trim());
        address.setProvinceName(request.provinceName().trim());
        address.setCityName(request.cityName().trim());
        address.setDistrictName(request.districtName().trim());
        address.setDetailAddress(request.detailAddress().trim());
    }
}
