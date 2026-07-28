package org.dhu.shiguang_market.common.api;

import java.time.OffsetDateTime;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;

public final class CommonViews {
    private CommonViews() {
    }

    public record UserSummary(String id, String username, String nickname, String avatarUrl, UserStatus status) {
    }

    public record ShopSummary(String id, String shopNo, String shopName, String logoUrl, ShopStatus status) {
    }

    public record AddressSnapshot(
            String recipientName, String recipientPhone, String provinceName, String cityName,
            String districtName, String detailAddress) {
    }

    public record AddressView(
            String id, String recipientName, String recipientPhone, String provinceName, String cityName,
            String districtName, String detailAddress, boolean isDefault,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }
}
