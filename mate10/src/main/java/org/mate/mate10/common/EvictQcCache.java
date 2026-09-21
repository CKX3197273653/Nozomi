package org.mate.mate10.common;

import org.springframework.cache.annotation.CacheEvict;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * QC 缓存失效的组合注解。
 *
 * <p>等价于
 * {@code @CacheEvict(cacheNames = {"qcStats", "qcDetail"}, allEntries = true)}，
 * 用于标记所有会改变 QC 数据的写操作。</p>
 *
 * <p><b>为什么用组合注解</b>：三个 Service 中共有 15 个写方法需要失效缓存，
 * 逐个书写完整注解既重复又容易漏；封装后每个方法只需一行，语义也更明确。</p>
 *
 * <p><b>原理</b>：Spring 的缓存抽象会沿注解链查找元注解，
 * 因此标在本注解上的 {@code @CacheEvict} 依然生效。</p>
 *
 * <p><b>注意</b>：该注解只在<b>外部调用</b>时生效（Spring AOP 代理机制）。
 * 若某个写方法被同类内部方法调用（如 {@code createFullReport} 内部调用
 * {@code this.createReport()}），则必须给<b>入口方法</b>也加上本注解。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@CacheEvict(cacheNames = {"qcStats", "qcDetail"}, allEntries = true)
public @interface EvictQcCache {
}
