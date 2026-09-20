package com.project.sns.timeline.application

import com.project.sns.timeline.domain.TimelinePolicy
import com.project.sns.timeline.domain.FanoutMessage
import com.project.sns.timeline.domain.FanoutQueue
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("!test")
class TimelineFanoutConsumer(
    private val fanoutQueue: FanoutQueue,
    private val fanoutService: TimelineFanoutService,
) : SmartLifecycle {
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null
    private val consumerName = "${runCatching { InetAddress.getLocalHost().hostName }.getOrElse { "unknown" }}-${ProcessHandle.current().pid()}"

    override fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = Thread(::loop, "timeline-fanout").apply { isDaemon = true; start() }
    }

    override fun stop() {
        running.set(false)
        thread?.join(TimelinePolicy.Fanout.POLL_BLOCK.toMillis() + 1_000)
    }

    override fun isRunning(): Boolean = running.get()

    private fun loop() {
        while (running.get()) {
            try {
                fanoutQueue.claimStale(
                    consumerName,
                    TimelinePolicy.Fanout.CLAIM_MIN_IDLE,
                    TimelinePolicy.Fanout.POLL_COUNT
                ).forEach(::handle)
                fanoutQueue.poll(consumerName, TimelinePolicy.Fanout.POLL_COUNT, TimelinePolicy.Fanout.POLL_BLOCK)
                    .forEach(::handle)
            } catch (e: RuntimeException) {
                if (!running.get()) return
                logger.warn("팬아웃 소비 루프 오류 — {}ms 뒤 재시도", TimelinePolicy.Fanout.POLL_BLOCK.toMillis(), e)
                Thread.sleep(TimelinePolicy.Fanout.POLL_BLOCK.toMillis())
            }
        }
    }

    private fun handle(message: FanoutMessage) {
        try {
            fanoutService.fanout(message.event)
            fanoutQueue.ack(message.id)
        } catch (e: RuntimeException) {
            if (message.deliveryCount >= TimelinePolicy.Fanout.MAX_DELIVERIES) {
                logger.error("팬아웃 {}회 실패로 폐기 — 재구축 때 복구된다: {}", message.deliveryCount, message.event, e)
                fanoutQueue.ack(message.id)
            } else {
                logger.warn("팬아웃 실패({}회) — 재시도 대기: {}", message.deliveryCount, message.event, e)
            }
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(TimelineFanoutConsumer::class.java)
    }
}
