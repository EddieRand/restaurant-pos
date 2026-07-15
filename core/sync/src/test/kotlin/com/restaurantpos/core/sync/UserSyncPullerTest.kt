package com.restaurantpos.core.sync

import com.restaurantpos.core.domain.repository.UserRepository
import com.restaurantpos.core.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * F-023 user pull-sync unit tests (JVM). Without this puller, PIN-login terminals
 * that don't run the on-device seeder (handheld) start with an empty users table
 * and can never authenticate.
 */
class UserSyncPullerTest {

    private lateinit var port: FakeUserPullPort
    private lateinit var repo: FakeUserRepo
    private lateinit var puller: UserSyncPuller

    @Before
    fun setup() {
        port = FakeUserPullPort()
        repo = FakeUserRepo()
        puller = UserSyncPuller(port = port, userRepository = repo, network = FakeNetworkMonitor(initialOnline = true))
    }

    private fun user(id: String, pin: String) =
        User(id = id, displayName = id, roleId = "cashier", pinHash = pin, isActive = true, createdAt = 0L)

    @Test
    fun `successful pull upserts every user`() = runBlocking {
        port.result = UserPullResult(serverTime = 1_000L, users = listOf(user("u1", "h1"), user("u2", "h2")))

        puller.pull()

        assertEquals(2, repo.saved.size)
        assertEquals(listOf("u1", "u2"), repo.saved.map { it.id })
        assertEquals("h1", repo.getByPin("h1")?.id?.let { "h1" })
    }

    @Test
    fun `null result does nothing`() = runBlocking {
        port.result = null
        puller.pull()
        assertTrue(repo.saved.isEmpty())
    }

    @Test
    fun `network failure is swallowed`() = runBlocking {
        port.shouldThrow = true
        puller.pull()
        assertTrue(repo.saved.isEmpty())
    }

    @Test
    fun `re-pull upserts existing user (idempotent, updates pinHash)`() = runBlocking {
        port.result = UserPullResult(serverTime = 1L, users = listOf(user("u1", "old")))
        puller.pull()
        port.result = UserPullResult(serverTime = 2L, users = listOf(user("u1", "new")))
        puller.pull()

        assertEquals("new", repo.getById("u1")?.pinHash)
    }
}

class FakeUserPullPort : UserPullPort {
    var result: UserPullResult? = null
    var shouldThrow: Boolean = false

    override suspend fun pullUsers(): UserPullResult? {
        if (shouldThrow) throw RuntimeException("Simulated network error")
        return result
    }
}

class FakeUserRepo : UserRepository {
    val store = mutableMapOf<String, User>()
    val saved = mutableListOf<User>()

    override fun observeActive(): Flow<List<User>> = flowOf(store.values.filter { it.isActive })
    override suspend fun getById(id: String): User? = store[id]
    override suspend fun getByPin(pinHash: String): User? = store.values.firstOrNull { it.pinHash == pinHash }
    override suspend fun save(user: User) { store[user.id] = user; saved.add(user) }
    override suspend fun setActive(id: String, isActive: Boolean) {
        store[id]?.let { store[id] = it.copy(isActive = isActive) }
    }
}
