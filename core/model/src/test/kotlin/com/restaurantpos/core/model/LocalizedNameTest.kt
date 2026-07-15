package com.restaurantpos.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalizedNameTest {

    @Test fun `exact bare language key wins`() {
        assertEquals("可乐", mapOf("zh" to "可乐", "en" to "Cola").localizedName("zh-CN"))
    }

    @Test fun `exact full locale tag matches when no bare key`() {
        assertEquals("可乐", mapOf("zh-CN" to "可乐", "en-US" to "Cola").localizedName("zh-CN"))
    }

    @Test fun `server-style BCP47 keys resolve by language prefix`() {
        // Regression for KDS bug: snapshot keyed "en-US"/"zh-CN", lookup locale "zh-CN"
        assertEquals("可乐", mapOf("en-US" to "Cola", "zh-CN" to "可乐").localizedName("zh-CN"))
    }

    @Test fun `falls back to English when requested language absent`() {
        assertEquals("Cola", mapOf("en-US" to "Cola", "ja-JP" to "コーラ").localizedName("ko-KR"))
    }

    @Test fun `falls back to any value when no English`() {
        assertEquals("コーラ", mapOf("ja-JP" to "コーラ").localizedName("ko-KR"))
    }

    @Test fun `empty map yields empty string`() {
        assertEquals("", emptyMap<String, String>().localizedName("en-US"))
    }
}
