package org.dhu.shiguang_market.common.security;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {
    private static final int COST = 12;

    public String hash(String password) {
        return BCrypt.withDefaults().hashToString(COST, password.toCharArray());
    }

    public boolean matches(String password, String digest) {
        return digest != null && BCrypt.verifyer().verify(password.toCharArray(), digest).verified;
    }
}
