package org.mate.mate10.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.entity.SysUser;
import org.mate.mate10.mapper.SysUserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CurrentUserProvider {

    public Long userIdOrNull(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Long id) {
            return id;
        }
        log.warn("[CurrentUser] principal 不是 Long,而是 {}",
                principal == null ? "null" : principal.getClass().getName());
        return null;
    }

    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

}
