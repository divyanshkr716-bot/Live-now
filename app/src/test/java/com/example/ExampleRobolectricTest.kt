package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.StreamConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("YouTube Loop Live", appName)
  }

  @Test
  fun `stream config rtmp endpoint formatting`() {
    val config = StreamConfig(
      serverUrl = "rtmp://a.rtmp.youtube.com/live2",
      streamKey = "abcd-1234-efgh-5678"
    )
    assertEquals(
      "rtmp://a.rtmp.youtube.com/live2/abcd-1234-efgh-5678",
      config.fullRtmpEndpoint
    )
  }
}

