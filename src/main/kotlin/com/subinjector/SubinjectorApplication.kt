package com.subinjector

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SubinjectorApplication

fun main(args: Array<String>) {
    runApplication<SubinjectorApplication>(*args)
}
