package com.questionforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class QuestionForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuestionForgeApplication.class, args);
    }
}
