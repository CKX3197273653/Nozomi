package org.mate.mate10.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    //登录用户名
    private String username;
    private String password;

   //员工号
    private String empNo;

    private String nickname;

    private String phone;

    private String email;
}
