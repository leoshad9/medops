package com.medops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MedopsApiApplication {

    private MedopsApiApplication() {
        // Utility class — not instantiated directly.
    }

    public static void main(final String[] args) {
        SpringApplication.run(MedopsApiApplication.class, args);
    }

}
