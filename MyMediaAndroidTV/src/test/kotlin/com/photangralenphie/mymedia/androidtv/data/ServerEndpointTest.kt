package com.photangralenphie.mymedia.androidtv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerEndpointTest {
    @Test
    fun `host without scheme uses HTTP and configured port`() {
        assertEquals("http://media-server.local:8080", serverEndpoint("media-server.local", 8080))
    }

    @Test
    fun `whitespace and trailing slash are removed`() {
        assertEquals("http://192.168.1.25:9000", serverEndpoint(" 192.168.1.25/ ", 9000))
    }

    @Test
    fun `scheme is preserved when adding configured port`() {
        assertEquals("https://media-server.local:8443", serverEndpoint("https://media-server.local", 8443))
    }

    @Test
    fun `explicit port is preserved`() {
        assertEquals("http://media-server.local:7000", serverEndpoint("http://media-server.local:7000", 8080))
    }
}
