package org.mate.mate10.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry){
        registry.addMapping("/**")//允许所有路径
                .allowedOrigins(
                        "http://localhost:5173",
                        "http://192.168.198.1:5173",
                        "http://192.168.40.1:5173",
                       "http://192.168.43.185:5173"

                )//允许vite项目来源
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")//允许的方法
                .allowedHeaders("*")//允许所有请求头
                .allowCredentials(true)//可以带有凭证
                .maxAge(3600);//请求缓存时间
    }
}
