package org.mate.mate10.common;

import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.entity.SysUser;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Slf4j
@Component
public class UserCache {
    private static final long TTL_MS = 30_000L;

    //user + 过期时间打包(允许user == null,用于缓存查不到)
    private record Entry(SysUser user, long expireAt){}

    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();

    //取用户,命中且没过期就返回,否则 loader 查库并回填
    public SysUser getOrLoad(Long userId,Supplier<SysUser> loader){
        if (userId == null){
            return null;
        }
        long now = System.currentTimeMillis();
        Entry e = cache.get(userId);
        if (e != null && e.expireAt() > now) {
            return e.user();
        }
        SysUser user = loader.get();
        cache.put(userId, new Entry(user, now + TTL_MS));
        return user;
    }
    //主动失效,改密码/重置密码/禁用/启用/该角色权限/删除后必须调用
    public void evict(Long userId) {
        if (userId == null) {
            return;
        }
        cache.remove(userId);
        log.info("[UserCache] 已失效 userId={}", userId);
    }
}
