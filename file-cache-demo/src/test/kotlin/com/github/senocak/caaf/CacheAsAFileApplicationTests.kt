package com.github.senocak.caaf

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    properties = [
        "caaf.cache.directory=\${java.io.tmpdir}/caaf-test-cache"
    ]
)
class CacheAsAFileApplicationTests {
    @Test
    fun contextLoads() {
    }
}
