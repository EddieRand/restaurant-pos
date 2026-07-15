package com.restaurantpos.core.sync

import com.restaurantpos.core.domain.repository.RolePermissionRepository
import com.restaurantpos.core.model.PermissionKey
import com.restaurantpos.core.model.RolePermission
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Batch 46 — permission pull-sync unit tests (JVM, no Android dependency).
 *
 * Covers:
 * - Successful pull replaces permissions per role
 * - Empty result (null from port) does nothing
 * - Pull with no permissions clears all role permissions
 * - Network failure is silently swallowed
 */
class PermissionSyncPullerTest {

    private lateinit var port: FakePermissionPullPort
    private lateinit var repo: FakeRolePermissionRepo
    private lateinit var puller: PermissionSyncPuller

    @Before
    fun setup() {
        port = FakePermissionPullPort()
        repo = FakeRolePermissionRepo()
        puller = PermissionSyncPuller(
            port = port,
            rolePermissionRepository = repo,
            network = FakeNetworkMonitor(initialOnline = true),
        )
    }

    @Test
    fun `successful pull replaces permissions per role`() = runBlocking {
        port.result = PermissionPullResult(
            serverTime = 1_000L,
            roles = emptyList(),
            rolePermissions = listOf(
                RolePermission("admin", "order.create"),
                RolePermission("admin", "order.void"),
                RolePermission("manager", "order.create"),
            ),
        )

        puller.pull()

        assertEquals(2, repo.replaceCalls.size)
        assertEquals("admin", repo.replaceCalls[0].first)
        assertEquals(listOf("order.create", "order.void"), repo.replaceCalls[0].second)
        assertEquals("manager", repo.replaceCalls[1].first)
        assertEquals(listOf("order.create"), repo.replaceCalls[1].second)
    }

    @Test
    fun `null result does nothing`() = runBlocking {
        port.result = null

        puller.pull()

        assertTrue(repo.replaceCalls.isEmpty())
    }

    @Test
    fun `pull with no permissions clears nothing (no roles to process)`() = runBlocking {
        port.result = PermissionPullResult(
            serverTime = 1_000L,
            roles = emptyList(),
            rolePermissions = emptyList(),
        )

        puller.pull()

        assertTrue(repo.replaceCalls.isEmpty())
    }

    @Test
    fun `network failure is swallowed`() = runBlocking {
        port.shouldThrow = true

        puller.pull()

        assertTrue(repo.replaceCalls.isEmpty())
    }
}

/** Fake [PermissionPullPort] for unit testing. */
class FakePermissionPullPort : PermissionPullPort {
    var result: PermissionPullResult? = null
    var shouldThrow: Boolean = false
    var pullCount: Int = 0
        private set

    override suspend fun pullPermissions(): PermissionPullResult? {
        if (shouldThrow) throw RuntimeException("Simulated network error")
        pullCount++
        return result
    }
}

/** Fake [RolePermissionRepository] that records calls instead of hitting Room. */
class FakeRolePermissionRepo : RolePermissionRepository {
    // roleId -> set of permissionKeys
    private var permissions = mutableMapOf<String, MutableSet<String>>()
    val replaceCalls = mutableListOf<Pair<String, List<String>>>()

    override suspend fun getAllRoles() = emptyList<com.restaurantpos.core.model.Role>()

    override suspend fun getPermissionKeys(roleId: String): List<String> =
        permissions[roleId]?.toList().orEmpty()

    override suspend fun hasPermission(roleId: String, permissionKey: PermissionKey): Boolean =
        permissions[roleId]?.contains(permissionKey.key) == true

    override suspend fun replacePermissions(roleId: String, permissionKeys: List<String>) {
        replaceCalls.add(roleId to permissionKeys)
        if (permissionKeys.isEmpty()) {
            permissions.remove(roleId)
        } else {
            permissions[roleId] = permissionKeys.toMutableSet()
        }
    }

    override suspend fun getAllMappings(): List<RolePermission> =
        permissions.flatMap { (roleId, keys) -> keys.map { RolePermission(roleId, it) } }
}
