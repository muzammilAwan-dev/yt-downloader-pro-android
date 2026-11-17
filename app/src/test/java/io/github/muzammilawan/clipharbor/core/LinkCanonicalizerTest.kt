package io.github.muzammilawan.clipharbor.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkCanonicalizerTest {
    @Test fun dropsYoutubeShareParametersButKeepsPlaybackOnes() {
        assertEquals(
            "https://www.youtube.com/watch?v=abc&t=42",
            LinkCanonicalizer.canonicalize("https://www.youtube.com/watch?v=abc&si=track&feature=share&t=42"),
        )
        assertEquals(
            "https://www.youtube.com/watch?v=x&list=PL1&index=2",
            LinkCanonicalizer.canonicalize("https://www.youtube.com/watch?v=x&list=PL1&index=2&si=z"),
        )
        assertEquals("https://youtu.be/abc", LinkCanonicalizer.canonicalize("https://youtu.be/abc?si=x"))
    }

    @Test fun dropsKnownShareParametersForSocialHosts() {
        assertEquals("https://www.instagram.com/p/ABC/",
            LinkCanonicalizer.canonicalize("https://www.instagram.com/p/ABC/?igsh=abc&utm_source=x"))
        assertEquals("https://x.com/user/status/123",
            LinkCanonicalizer.canonicalize("https://x.com/user/status/123?s=20&t=abc"))
        assertEquals("https://www.reddit.com/r/x/comments/abc/",
            LinkCanonicalizer.canonicalize("https://www.reddit.com/r/x/comments/abc/?utm_source=share&share_id=1"))
        assertEquals("https://soundcloud.com/a/b",
            LinkCanonicalizer.canonicalize("https://soundcloud.com/a/b?si=track"))
    }

    @Test fun neverRewritesUnknownHostsOrSignedUrls() {
        val unknown = "https://example.invalid/watch?v=x&si=y&utm_source=z"
        assertEquals(unknown, LinkCanonicalizer.canonicalize(unknown))
        val signed = "https://www.youtube.com/watch?v=x&sig=y"
        assertEquals(signed, LinkCanonicalizer.canonicalize(signed))
        val cdn = "https://x.com/a?Policy=abc&Key-Pair-Id=k"
        assertEquals(cdn, LinkCanonicalizer.canonicalize(cdn))
    }

    @Test fun normalisesCaseAndDropsFragmentOnlyOnRecognisedHosts() {
        assertEquals("https://www.youtube.com/watch?v=x",
            LinkCanonicalizer.canonicalize("HTTPS://WWW.YOUTUBE.COM/watch?v=x&si=y"))
        assertEquals("https://www.youtube.com/watch?v=x",
            LinkCanonicalizer.canonicalize("https://www.youtube.com/watch?v=x#t=10"))
        assertEquals("https://example.invalid/a#part",
            LinkCanonicalizer.canonicalize("https://example.invalid/a#part"))
    }

    @Test fun keepsValidPortsAndHandlesValuelessParameters() {
        assertEquals("https://www.youtube.com:443/watch?v=x",
            LinkCanonicalizer.canonicalize("https://www.youtube.com:443/watch?v=x&si=y"))
        assertEquals("https://www.youtube.com/watch?v=x",
            LinkCanonicalizer.canonicalize("https://www.youtube.com/watch?v=x&si"))
    }

    @Test fun mediaEntryReturnsTheCanonicalForm() {
        val input = MediaEntry.pasted("https://youtu.be/abc?si=track") as MediaEntry.Input.Link
        assertEquals("https://youtu.be/abc", input.url)
    }
}
