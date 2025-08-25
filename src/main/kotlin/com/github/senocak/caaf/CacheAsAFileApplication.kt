package com.github.senocak.caaf

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CacheAsAFileApplication

fun main(args: Array<String>) {
    runApplication<CacheAsAFileApplication>(*args)
}
