package com.restaurantpos.core.network

import com.restaurantpos.core.sync.SyncEntityType
import com.restaurantpos.core.sync.SyncOperation
import com.restaurantpos.core.sync.SyncRecord
import com.restaurantpos.core.sync.SyncResponse
import com.restaurantpos.core.sync.SyncStatus
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HttpRemoteSyncPortTest {

    private lateinit var server: MockWebServer
    private lateinit var port: HttpRemoteSyncPort

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        port = HttpRemoteSyncPort(
            baseUrl = server.url("").toString().trimEnd('/'),
            client = OkHttpClient(),
            authToken = { "test-token" },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun makeRecord(entityId: String = "e-1") = SyncRecord(
        id = "r-1",
        entityType = SyncEntityType.ORDER,
        entityId = entityId,
        operation = SyncOperation.UPDATE,
        payload = """{"id":"$entityId"}""",
        updatedAt = 1_000L,
        retryCount = 0,
        status = SyncStatus.PENDING,
    )

    @Test fun `200 response returns Accepted`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val result = port.push(makeRecord())
        assertTrue(result is SyncResponse.Accepted)
    }

    @Test fun `204 response returns Accepted`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        val result = port.push(makeRecord())
        assertTrue(result is SyncResponse.Accepted)
    }

    @Test fun `409 response returns Conflict with server body`() = runTest {
        val serverJson = """{"id":"e-1","status":"CLOSED"}"""
        server.enqueue(MockResponse().setResponseCode(409).setBody(serverJson))
        val result = port.push(makeRecord())
        assertTrue(result is SyncResponse.Conflict)
        assertEquals(serverJson, (result as SyncResponse.Conflict).serverPayload)
    }

    @Test fun `401 response returns PermanentError`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = port.push(makeRecord())
        assertTrue(result is SyncResponse.PermanentError)
    }

    @Test fun `410 response returns PermanentError`() = runTest {
        server.enqueue(MockResponse().setResponseCode(410))
        val result = port.push(makeRecord())
        assertTrue(result is SyncResponse.PermanentError)
    }

    @Test fun `500 response returns NetworkError`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = port.push(makeRecord())
        assertTrue(result is SyncResponse.NetworkError)
    }

    @Test fun `request includes Authorization header`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        port.push(makeRecord())
        val request = server.takeRequest()
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
    }

    @Test fun `request body contains entityType and payload`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        port.push(makeRecord("ord-42"))
        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("ORDER"))
        // F-014 回归：server 端 SyncPushRequest 要求顶层 id（幂等去重键）
        assertTrue(body.contains("\"id\":\"r-1\""))
        assertTrue(body.contains("ord-42"))
    }
}
