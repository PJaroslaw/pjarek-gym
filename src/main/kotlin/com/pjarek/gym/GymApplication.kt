package com.pjarek.gym

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class GymApplication

fun main(args: Array<String>) {
    runApplication<GymApplication>(*args)
}
