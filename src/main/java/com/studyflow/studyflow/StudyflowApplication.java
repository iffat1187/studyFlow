package com.studyflow.studyflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StudyflowApplication {

    public static void main(String[] args) {

        SpringApplication.run(
                StudyflowApplication.class,
                args
        );
    }

}