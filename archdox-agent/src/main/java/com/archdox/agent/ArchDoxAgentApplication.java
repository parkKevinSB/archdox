package com.archdox.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ArchDoxAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(ArchDoxAgentApplication.class, args);
    }
}
