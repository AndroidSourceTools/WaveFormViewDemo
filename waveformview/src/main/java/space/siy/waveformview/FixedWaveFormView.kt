package space.siy.waveformview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
class FixedWaveFormView(context: Context, attr: AttributeSet?, defStyleAttr: Int) : View(context, attr, defStyleAttr) {
  constructor(context: Context) : this(context, null, 0)
  constructor(context: Context, attr: AttributeSet) : this(context, attr, 0)

  /**
   * Used to retrieve the values defined in the layout XML
   */
  private val lp = context.obtainStyledAttributes(attr, R.styleable.FixedWaveFormView, defStyleAttr, 0)

  /**
   * WaveFormData show in view
   */
  var data: WaveFormData? = null
    set(value) {
      field = value
      if (value == null) return
      CoroutineScope(Dispatchers.Default).launch {
        val possibleBlockCountOnScreen = floor(width / blockWidth).toInt()
        resampleData = FloatArray(possibleBlockCountOnScreen)
        if (value.samples.size > possibleBlockCountOnScreen) {
          val numberOfDataToNormalize: Int = value.samples.size / possibleBlockCountOnScreen
          for (i in 0 until possibleBlockCountOnScreen) {
            resampleData[i] = value.samples.average(
                i * numberOfDataToNormalize, (i + 1) * numberOfDataToNormalize
            )
          }
        }
        invalidate()
      }
    }

  /**
   * position in milliseconds
   */
  var position: Long = 0
    set(value) {
      if (seeking) return
      val lastValue = field
      field = value
      if (position == 0L) {
        lastDeltaProgress = 0
        seekingPosition = 0
      } else if (position == data?.duration) {
        seekingPosition = position
      }
      if (position - lastValue >= 0 && position >= seekingPosition) {
        lastDeltaProgress = position - lastValue
        seekingPosition = position
      } else {
        seekingPosition += lastDeltaProgress
      }
      invalidate()
    }

  private var lastDeltaProgress = 0L

  /**
   * Width each block
   */
  private var blockWidth = lp.getDimension(R.styleable.FixedWaveFormView_blockWidth, 5f)
    set(value) {
      field = value
      blockPaint.strokeWidth = blockWidth - SPLIT_GAP
    }
  /**
   * @see Callback
   */
  var callback: Callback? = null

  /**
   * Scale of top blocks
   */
  private var topBlockScale = lp.getFloat(R.styleable.FixedWaveFormView_topBlockScale, 1f)
  /**
   * Scale of bottom blocks
   */
  private var bottomBlockScale = lp.getFloat(R.styleable.FixedWaveFormView_bottomBlockScale, 0f)

  /**
   * Color used in played blocks
   */
  private var blockColorPlayed: Int = lp.getColor(R.styleable.FixedWaveFormView_blockColorPlayed, Color.RED)
    set(value) {
      field = value
      barShader = LinearGradient(canvasWidth / 2f - 1, 0f, canvasWidth / 2f + 1, 0f, blockColorPlayed, blockColor, Shader.TileMode.CLAMP)
      blockPaint.shader = barShader
    }

  /**
   * Color used in blocks default
   */
  private var blockColor: Int = lp.getColor(R.styleable.FixedWaveFormView_blockColor, Color.WHITE)
    set(value) {
      field = value
      barShader = LinearGradient(canvasWidth / 2f - 1, 0f, canvasWidth / 2f + 1, 0f, blockColorPlayed, blockColor, Shader.TileMode.CLAMP)
      blockPaint.shader = barShader
    }

  /**
   * The resampled data to show
   *
   * This generate when [data] set
   */
  private var resampleData = FloatArray(0)

  private val blockPaint = Paint()

  private var offsetX = 0f
  private var canvasWidth = 0
  private var seekingPosition = 0L

  private var barShader = LinearGradient(0f, 0f, 0f, 700f, Color.RED, Color.GRAY, Shader.TileMode.CLAMP)

  init {
    blockPaint.strokeWidth = blockWidth - 2
    blockPaint.shader = barShader
  }

  @SuppressLint("DrawAllocation")
  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)

    offsetX = (width / (data?.duration ?: 1L).toFloat()) * seekingPosition
    // Right now, I don't have any better way than allocating shader in every invalidate() invocation
    barShader = LinearGradient(offsetX, 0f, offsetX + 1, 0f, blockColorPlayed, blockColor, Shader.TileMode.CLAMP)
    blockPaint.shader = barShader

    // Draw data points
    if (resampleData.isNotEmpty()) {
      val maxAmplitude = resampleData.max()!!
      for (i in 0 until resampleData.size) {
        val x = i.toFloat() * blockWidth
        if (topBlockScale > 0f) {
          val startY = height * topBlockScale
          val stopY = startY - (startY * resampleData[i] / maxAmplitude)
          canvas.drawLine(x, startY, x, stopY, blockPaint)
        }
        if (bottomBlockScale > 0f) {
          val startY = (height - height * bottomBlockScale) + SPLIT_GAP
          val stopY = startY + ((height - startY) * resampleData[i] / maxAmplitude)
          canvas.drawLine(x, startY, x, stopY, blockPaint)
        }
      }
    }
  }

  private var lastTapTime = 0L
  private var paused = false
  @SuppressLint("ClickableViewAccessibility")
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
            callback?.onSeekStarted()
          }
          seekingPosition = ((data?.duration ?: 1L) * event.x.toLong()) / width
        }
      }
      MotionEvent.ACTION_UP -> {
        seekingCount = 0
        paused = false

        if (seeking) {
          seeking = false
          seekingPosition = ((data?.duration ?: 1L) * event.x.toLong()) / width
          callback?.onSeek(seekingPosition)
        } else {
          if (System.currentTimeMillis() - lastTapTime <= TAP_THRESHOLD_TIME) {
            callback?.onTap()
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

  fun forceComplete() {
    position = data?.duration ?: 0
  }

  private var seeking = false
  /**
   * Count up ACTION_MOVE event
   */
  private var seekingCount = 0

  /**
   * It provide a simple callback to sync your MediaPlayer
   */
  interface Callback {
    /**
     * Called when view tapped
     */
    fun onTap()

    /**
     * Called when gestures detects as an attempt to seek
     */
    fun onSeekStarted()

    /**
     * Called when seek complete
     * @param pos Position in milliseconds
     */
    fun onSeek(pos: Long)
  }

  companion object {
    const val SEEKING_THRESHOLD = 4
    const val TAP_THRESHOLD_TIME = 300L
    const val SPLIT_GAP = 2
  }
}