package com.galactic.mimeartiststreetperformer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class InvisibleWallActivity : AppCompatActivity() {

    // UI elements
    private lateinit var progressBar: ProgressBar
    private lateinit var timerText: TextView
    private lateinit var touchInputArea: FrameLayout
    private lateinit var hintText: TextView
    private lateinit var topBarContainer: View
    private lateinit var centerHintContainer: LinearLayout
    private lateinit var backButton: ImageButton
    private lateinit var customAlertTextView: TextView

    // Game variables
    private var gameTimer: CountDownTimer? = null
    private var isGameActive = false
    private var initialX = 0f
    private var initialY = 0f
    private val touchThreshold = 15f
    private val gameDuration = 10000L

    private lateinit var handler: Handler

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_invisible_wall)

        handler = Handler(Looper.getMainLooper())

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.invisibleWallScene)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize UI components
        progressBar = findViewById(R.id.gameProgressBar)
        timerText = findViewById(R.id.timerTextView)
        touchInputArea = findViewById(R.id.touchInputArea)
        hintText = findViewById(R.id.gestureHintText)
        topBarContainer = findViewById(R.id.topBarContainer)
        centerHintContainer = findViewById(R.id.centerHintContainer)
        backButton = findViewById(R.id.backButton)
        customAlertTextView = findViewById(R.id.customAlertTextView)

        // Set up listeners
        backButton.setOnClickListener { finish() }

        touchInputArea.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }

        animateUIAppearance()
        resetGame()
    }

    private fun handleTouch(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!isGameActive) {
                    initialX = event.x
                    initialY = event.y
                    startGame()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isGameActive) {
                    val dx = abs(event.x - initialX)
                    val dy = abs(event.y - initialY)
                    if (sqrt(dx.pow(2) + dy.pow(2)) > touchThreshold) {
                        endGame(isWin = false)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isGameActive) {
                    endGame(isWin = false)
                }
            }
        }
    }

    private fun startGame() {
        isGameActive = true
        animateHintTextChange()

        touchInputArea.animate()
            .scaleX(1.05f).scaleY(1.05f).setDuration(200)
            .withEndAction {
                touchInputArea.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
            }.start()

        gameTimer = object : CountDownTimer(gameDuration, 50) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = "%.1f".format(millisUntilFinished / 1000.0)
                timerText.text = "$seconds s"
                progressBar.progress = (millisUntilFinished * 100 / gameDuration).toInt()
            }

            override fun onFinish() {
                endGame(isWin = true)
            }
        }.start()
    }

    private fun endGame(isWin: Boolean) {
        if (!isGameActive) return
        isGameActive = false
        gameTimer?.cancel()

        if (isWin) {
            showCustomAlert("You Win!", true)
        } else {
            showCustomAlert("Don't Move!", false)
            val shake = AnimationUtils.loadAnimation(this, R.anim.shake_effect)
            touchInputArea.startAnimation(shake)
        }

        handler.postDelayed({ resetGame() }, 1500) // Increased delay for alert
    }

    private fun resetGame() {
        isGameActive = false
        hintText.text = "Place your finger on the zone and hold"
        timerText.text = "10.0 s"
        progressBar.progress = 100
        hintText.alpha = 1f
    }

    private fun showCustomAlert(message: String, isWin: Boolean) {
        customAlertTextView.text = message
        val color = if (isWin) Color.parseColor("#99FF99") else Color.parseColor("#FF9999")
        customAlertTextView.setTextColor(color)

        customAlertTextView.alpha = 0f
        customAlertTextView.scaleX = 0.5f
        customAlertTextView.scaleY = 0.5f

        val fadeIn = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(customAlertTextView, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(customAlertTextView, "scaleX", 0.5f, 1f),
                ObjectAnimator.ofFloat(customAlertTextView, "scaleY", 0.5f, 1f)
            )
            duration = 400
            interpolator = DecelerateInterpolator()
        }

        val fadeOut = ObjectAnimator.ofFloat(customAlertTextView, "alpha", 1f, 0f).apply {
            duration = 400
            startDelay = 1000 // How long the alert stays visible
        }

        val finalSet = AnimatorSet()
        finalSet.playSequentially(fadeIn, fadeOut)
        finalSet.start()
    }

    private fun animateUIAppearance() {
        // Animate top bar
        topBarContainer.translationY = -200f
        topBarContainer.animate().translationY(0f).setInterpolator(DecelerateInterpolator(1.5f)).setDuration(800).start()

        // Animate back button
        backButton.alpha = 0f
        backButton.animate().alpha(1f).setDuration(600).setStartDelay(200).start()

        // Animate center content and touch area
        val fadeIn = AnimatorSet()
        centerHintContainer.alpha = 0f
        touchInputArea.alpha = 0f
        fadeIn.playTogether(
            ObjectAnimator.ofFloat(centerHintContainer, "alpha", 0f, 1f),
            ObjectAnimator.ofFloat(touchInputArea, "alpha", 0f, 1f)
        )
        fadeIn.interpolator = AccelerateInterpolator()
        fadeIn.startDelay = 400
        fadeIn.duration = 600
        fadeIn.start()
    }

    private fun animateHintTextChange() {
        ObjectAnimator.ofFloat(hintText, "alpha", 1f, 0f).apply {
            duration = 300
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    hintText.text = "Hold still!"
                    ObjectAnimator.ofFloat(hintText, "alpha", 0f, 1f).setDuration(300).start()
                }
            })
            start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gameTimer?.cancel()
        handler.removeCallbacksAndMessages(null)
    }
}