package com.cheddarflow.signal.evaluation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableJms
public class SignalEvaluationApplication {
    public static void main(String[] args) {
        SpringApplication.run(SignalEvaluationApplication.class, args);
    }
}
