package dev.learningops.snapshot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class NightlySnapshotApplication {
    public static void main(String[] args) {
        SpringApplication.run(NightlySnapshotApplication.class, args);
    }
}
