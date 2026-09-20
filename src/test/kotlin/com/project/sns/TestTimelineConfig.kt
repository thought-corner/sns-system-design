package com.project.sns

import com.project.sns.timeline.domain.FanoutEvent
import com.project.sns.timeline.domain.FanoutMessage
import com.project.sns.timeline.domain.FanoutQueue
import com.project.sns.timeline.domain.FollowCache
import com.project.sns.timeline.domain.PostCache
import com.project.sns.timeline.domain.PostSnapshot
import com.project.sns.timeline.domain.TimelineEntry
import com.project.sns.timeline.domain.TimelineSlice
import com.project.sns.timeline.domain.TimelineStore
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration(proxyBeanMethods = false)
class TestTimelineConfig {
    @Bean
    fun timelineStore(): InMemoryTimelineStore = InMemoryTimelineStore(homeSize = 800, authorSize = 200)

    @Bean
    fun fanoutQueue(): InMemoryFanoutQueue = InMemoryFanoutQueue()

    @Bean
    fun followCache(): InMemoryFollowCache = InMemoryFollowCache()

    @Bean
    fun postCache(): InMemoryPostCache = InMemoryPostCache()
}

class InMemoryTimelineStore(
    private val homeSize: Int,
    private val authorSize: Int,
) : TimelineStore {
    private val home = ConcurrentHashMap<Long, MutableMap<Long, Long>>()
    private val author = ConcurrentHashMap<Long, MutableMap<Long, Long>>()
    private val rebuilding: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun clear() {
        home.clear()
        author.clear()
        rebuilding.clear()
    }

    fun homeIds(userId: Long): List<Long>? =
        home[userId]?.let { m -> synchronized(m) { m.entries.sortedByDescending { it.value }.map { it.key } } }

    override fun pushHome(userIds: Collection<Long>, entry: TimelineEntry) =
        userIds.forEach { add(home, it, entry, homeSize) }

    override fun pushAuthor(authorId: Long, entry: TimelineEntry) = add(author, authorId, entry, authorSize)

    override fun removeHome(userId: Long, postIds: Collection<Long>) {
        home[userId]?.let { m -> synchronized(m) { postIds.forEach { m.remove(it) } } }
    }

    override fun readHome(userId: Long, beforeScore: Long?, limit: Int): TimelineSlice =
        read(home, userId, beforeScore, limit, "home:$userId")

    override fun readAuthor(authorId: Long, beforeScore: Long?, limit: Int): TimelineSlice =
        read(author, authorId, beforeScore, limit, "author:$authorId")

    override fun beginRebuildHome(userId: Long) {
        home.computeIfAbsent(userId) { mutableMapOf() }
        rebuilding.add("home:$userId")
    }

    override fun rebuildHome(userId: Long, entries: List<TimelineEntry>) = rebuild(home, userId, entries, homeSize, "home:$userId")

    override fun beginRebuildAuthor(authorId: Long) {
        author.computeIfAbsent(authorId) { mutableMapOf() }
        rebuilding.add("author:$authorId")
    }

    override fun rebuildAuthor(authorId: Long, entries: List<TimelineEntry>) = rebuild(author, authorId, entries, authorSize, "author:$authorId")

    private fun rebuild(map: ConcurrentHashMap<Long, MutableMap<Long, Long>>, key: Long, entries: List<TimelineEntry>, size: Int, tag: String) {
        val m = map.computeIfAbsent(key) { mutableMapOf() }
        synchronized(m) {
            entries.forEach { m[it.postId] = it.score }
            while (m.size > size) m.remove(m.minByOrNull { it.value }!!.key)
        }
        rebuilding.remove(tag)
        if (m.isEmpty()) map.remove(key)
    }

    private fun add(map: ConcurrentHashMap<Long, MutableMap<Long, Long>>, key: Long, entry: TimelineEntry, size: Int) {
        val m = map[key] ?: return
        synchronized(m) {
            m[entry.postId] = entry.score
            while (m.size > size) m.remove(m.minByOrNull { it.value }!!.key)
        }
    }

    private fun read(
        map: ConcurrentHashMap<Long, MutableMap<Long, Long>>,
        key: Long,
        beforeScore: Long?,
        limit: Int,
        tag: String,
    ): TimelineSlice {
        if (tag in rebuilding) return TimelineSlice(exists = false, entries = emptyList())
        val m = map[key] ?: return TimelineSlice(exists = false, entries = emptyList())
        if (synchronized(m) { m.isEmpty() }) return TimelineSlice(exists = false, entries = emptyList())
        val entries = synchronized(m) {
            m.entries.filter { beforeScore == null || it.value < beforeScore }.sortedByDescending { it.value }
                .take(limit).map { TimelineEntry(it.key, it.value) }
        }
        return TimelineSlice(exists = true, entries = entries)
    }
}

class InMemoryFanoutQueue : FanoutQueue {
    private val pending = ConcurrentLinkedQueue<FanoutMessage>()
    private val unacked = ConcurrentHashMap<String, FanoutMessage>()
    private val sequence = AtomicLong()

    fun clear() {
        pending.clear()
        unacked.clear()
    }

    fun unackedCount(): Int = unacked.size

    override fun enqueue(event: FanoutEvent) {
        pending.add(FanoutMessage(id = sequence.incrementAndGet().toString(), event = event, deliveryCount = 1))
    }

    override fun poll(consumer: String, count: Int, block: Duration): List<FanoutMessage> {
        val taken = generateSequence { pending.poll() }.take(count).toList()
        taken.forEach { unacked[it.id] = it }
        return taken
    }

    override fun claimStale(consumer: String, minIdle: Duration, count: Int): List<FanoutMessage> = emptyList()

    override fun ack(messageId: String) {
        unacked.remove(messageId)
    }
}

class InMemoryFollowCache : FollowCache {
    private val following = ConcurrentHashMap<Long, MutableSet<Long>>()
    private val celebrities = ConcurrentHashMap<Long, Set<Long>>()

    fun clear() {
        following.clear()
        celebrities.clear()
    }

    fun hasFollowing(userId: Long): Boolean = following.containsKey(userId)

    override fun followingIds(userId: Long): Set<Long>? = following[userId]?.toSet()

    override fun putFollowingIds(userId: Long, ids: Collection<Long>) {
        following[userId] = ConcurrentHashMap.newKeySet<Long>().also { it.addAll(ids) }
    }

    override fun evictFollowingIds(userId: Long) {
        following.remove(userId)
    }

    override fun celebrityIds(userId: Long): Set<Long>? = celebrities[userId]

    override fun putCelebrityIds(userId: Long, ids: Collection<Long>) {
        celebrities[userId] = ids.toSet()
    }

    override fun evictCelebrityIds(userId: Long) {
        celebrities.remove(userId)
    }
}

class InMemoryPostCache : PostCache {
    private val snapshots = ConcurrentHashMap<Long, PostSnapshot>()

    fun clear() = snapshots.clear()

    fun contains(id: Long): Boolean = snapshots.containsKey(id)

    override fun getAll(ids: Collection<Long>): Map<Long, PostSnapshot> =
        ids.mapNotNull { id -> snapshots[id]?.let { id to it } }.toMap()

    override fun putAll(snapshots: Collection<PostSnapshot>) = snapshots.forEach { this.snapshots[it.id] = it }

    override fun evict(id: Long) {
        snapshots.remove(id)
    }
}
