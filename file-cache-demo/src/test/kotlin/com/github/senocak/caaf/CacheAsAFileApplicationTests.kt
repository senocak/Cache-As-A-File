package com.github.senocak.caaf

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    properties = [
        "caaf.cache.directory=\${java.io.tmpdir}/caaf-test-cache",
        "caaf.cache.clear-interval=0ms"
    ]
)
class CacheAsAFileApplicationTests {
    @Test
    fun contextLoads() {
    }
}
