package org.mate.mate10.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mate.mate10.common.PasswordPolicy.PasswordError;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mate.mate10.common.PasswordPolicy.message;

@DisplayName("PasswordPolicy —— 密码规则(8~16 位,不含空格)")
class PasswordPolicyTest {


    @Test
    @DisplayName("合规密码 → null")
    void valid() {
        assertNull(PasswordPolicy.check("12345678"));
        assertNull(PasswordPolicy.check("Abc@1234"));
        assertNull(PasswordPolicy.check("a".repeat(16)));
    }

    @Test
    @DisplayName("null / 空串 / 全空白 → BLANK")
    void blank() {
        assertEquals(PasswordError.BLANK, PasswordPolicy.check(null));
        assertEquals(PasswordError.BLANK, PasswordPolicy.check(""));
        assertEquals(PasswordError.BLANK, PasswordPolicy.check("   "));
    }

    @Test
    @DisplayName("7 位 / 17 位 → LENGTH")
    void length() {
        assertEquals(PasswordError.LENGTH, PasswordPolicy.check("1234567"));
        assertEquals(PasswordError.LENGTH, PasswordPolicy.check("a".repeat(17)));
    }

    @Test
    @DisplayName("中间有空格(9 位)→ SPACE_INNER")
    void innerSpace() {
        assertEquals(PasswordError.SPACE_INNER, PasswordPolicy.check("1234 5678"));
    }

    @Test
    @DisplayName("首尾有空格(9 位)→ SPACE_EDGE")
    void trimSpace() {
        assertEquals(PasswordError.SPACE_EDGE, PasswordPolicy.check(" 12345678"));
        assertEquals(PasswordError.SPACE_EDGE, PasswordPolicy.check("12345678 "));
    }


    @Test
    @DisplayName("文案锁:用户看到的提示(改这里 = 改产品文案)")
    void messageContract() {
        assertEquals("密码不能为空", message(PasswordError.BLANK));
        assertEquals("密码长度需在 8~16 个字符之间", message(PasswordError.LENGTH));
        assertEquals("密码首尾不能有空格", message(PasswordError.SPACE_EDGE));
        assertEquals("密码不能包含空格", message(PasswordError.SPACE_INNER));
        assertNull(message(null));
    }
}