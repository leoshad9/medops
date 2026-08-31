package com.medops.files.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ClinicalFileProperties.class)
public class ClinicalFilesConfiguration {
}
