package com.admin.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentKeyUtilTest {

    @Test
    void generatePlaintextKeyShouldUseExpectedFormat() {
        String key = AgentKeyUtil.generatePlaintextKey();

        assertTrue(key.startsWith("fpak_"));
        String[] segments = key.split("_");
        assertEquals(3, segments.length);
        assertEquals(12, segments[1].length());
        assertEquals(32, segments[2].length());
    }

    @Test
    void hashKeyShouldBeStableAndNotEqualToRawKey() {
        String rawKey = "fpak_testprefix_testsecretvalue1234567890";

        String firstHash = AgentKeyUtil.hashKey(rawKey);
        String secondHash = AgentKeyUtil.hashKey(rawKey);

        assertEquals(firstHash, secondHash);
        assertNotEquals(rawKey, firstHash);
        assertEquals(64, firstHash.length());
    }

    @Test
    void extractPrefixShouldReturnMiddleSegment() {
        String rawKey = "fpak_prefixabc123_secretxyz987654321";

        assertEquals("prefixabc123", AgentKeyUtil.extractPrefix(rawKey));
    }
}
