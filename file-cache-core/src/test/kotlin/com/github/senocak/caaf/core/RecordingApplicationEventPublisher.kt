package com.github.senocak.caaf.core

import org.springframework.context.ApplicationEventPublisher

class RecordingApplicationEventPublisher : ApplicationEventPublisher {
    val events: MutableList<Any> = mutableListOf()

    override fun publishEvent(event: Any) {
        events.add(element = event)
    }
}
