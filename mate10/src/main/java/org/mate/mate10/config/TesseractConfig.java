package org.mate.mate10.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "tesseract")
public class TesseractConfig {
    private String path;
    private String dataPath;
    private String language = "chi_sim";
}
