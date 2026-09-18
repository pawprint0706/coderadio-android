package io.github.pawprint0706.coderadio;

import org.json.JSONException;
import org.junit.Test;
import static org.junit.Assert.*;

public class NowPlayingParserTest {
    @Test public void discoversBothMountsAndListeners() throws Exception {
        StationSnapshot s = NowPlayingParser.parse("""
            {"is_online":true,"station":{"listen_url":"https://radio.example/default.mp3","mounts":[
                {"name":"/radio.mp3","url":"https://radio.example/high.mp3","is_default":true},
                {"name":"/low.mp3","url":"https://radio.example/low.mp3"}]},
             "now_playing":{"song":{"title":"Funkaholic","artist":"","text":"Flitz&Suppe - Funkaholic",
                "album":"Album","art":"/art/test.jpg"}},"listeners":{"current":321}}
            """);
        assertTrue(s.online);
        assertEquals("https://radio.example/high.mp3", s.stream(128));
        assertEquals("https://radio.example/low.mp3", s.stream(64));
        assertEquals("Flitz&Suppe", s.artist);
        assertEquals("Funkaholic", s.title);
        assertEquals(321, s.listeners);
        assertEquals("https://coderadio-admin-v2.freecodecamp.org/art/test.jpg", s.artwork);
    }
    @Test public void missingLowFallsBackToDefaultNotFirstMount() throws Exception {
        StationSnapshot s = NowPlayingParser.parse("""
            {"station":{"mounts":[
                {"name":"other","url":"https://radio.example/other"},
                {"name":"main","url":"https://radio.example/main","is_default":true}]}}
            """);
        assertEquals("", s.low);
        assertEquals("https://radio.example/main", s.stream(64));
    }
    @Test public void listensWithoutMountsAndIgnoresNullFields() throws Exception {
        StationSnapshot s = NowPlayingParser.parse("""
            {"station":{"listen_url":"https://radio.example/live"},
             "now_playing":{"song":{"title":null,"artist":"Unknown","text":" — Untagged track","art":null}},
             "listeners":{"current":-12}}
            """);
        assertEquals("Untagged track", s.title);
        assertEquals("", s.artist);
        assertEquals("", s.artwork);
        assertEquals(0, s.listeners);
        assertEquals("https://radio.example/live", s.stream(128));
    }
    @Test public void missingTitleUsesStationPlaceholder() throws Exception {
        assertEquals("Code Radio", NowPlayingParser.parse("{\"station\":{}}").title);
    }
    @Test public void offlineSnapshotDoesNotInventStream() throws Exception {
        StationSnapshot s = NowPlayingParser.parse("{\"is_online\":false,\"station\":{}}");
        assertFalse(s.online);
        assertEquals("", s.stream(128));
    }
    @Test public void rejectsUnsafeMountAndArtwork() throws Exception {
        StationSnapshot s = NowPlayingParser.parse("""
            {"station":{"listen_url":"https://radio.example/live","mounts":[
               {"url":"http://radio.example/unsafe","is_default":true}]},
             "now_playing":{"song":{"art":"file:///private/image"}}}
            """);
        assertEquals("https://radio.example/live", s.stream(128));
        assertEquals("", s.artwork);
    }
    @Test(expected = JSONException.class) public void rejectsApiErrorResponse() throws Exception {
        NowPlayingParser.parse("{\"error\":\"Unavailable\"}");
    }
    @Test public void usesExplicitBitrateWhenMountNameIsOpaque() throws Exception {
        StationSnapshot s = NowPlayingParser.parse("""
            {"station":{"mounts":[{"name":"mobile","bitrate":64,"url":"https://radio.example/mobile"}]}}
            """);
        assertEquals("https://radio.example/mobile", s.low);
    }
}
