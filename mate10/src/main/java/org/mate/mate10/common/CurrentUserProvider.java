package org.mate.mate10.common;

import lombok.RequiredArgsConstructor;
import org.mate.mate10.entity.SysUser;
import org.mate.mate10.mapper.SysUserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentUserProvider {
    private final SysUserMapper sysUserMapper;

    public Long userIdOrNull(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        String username = auth.getName();
        if (username == null || username.isBlank() || "anonymousUser".equals(username)) {
            return null;
        }
        SysUser user = sysUserMapper.selectByUsernameOrEmail(username);
        return user == null ? null : user.getId();
    }


    public boolean isAdmin(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

}
