package com.project.sns.follow.application

import com.project.sns.follow.domain.FollowCounts
import com.project.sns.follow.domain.FollowRepository
import com.project.sns.follow.domain.SelfFollowNotAllowedException
import com.project.sns.timeline.application.TimelineFollowListener
import com.project.sns.user.application.UserService
import com.project.sns.user.domain.User
import com.project.sns.user.domain.UserNotFoundException
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FollowServiceTests {
    private val userService = mock(UserService::class.java)
    private val followRepository = mock(FollowRepository::class.java)
    private val timelineFollowListener = mock(TimelineFollowListener::class.java)
    private val followService = FollowService(userService, followRepository, timelineFollowListener)

    @Test
    fun `대상 사용자를 확인하고 팔로우 관계를 생성한다`() {
        doReturn(user(TARGET_ID, TARGET_EMAIL)).`when`(userService).getById(TARGET_ID)
        doReturn(true).`when`(followRepository).create(ACTOR_ID, TARGET_ID)

        val result = followService.follow(ACTOR_ID, TARGET_ID)

        assertTrue(result.changed)
        verify(followRepository).increaseCounts(ACTOR_ID, TARGET_ID)
    }

    @Test
    fun `이미 존재하는 팔로우 관계는 변경 없이 성공한다`() {
        doReturn(user(TARGET_ID, TARGET_EMAIL)).`when`(userService).getById(TARGET_ID)
        doReturn(false).`when`(followRepository).create(ACTOR_ID, TARGET_ID)

        val result = followService.follow(ACTOR_ID, TARGET_ID)

        assertFalse(result.changed)
        verify(followRepository, never()).increaseCounts(ACTOR_ID, TARGET_ID)
    }

    @Test
    fun `자기 자신은 팔로우할 수 없다`() {

        assertFailsWith<SelfFollowNotAllowedException> {
            followService.follow(ACTOR_ID, ACTOR_ID)
        }
        verifyNoInteractions(followRepository)
    }

    @Test
    fun `존재하지 않는 대상은 팔로우할 수 없다`() {
        doThrow(UserNotFoundException()).`when`(userService).getById(TARGET_ID)

        assertFailsWith<UserNotFoundException> {
            followService.follow(ACTOR_ID, TARGET_ID)
        }
        verifyNoInteractions(followRepository)
    }

    @Test
    fun `자기 자신은 언팔로우할 수 없다`() {

        assertFailsWith<SelfFollowNotAllowedException> {
            followService.unfollow(ACTOR_ID, ACTOR_ID)
        }
        verifyNoInteractions(followRepository)
    }

    @Test
    fun `존재하지 않는 대상은 언팔로우할 수 없다`() {
        doThrow(UserNotFoundException()).`when`(userService).getById(TARGET_ID)

        assertFailsWith<UserNotFoundException> {
            followService.unfollow(ACTOR_ID, TARGET_ID)
        }
        verifyNoInteractions(followRepository)
    }

    @Test
    fun `언팔로우는 관계를 소프트 삭제한다`() {
        doReturn(user(TARGET_ID, TARGET_EMAIL)).`when`(userService).getById(TARGET_ID)
        doReturn(true).`when`(followRepository).softDelete(ACTOR_ID, TARGET_ID)

        val result = followService.unfollow(ACTOR_ID, TARGET_ID)

        assertTrue(result.changed)
        verify(followRepository).softDelete(ACTOR_ID, TARGET_ID)
        verify(followRepository).decreaseCounts(ACTOR_ID, TARGET_ID)
    }

    @Test
    fun `이미 언팔로우된 관계는 카운터를 내리지 않는다`() {
        doReturn(user(TARGET_ID, TARGET_EMAIL)).`when`(userService).getById(TARGET_ID)
        doReturn(false).`when`(followRepository).softDelete(ACTOR_ID, TARGET_ID)

        val result = followService.unfollow(ACTOR_ID, TARGET_ID)

        assertFalse(result.changed)
        verify(followRepository, never()).decreaseCounts(ACTOR_ID, TARGET_ID)
    }

    @Test
    fun `팔로워 수와 팔로잉 수를 각각 조회한다`() {
        doReturn(user(TARGET_ID, TARGET_EMAIL)).`when`(userService).getById(TARGET_ID)
        doReturn(FollowCounts(userId = TARGET_ID, followerCount = 3L, followingCount = 5L)).`when`(followRepository)
            .getCounts(TARGET_ID)

        val stats = followService.getStats(TARGET_ID)

        assertEquals(3L, stats.followerCount)
        assertEquals(5L, stats.followingCount)
    }

    @Test
    fun `존재하지 않는 사용자의 통계는 조회할 수 없다`() {
        doThrow(UserNotFoundException()).`when`(userService).getById(TARGET_ID)

        assertFailsWith<UserNotFoundException> {
            followService.getStats(TARGET_ID)
        }
        verifyNoInteractions(followRepository)
    }

    private fun user(id: Long, email: String) = User(
        id = id,
        email = email,
        passwordHash = "encoded",
        nickname = "테스터",
    )

    private companion object {
        const val ACTOR_ID = 1L
        const val TARGET_ID = 2L
        const val TARGET_EMAIL = "target@example.com"
    }
}
