package space.siy.waveformview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.floor

/**
 * Copyright 2018 siy1121
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * A WaveFormView show WaveFormData given
 *
 * You have to build [WaveFormData] first using [WaveFormData.Factory]
 *
 */
class FittedWaveFormView(context: Context, attr: AttributeSet?, defStyleAttr: Int) : View(context, attr, defStyleAttr) {
  constructor(context: Context) : this(context, null, 0)
  constructor(context: Context, attr: AttributeSet) : this(context, attr, 0)

  /**
   * Used to retrieve the values defined in the layout XML
   */
  private val lp = context.obtainStyledAttributes(attr, R.styleable.WaveFormView, defStyleAttr, 0)

  companion object {
    /**
     * Flag to use average in specific range
     */
    const val PEAKMODE_AVERAGE = 0
    /**
     * Flag to use the maximum value in specific range
     */
    const val PEAKMODE_MAX = 1
  }

  /**
   * WaveFormData show in view
   */
  var data: WaveFormData? = null
    set(value) {
      field = value
      if (value == null) return
      val possibleBlockCountOnScreen = floor(width / blockWidth).toInt()
      resampleData = FloatArray(possibleBlockCountOnScreen)
      if (value.samples.size > possibleBlockCountOnScreen) {
        val numberOfDataToNormalize: Int = value.samples.size / possibleBlockCountOnScreen
        for (i in 0 until possibleBlockCountOnScreen) {
          resampleData[i] = value.samples.average(i * numberOfDataToNormalize, (i + 1) * numberOfDataToNormalize)
        }
      }
    }

  /**
   * Method to detect blocks height
   *
   * Use [PEAKMODE_AVERAGE] or [PEAKMODE_MAX]
   */
  var peakMode = lp.getInteger(R.styleable.WaveFormView_peakMode, PEAKMODE_AVERAGE)
    set(value) {
      field = value
      data = data
    }

  /**
   * position in milliseconds
   */
  var position: Long = 0
    set(value) {
      if (seeking) return
      field = value
      seekingPosition = position
      invalidate()
    }

  /**
   * Duration which a block represent
   *
   *
   */
  var secPerBlock = lp.getFloat(R.styleable.WaveFormView_secPerBlock, 0.5f)
    set(value) {
      field = value
      data = data
    }

  /**
   * Width each block
   */
  var blockWidth = lp.getFloat(R.styleable.WaveFormView_blockWidth, 10f)
    set(value) {
      field = value
      blockPaint.strokeWidth = blockWidth - 2
    }
  /**
   * @see Callback
   */
  var callback: Callback? = null

  /**
   * Scale of top blocks
   */
  var topBlockScale = lp.getFloat(R.styleable.WaveFormView_topBlockScale, 1f)
  /**
   * Scale of bottom blocks
   */
  var bottomBlockScale = lp.getFloat(R.styleable.WaveFormView_bottomBlockScale, 0.5f)

  /**
   * If you want to hide, set false
   */
  var showTimeText = lp.getBoolean(R.styleable.WaveFormView_showTimeText, true)

  /**
   * Color used in played blocks
   */
  var blockColorPlayed: Int = lp.getColor(R.styleable.WaveFormView_blockColorPlayed, Color.RED)
    set(value) {
      field = value
      barShader = LinearGradient(canvasWidth / 2f - 1, 0f, canvasWidth / 2f + 1, 0f, blockColorPlayed, blockColor, Shader.TileMode.CLAMP)
      blockPaint.shader = barShader
    }

  /**
   * Color used in blocks default
   */
  var blockColor: Int = lp.getColor(R.styleable.WaveFormView_blockColor, Color.WHITE)
    set(value) {
      field = value
      barShader = LinearGradient(canvasWidth / 2f - 1, 0f, canvasWidth / 2f + 1, 0f, blockColorPlayed, blockColor, Shader.TileMode.CLAMP)
      blockPaint.shader = barShader
    }

  /**
   * Color used in the text
   */
  var textColor: Int = lp.getColor(R.styleable.WaveFormView_textColor, Color.WHITE)
    set(value) {
      field = value
      textPaint.color = value
    }

  /**
   * Color used in the text background
   */
  var textBgColor: Int = lp.getColor(R.styleable.WaveFormView_textBgColor, 0xAA000000.toInt())
    set(value) {
      field = value
      textBgPaint.color = value
    }
  /**
   *You can set a MediaController
   * It enables to automate media control and setting position
   */
  var controller: MediaControllerCompat? = null
    set(value) {
      if (value == null && field != null) {
        field?.unregisterCallback(mediaControllerCallback)
      }
      field = value
      if (value == null) return
      value.registerCallback(mediaControllerCallback)
    }

  private val mediaControllerCallback = object : MediaControllerCompat.Callback() {
    override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
      position = state?.position ?: 0L
    }
  }

  /**
   * The resampled data to show
   *
   * This generate when [data] set
   */
  private var resampleData = FloatArray(0)

  private val blockPaint = Paint()
  private val textPaint = Paint()
  private val textBgPaint = Paint()

  private var offsetX = 0f
  private var canvasWidth = 0
  private var seekingPosition = 0L
  private var donePlaying = false

  private var barShader = LinearGradient(0f, 0f, 0f, 700f, Color.RED, Color.GRAY, Shader.TileMode.CLAMP)

  init {
    blockPaint.strokeWidth = blockWidth - 2
    blockPaint.shader = barShader

    textPaint.color = textColor
    textPaint.style = Paint.Style.FILL_AND_STROKE
    textPaint.textSize = 40f
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.isAntiAlias = true

    textBgPaint.color = textBgColor
  }

  @SuppressLint("DrawAllocation")
  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)

    //When canvas size changed, initialize block paint shader
    if (canvasWidth != width) {
      canvasWidth = width
      barShader = LinearGradient(99f, 0f, 100f, 0f, blockColorPlayed, blockColor, Shader.TileMode.CLAMP)
      blockPaint.shader = barShader
    }

    offsetX = (width / (data?.duration ?: 1L).toFloat()) * seekingPosition
    // Right now, I don't have any better way than allocating shader in every invalidate() invocation
    barShader = LinearGradient(offsetX, 0f, offsetX + 1, 0f, blockColorPlayed, blockColor, Shader.TileMode.CLAMP)
    blockPaint.shader = barShader

    // Draw data points
    if (resampleData.isNotEmpty()) {
      val maxAmplitude = resampleData.max()!!
      for (i in 0 until resampleData.size) {
        val x = i.toFloat() * blockWidth
        val startY = height.toFloat() //height / 2f
        val stopY = height.toFloat() - (height.toFloat() * resampleData[i] / maxAmplitude)
        canvas.drawLine(x, startY, x, stopY, blockPaint)
        //canvas.drawLine(x, canvas.height / 2f + 2, x, canvas.height / 2f + (resampleData[i] / Short.MAX_VALUE) * canvas.height / 2 * bottomBlockScale, blockPaint)
      }
    }

    donePlaying = offsetX.toInt() == 100
  }

  val SEEKING_THRESHOLD = 4
  val TAP_THRESHOLD_TIME = 300L

  var lastTapTime = 0L
  var paused = false
  override fun onTouchEvent(event: MotionEvent): Boolean {
    when (event.action) {
      MotionEvent.ACTION_DOWN -> {
        lastTapTime = System.currentTimeMillis()
      }
      MotionEvent.ACTION_MOVE -> {
        seekingCount++
        if (seekingCount > SEEKING_THRESHOLD) seeking = true
        if (seeking) {
          if (!paused) {
            paused = true
            callback?.onPause()
          }
          seekingPosition = ((data?.duration ?: 1L) * event.x.toLong()) / width
        }
      }
      MotionEvent.ACTION_UP -> {
        if (seeking) {
          seeking = false
          seekingCount = 0

          seekingPosition = ((data?.duration ?: 1L) * event.x.toLong()) / width
          callback?.onSeek(seekingPosition)
          controller?.transportControls?.seekTo(seekingPosition)
        } else {
          seekingCount = 0

          if (controller?.playbackState?.playbackState == PlaybackStateCompat.STATE_PLAYING)
            controller?.transportControls?.pause()
          else
            controller?.transportControls?.play()

          if (System.currentTimeMillis() - lastTapTime <= TAP_THRESHOLD_TIME) {
            paused = false
            callback?.onPlay()
          }
        }
      }
    }
    invalidate()
    return true
  }

  /**
   * Extension method to average data in specific range
   */
  private fun ShortArray.average(start: Int, end: Int): Float {
    var sum = 0.0
    for (i in start until end)
      sum += Math.abs(this[i].toDouble())

    return sum.toFloat() / (end - start)
  }

  /**
   * Extension Method to detect maximum value in specific range
   */
  private fun ShortArray.max(start: Int, end: Int): Float {
    var max = 0f
    for (i in start until end)
      if (max < this[i].toFloat())
        max = this[i].toFloat()

    return max
  }

  /**
   * Temporary value to calculate swipe distance
   */
  private var ox = 0f
  /**
   * Temporary value holding the old offset
   */
  private var oox = 0f

  private var seeking = false
  /**
   * Count up ACTION_MOVE event
   */
  private var seekingCount = 0

  private fun Long.toTimeText(): String {
    val mm = (this / 1000 / 60).toString()
    val ss = (this / 1000 % 60).toString()
    return mm + ":" + if (ss.length == 2) ss else "0$ss"
  }

  /**
   * It provide a simple callback to sync your MediaPlayer
   */
  interface Callback {
    /**
     * Called when view clicked
     */
    fun onPlay()

    fun onPause()

    /**
     * Called when seek complete
     * @param pos Position in milliseconds
     */
    fun onSeek(pos: Long)
  }
}