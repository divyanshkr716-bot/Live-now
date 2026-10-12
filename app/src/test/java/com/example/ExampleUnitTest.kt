package com.example

import com.example.model.StreamChannel
import com.example.model.StreamPreset
import com.example.model.StreamStateEnum
import com.example.model.ChannelStreamStatus
import com.example.model.MultiStreamStatus
import com.example.util.VideoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testAlignDimension() {
    // Android 9 H.264 macroblock alignment checks
    assertEquals(848, VideoUtils.alignDimension(854, 16))
    assertEquals(1280, VideoUtils.alignDimension(1280, 16))
    assertEquals(1920, VideoUtils.alignDimension(1920, 16))
    assertEquals(640, VideoUtils.alignDimension(640, 16))
  }

  @Test
  fun testStreamChannelEndpoint() {
    val ch1 = StreamChannel(
      name = "Channel 1",
      serverUrl = "rtmp://a.rtmp.youtube.com/live2",
      streamKey = "abcd-1234-wxyz"
    )
    assertEquals("rtmp://a.rtmp.youtube.com/live2/abcd-1234-wxyz", ch1.fullRtmpEndpoint)
  }

  @Test
  fun testMultiStreamStatusAggregation() {
    val ch1 = ChannelStreamStatus(
      channelId = "c1",
      channelName = "Channel 1",
      state = StreamStateEnum.LIVE,
      durationSeconds = 120,
      currentBitrateKbps = 2000
    )
    val ch2 = ChannelStreamStatus(
      channelId = "c2",
      channelName = "Channel 2",
      state = StreamStateEnum.LIVE,
      durationSeconds = 60,
      currentBitrateKbps = 2100
    )
    val ch3 = ChannelStreamStatus(
      channelId = "c3",
      channelName = "Channel 3",
      state = StreamStateEnum.OFFLINE,
      durationSeconds = 0,
      currentBitrateKbps = 0
    )

    val multi = MultiStreamStatus(
      channelStatuses = mapOf("c1" to ch1, "c2" to ch2, "c3" to ch3)
    )

    assertEquals(2, multi.activeLiveCount)
    assertTrue(multi.isAnyStreaming)
    assertEquals(4100L, multi.totalBitrateKbps)
    assertEquals("00:02:00", ch1.formattedDuration)
    assertEquals("00:01:00", ch2.formattedDuration)
  }

  @Test
  fun testStreamPresets() {
    assertEquals(848, StreamPreset.ULTRA_BATTERY.width)
    assertEquals(480, StreamPreset.ULTRA_BATTERY.height)
    assertEquals(24, StreamPreset.ULTRA_BATTERY.fps)
    assertEquals(1200, StreamPreset.ULTRA_BATTERY.videoBitrateKbps)

    assertEquals(1280, StreamPreset.BALANCED_HD.width)
    assertEquals(720, StreamPreset.BALANCED_HD.height)
    assertEquals(30, StreamPreset.BALANCED_HD.fps)
    assertEquals(2000, StreamPreset.BALANCED_HD.videoBitrateKbps)
  }
}
