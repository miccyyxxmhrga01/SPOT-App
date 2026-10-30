package com.example.spotapp.data.cloudinary

import org.junit.Assert.*
import org.junit.Test

class CloudinaryImagesTest {
    private val photo = "https://res.cloudinary.com/spot/image/upload/v1712345678/spot/posts/uid/post/photo.jpg"
    private fun sized(transformation: String) = photo.replace("/upload/", "/upload/$transformation/")

    @Test fun photosAreRequestedAtTheNextSizeStepForTheirBox() {
        assertEquals(sized("f_webp,q_auto,w_1080,c_limit"), cloudinaryDisplayUrl(photo, 1000, 0, crop = false))
        assertEquals(sized("f_webp,q_auto,w_160,h_160,c_lfill"), cloudinaryDisplayUrl(photo, 126, 126, crop = true))
        // A cropped box keeps its shape so the photo still covers it.
        assertEquals(sized("f_webp,q_auto,w_1152,h_1440,c_lfill"), cloudinaryDisplayUrl(photo, 1080, 1350, crop = true))
        assertEquals(sized("f_webp,q_auto,w_1152,h_2560,c_limit"), cloudinaryDisplayUrl(photo, 1080, 2400, crop = false))
        // Boxes past the largest step are capped at it.
        assertEquals(sized("f_webp,q_auto,w_1228,h_2560,c_limit"), cloudinaryDisplayUrl(photo, 1440, 3000, crop = false))
    }

    @Test fun otherUrlsAndUnknownSizesAreLeftAlone() {
        assertNull(cloudinaryDisplayUrl(photo, 0, 0, crop = true))
        assertNull(cloudinaryDisplayUrl(sized("w_960,c_limit"), 500, 500, crop = false))
        assertNull(cloudinaryDisplayUrl(photo.replace("/image/", "/video/"), 500, 500, crop = false))
        assertNull(cloudinaryDisplayUrl(photo.replace("/image/", "/raw/"), 500, 500, crop = false))
        assertNull(cloudinaryDisplayUrl("https://example.com/image/upload/v1/photo.jpg", 500, 500, crop = false))
        assertNull(cloudinaryDisplayUrl("content://media/external/images/1", 500, 500, crop = false))
    }
}
