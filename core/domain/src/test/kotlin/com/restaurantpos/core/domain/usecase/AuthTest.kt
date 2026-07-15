package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.RolePermissionRepository
import com.restaurantpos.core.domain.repository.UserRepository
import com.restaurantpos.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AuthTest {

    private fun makeUser(
        id: String = "u1",
        roleId: String = "cashier",
        pin: String = "1234",
        isActive: Boolean = true,
    ) = User(
        id = id,
        displayName = "Test",
        roleId = roleId,
        pinHash = pin.sha256(),
        isActive = isActive,
        createdAt = 0L,
    )

    /** 27 权限 key 全集 */
    private val ALL_KEYS = PermissionKey.entries.map { it.key }

    /** 根据角色 ID 返回该角色默认权限 key 集合（对齐 PRD §4.1 默认矩阵） */
    private fun defaultPerms(roleId: String): Set<String> =
        when (roleId) {
            "admin"  -> ALL_KEYS.toSet()
            "manager" -> ALL_KEYS.filterNot {
                it in setOf("settings.region", "settings.tax", "staff.manage", "staff.roles")
            }.toSet()
            "cashier" -> setOf(
                "order.create", "order.note",
                "payment.process", "payment.discount", "payment.tip", "payment.coupon",
                "menu.view", "menu.sold_out",
                "report.daily",
            )
            "waiter"  -> setOf("order.create", "order.note", "menu.view")
            else       -> emptySet()
        }

    private inner class FakeRolePermissionRepository(
        private val perms: Map<String, Set<String>>,
    ) : RolePermissionRepository {

        override suspend fun getAllRoles(): List<Role> = perms.map { (id, _) ->
            Role(id = id, displayName = "role.$id", isBuiltin = true, sortOrder = 0)
        }

        override suspend fun getPermissionKeys(roleId: String): List<String> =
            perms[roleId]?.toList() ?: emptyList()

        override suspend fun hasPermission(roleId: String, permissionKey: PermissionKey): Boolean =
            perms[roleId]?.contains(permissionKey.key) ?: false

        override suspend fun replacePermissions(roleId: String, permissionKeys: List<String>) {
            // no-op for tests
        }

        override suspend fun getAllMappings(): List<RolePermission> =
            perms.flatMap { (rid, keys) -> keys.map { RolePermission(rid, it) } }
    }

    // ── LoginWithPinUseCase ──────────────────────────────────────

    @Test fun `correct PIN returns success`() = runTest {
        val user = makeUser(pin = "9999")
        val result = LoginWithPinUseCase(FakeUserRepo(listOf(user)))("9999")
        assertTrue(result is LoginWithPinUseCase.Result.Success)
        assertEquals(user.id, (result as LoginWithPinUseCase.Result.Success).user.id)
    }

    @Test fun `wrong PIN returns InvalidPin`() = runTest {
        val user = makeUser(pin = "9999")
        val result = LoginWithPinUseCase(FakeUserRepo(listOf(user)))("0000")
        assertTrue(result is LoginWithPinUseCase.Result.InvalidPin)
    }

    @Test fun `inactive user returns UserInactive`() = runTest {
        val user = makeUser(pin = "9999", isActive = false)
        val result = LoginWithPinUseCase(FakeUserRepo(listOf(user)))("9999")
        assertTrue(result is LoginWithPinUseCase.Result.UserInactive)
    }

    // ── CheckPermissionUseCase (Batch 46 · DB 驱动) ─────────────

    @Test fun `admin has all 27 permissions`() = runTest {
        val admin = makeUser(roleId = "admin")
        val repo  = FakeRolePermissionRepository(mapOf("admin" to defaultPerms("admin")))
        val check = CheckPermissionUseCase(repo)
        PermissionKey.entries.forEach { pk ->
            assertEquals(
                "admin 应拥有权限 ${pk.key}",
                CheckPermissionUseCase.Result.Allowed,
                check(admin, pk),
            )
        }
    }

    @Test fun `cashier cannot refund`() = runTest {
        val cashier = makeUser(roleId = "cashier")
        val repo = FakeRolePermissionRepository(mapOf("cashier" to defaultPerms("cashier")))
        val result = CheckPermissionUseCase(repo)(cashier, PermissionKey.PAYMENT_REFUND)
        assertEquals(CheckPermissionUseCase.Result.Denied, result)
    }

    @Test fun `waiter can place order`() = runTest {
        val waiter = makeUser(roleId = "waiter")
        val repo = FakeRolePermissionRepository(mapOf("waiter" to defaultPerms("waiter")))
        val result = CheckPermissionUseCase(repo)(waiter, PermissionKey.ORDER_CREATE)
        assertEquals(CheckPermissionUseCase.Result.Allowed, result)
    }

    @Test fun `waiter cannot void item`() = runTest {
        val waiter = makeUser(roleId = "waiter")
        val repo = FakeRolePermissionRepository(mapOf("waiter" to defaultPerms("waiter")))
        val result = CheckPermissionUseCase(repo)(waiter, PermissionKey.ORDER_VOID_ITEM)
        assertEquals(CheckPermissionUseCase.Result.Denied, result)
    }

    @Test fun `null user returns NotLoggedIn`() = runTest {
        val repo = FakeRolePermissionRepository(emptyMap())
        val result = CheckPermissionUseCase(repo)(null, PermissionKey.ORDER_CREATE)
        assertEquals(CheckPermissionUseCase.Result.NotLoggedIn, result)
    }

    @Test fun `inactive user returns Denied`() = runTest {
        val user = makeUser(isActive = false)
        val repo = FakeRolePermissionRepository(mapOf("cashier" to defaultPerms("cashier")))
        val result = CheckPermissionUseCase(repo)(user, PermissionKey.ORDER_CREATE)
        assertEquals(CheckPermissionUseCase.Result.Denied, result)
    }

    @Test fun `manager can refund and view reports`() = runTest {
        val manager = makeUser(roleId = "manager")
        val repo = FakeRolePermissionRepository(mapOf("manager" to defaultPerms("manager")))
        val check = CheckPermissionUseCase(repo)
        assertEquals(CheckPermissionUseCase.Result.Allowed, check(manager, PermissionKey.PAYMENT_REFUND))
        assertEquals(CheckPermissionUseCase.Result.Allowed, check(manager, PermissionKey.REPORT_DAILY))
        assertEquals(CheckPermissionUseCase.Result.Allowed, check(manager, PermissionKey.REPORT_SHIFT))
    }

    @Test fun `manager cannot manage users`() = runTest {
        val manager = makeUser(roleId = "manager")
        val repo = FakeRolePermissionRepository(mapOf("manager" to defaultPerms("manager")))
        val result = CheckPermissionUseCase(repo)(manager, PermissionKey.STAFF_MANAGE)
        assertEquals(CheckPermissionUseCase.Result.Denied, result)
    }

    // ── FakeUserRepo ─────────────────────────────────────────

    private inner class FakeUserRepo(private val users: List<User>) : UserRepository {
        override fun observeActive(): Flow<List<User>> = flowOf(users)
        override suspend fun getById(id: String): User? = users.find { it.id == id }
        override suspend fun getByPin(pinHash: String): User? = users.find { it.pinHash == pinHash }
        override suspend fun save(user: User) { }
        override suspend fun setActive(id: String, isActive: Boolean) { }
    }
}
