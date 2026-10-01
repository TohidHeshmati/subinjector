package com.subinjector

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HelloController {
    @GetMapping("/api/hello")
    fun hello(): HelloResponse = HelloResponse(message = "Hello, world!")
}

data class HelloResponse(val message: String)
