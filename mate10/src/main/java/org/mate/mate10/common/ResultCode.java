package org.mate.mate10.common;

public final class ResultCode {
    private ResultCode() {}
    public static final int OK           = 200;
    public static final int BAD_REQUEST  = 400;   // 参数问题
    public static final int UNAUTHORIZED = 401;   // 未登录 / 凭证错
    public static final int FORBIDDEN    = 403;   // 没权限 / 不允许对自己做
    public static final int NOT_FOUND    = 404;   // 资源不存在
    public static final int CONFLICT     = 409;   // 冲突:重名 / 状态不对
    public static final int ERROR        = 500;   // 服务器内部
}
