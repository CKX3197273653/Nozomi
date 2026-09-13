package org.mate.mate10;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;


@ConfigurationPropertiesScan("org.mate.mate10.config")
@MapperScan("org.mate.mate10.mapper")
@SpringBootApplication
public class Mate10Application {
    public static void main(String[] args) {
        SpringApplication.run(Mate10Application.class, args);
    }

}
