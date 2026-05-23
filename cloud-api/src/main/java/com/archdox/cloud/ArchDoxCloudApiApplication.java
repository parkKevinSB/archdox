package com.archdox.cloud;

import io.github.parkkevinsb.bloom.spring.EnableBloom;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableBloom
public class ArchDoxCloudApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ArchDoxCloudApiApplication.class, args);
    }
}
