package org.mate.mate10.common;

import org.springframework.util.StringUtils;

public final class PasswordPolicy {
    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 16;

    private PasswordPolicy() {
    }
    public enum PasswordError {
        BLANK,
        LENGTH,
        SPACE_EDGE,
        SPACE_INNER
    }

    public static PasswordError  check(String password){
        if (!StringUtils.hasText(password)) {
            return PasswordError.BLANK;
        }
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return PasswordError.LENGTH;
        }
        if (!password.equals(password.trim())) {
            return PasswordError.SPACE_EDGE;
        }
        if (password.contains(" ")) {
            return PasswordError.SPACE_INNER;
        }
        return null;
    }

    public static String message(PasswordError error){
        if (error == null) {
            return null;
        }
        return switch (error){
            case BLANK       -> "密码不能为空";
            case LENGTH      -> "密码长度需在 " + MIN_LENGTH + "~" + MAX_LENGTH + " 个字符之间";
            case SPACE_EDGE  -> "密码首尾不能有空格";
            case SPACE_INNER -> "密码不能包含空格";
        };
    }

    public static String validate(String password) {
        return message(check(password));
    }

}
