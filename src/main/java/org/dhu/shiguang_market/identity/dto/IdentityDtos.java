package org.dhu.shiguang_market.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonSetter;
import org.dhu.shiguang_market.common.api.CommonViews.ShopSummary;
import org.dhu.shiguang_market.common.api.CommonViews.UserSummary;

public final class IdentityDtos {
    private IdentityDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{3,63}$") String username,
            @NotBlank @Size(min = 8, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).+$") String password,
            @NotBlank @Size(max = 64) String nickname,
            @Size(min = 6, max = 32) String phone,
            @Email @Size(max = 128) String email) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public static final class UpdateProfileRequest {
        private String nickname;
        private String phone;
        private String email;
        private String avatarUrl;
        private boolean nicknamePresent;
        private boolean phonePresent;
        private boolean emailPresent;
        private boolean avatarUrlPresent;

        @JsonSetter("nickname")
        public void setNickname(String nickname) { this.nickname = nickname; this.nicknamePresent = true; }
        @JsonSetter("phone")
        public void setPhone(String phone) { this.phone = phone; this.phonePresent = true; }
        @JsonSetter("email")
        public void setEmail(String email) { this.email = email; this.emailPresent = true; }
        @JsonSetter("avatarUrl")
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; this.avatarUrlPresent = true; }

        public String nickname() { return nickname; }
        public String phone() { return phone; }
        public String email() { return email; }
        public String avatarUrl() { return avatarUrl; }
        public boolean hasNickname() { return nicknamePresent; }
        public boolean hasPhone() { return phonePresent; }
        public boolean hasEmail() { return emailPresent; }
        public boolean hasAvatarUrl() { return avatarUrlPresent; }
    }

    public record LoginView(
            String tokenName, String tokenValue, long expiresInSeconds,
            long activeTimeoutSeconds, UserSummary user) {
    }

    public record ShopContextView(ShopSummary shop, String roleCode, List<String> permissions) {
    }

    public record CurrentUserView(
            UserSummary user, String phone, String email, List<String> platformRoles,
            List<String> platformPermissions, List<ShopContextView> shops) {
    }

    public record AddressUpsertRequest(
            @NotBlank @Size(max = 64) String recipientName,
            @NotBlank @Size(min = 6, max = 32) String recipientPhone,
            @NotBlank @Size(max = 64) String provinceName,
            @NotBlank @Size(max = 64) String cityName,
            @NotBlank @Size(max = 64) String districtName,
            @NotBlank @Size(max = 255) String detailAddress,
            @NotNull Boolean isDefault) {
    }
}
