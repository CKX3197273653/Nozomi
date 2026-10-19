package org.mate.mate10.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.entity.SysUser;
import org.mate.mate10.mapper.SysUserMapper;
import org.mate.mate10.util.JwtUtil;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    //本地缓存30S
    private static final long USER_CACHE_TTL_MS = 30_000L;
    private final JwtUtil jwtUtil;
    private final SysUserMapper sysUserMapper;
    //过期时间戳
    private final Map<Long, Object[]> userCache = new ConcurrentHashMap<>();

    public JwtAuthenticationFilter(JwtUtil jwtUtil, SysUserMapper sysUserMapper) {
        this.jwtUtil = jwtUtil;
        this.sysUserMapper = sysUserMapper;
    }

    private SysUser loadUserById(Long userId) {
        long now = System.currentTimeMillis();
        Object[] cache = userCache.get(userId);
        if (cache != null && (Long) cache[1] > now) {
            return (SysUser) cache[0];
        }
        SysUser user = sysUserMapper.selectById(userId);
        userCache.put(userId, new Object[]{user, now + USER_CACHE_TTL_MS});
        return user;
    }
    private SysUser loadUserByUsername(String username) {
        return sysUserMapper.selectByUsernameOrEmail(username);
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // 从请求头提取 Authorization
        String header = request.getHeader("Authorization");

        // 格式必须是 "Bearer xxx"
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7); // 去掉 "Bearer " 前缀

            try {
                // 解析并验证 token
                Claims claims = jwtUtil.parseToken(token);

                String username = claims.get("username", String.class);
                Object uid = claims.get("userId");
                Long tokenUserId = (uid instanceof Number n) ? n.longValue() : null;
                SysUser user = (tokenUserId != null)
                        ? loadUserById(tokenUserId)
                        : (username != null ? loadUserByUsername(username) : null);
                //用户被删 / 被禁用 -> 不认证,交给 SecurityConfig 返回401
                if (user == null || user.getStatus() == null || user.getStatus() != 1) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                //改密码 / 重置 / 禁用后库里的 version 会 +1,  老token的ver 对比上 -> 立即失效
                Object verObj = claims.get("ver");
                Integer tokenVer = (verObj instanceof Number n) ? n.intValue() : null;
                int dbVer = (user.getTokenVersion() == null ? 0 : user.getTokenVersion());
                if (tokenVer == null || tokenVer != dbVer) {
                    log.info("[JWT] token失效 userId={} tokenVer={} dbVer={}",
                            user.getId(), tokenVer, dbVer);
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                //角色用数据库中的,不用token的,降级会直接生效,老token里的ADMIN不会再生效
                Integer dbRole = user.getUserRole();
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority(
                        (dbRole != null && dbRole == 1) ? "ROLE_ADMIN" : "ROLE_USER"));
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user.getId(), null, authorities);
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

//                    放入安全上下文 → 后续授权规则就能识别"已登录"
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // token 无效或过期：不设置认证信息，让后面的授权规则拒绝
                SecurityContextHolder.clearContext();
                log.warn("JWT 解析失败: {}", e.getMessage());
            }
        }
        // 继续过滤器链
        filterChain.doFilter(request, response);
    }
}
