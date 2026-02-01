package com.da_emi_locker.backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FCMCommandPayloadTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `test toggle payload construction`() {
        val commandId = "123"
        val commandType = "USB_BLOCK"
        val commandData = "{}" // formatted as empty json object string
        
        val innerPayloadMap = mapOf(
            "command_id" to commandId,
            "command" to commandType,
            "payload" to objectMapper.readTree(commandData)
        )
        
        val innerPayloadString = objectMapper.writeValueAsString(innerPayloadMap)
        
        // Expected: {"command_id":"123","command":"USB_BLOCK","payload":{}}
        // Note: Map order is not guaranteed in string output, so we should parse back to verify
        
        val parsed = objectMapper.readTree(innerPayloadString)
        assertEquals("123", parsed.get("command_id").asText())
        assertEquals("USB_BLOCK", parsed.get("command").asText())
        assertTrue(parsed.get("payload").isObject)
        assertTrue(parsed.get("payload").isEmpty)
    }

    @Test
    fun `test complex command payload`() {
        val commandId = "456"
        val commandType = "HIDE_APPS"
        val commandData = """{"packages": ["com.example.app1"]}"""
        
        val innerPayloadMap = mapOf(
            "command_id" to commandId,
            "command" to commandType,
            "payload" to objectMapper.readTree(commandData)
        )
        
        val innerPayloadString = objectMapper.writeValueAsString(innerPayloadMap)
        
        val parsed = objectMapper.readTree(innerPayloadString)
        assertEquals("HIDE_APPS", parsed.get("command").asText())
        assertEquals("com.example.app1", parsed.get("payload").get("packages").get(0).asText())
    }
}
