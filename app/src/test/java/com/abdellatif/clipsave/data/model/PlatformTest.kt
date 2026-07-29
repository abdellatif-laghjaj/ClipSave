package com.abdellatif.clipsave.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformTest {
    @Test
    fun detectsFullUrlsAndSubdomains() {
        assertEquals(Platform.YOUTUBE, Platform.fromUrl("https://m.youtube.com/watch?v=abc"))
        assertEquals(Platform.INSTAGRAM, Platform.fromUrl("https://www.instagram.com/reel/abc/"))
        assertEquals(Platform.TIKTOK, Platform.fromUrl("https://vm.tiktok.com/ZM123/"))
        assertEquals(Platform.TWITTER, Platform.fromUrl("https://mobile.x.com/user/status/1"))
    }

    @Test
    fun detectsBareDomainsAndLinksInsideSharedText() {
        assertEquals(Platform.REDDIT, Platform.fromUrl("reddit.com/r/android/comments/abc"))
        assertEquals(
            Platform.YOUTUBE,
            Platform.fromUrl("Watch this: https://youtu.be/abc?si=xyz sent from my phone")
        )
    }

    @Test
    fun unknownAndInvalidInputsFallBackGracefully() {
        assertEquals(Platform.GENERIC, Platform.fromUrl("https://example.org/video/123"))
        assertEquals(Platform.GENERIC, Platform.fromUrl("not a link"))
        assertEquals(Platform.GENERIC, Platform.fromUrl(""))
    }
}
