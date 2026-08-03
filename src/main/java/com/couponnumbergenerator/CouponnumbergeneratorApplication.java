package com.couponnumbergenerator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CouponnumbergeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(CouponnumbergeneratorApplication.class, args);
    }
}
