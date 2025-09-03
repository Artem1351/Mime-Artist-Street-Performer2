package com.galactic.mimeartiststreetperformer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs

class LadderGameActivity : AppCompatActivity() {

    private enum class GamePhase(val instruction: String) {
        INITIAL_HOLD("Place your finger and hold"),
        HOLD("Keep holding your finger"),
        SWIPE_DOWN("Now, swipe down"),
        PREPARE_HOLD_1("Get ready to hold in 2 seconds..."),
        HOLD_1("Keep holding your finger"),
        SWIPE_UP("Now, swipe up"),
        PREPARE_HOLD_2("Get ready to hold in 2 seconds..."),
        HOLD_2("Keep holding your finger"),
        WIN("You win!"),
        FAIL("Wrong move!")
    }

    // UI elements
    private lateinit var progressBar: ProgressBar
    private lateinit var touchInputArea: FrameLayout
    private lateinit var hintText: TextView
    private lateinit var customAlertTextView: TextView
    private lateinit var topBarContainer: View
    private lateinit var centerHintContainer: LinearLayout
    private lateinit var backButton: ImageButton
    // *** НОВОЕ: Ссылка на иконку-подсказку ***
    private lateinit var hintIcon: ImageView


    // Game state
    private lateinit var handler: Handler
    private var currentPhase: GamePhase = GamePhase.INITIAL_HOLD
    private var isGameActive = false
    private var isActionInProgress = false
    private var isShowingAlert = false
    // *** НОВОЕ: Переменная для отслеживания текущей иконки ***
    private var currentIconResId: Int = 0

    // Gesture anchors and timing
    private var anchorX = 0f
    private var anchorY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private val holdDuration = 3750L
    private val warningDuration = 2000L
    private val alertDuration = 3000L
    private val moveThreshold = 30f

    // Progress
    private var currentProgress = 0
    private val progressPerPhase = 20

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_ladder_game)
        handler = Handler(Looper.getMainLooper())
        setupViews()
        setupListeners()
        animateUIAppearance()
        resetGame()
    }

    private fun setupViews() {
        progressBar = findViewById(R.id.gameProgressBar)
        touchInputArea = findViewById(R.id.touchInputArea)
        hintText = findViewById(R.id.gestureHintText)
        customAlertTextView = findViewById(R.id.customAlertTextView)
        findViewById<TextView>(R.id.timerTextView).text = "Steps"
        topBarContainer = findViewById(R.id.topBarContainer)
        centerHintContainer = findViewById(R.id.centerHintContainer)
        backButton = findViewById(R.id.backButton)
        // *** НОВОЕ: Инициализация иконки ***
        hintIcon = findViewById(R.id.gestureHintIcon)

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.ladderGameScene)
        ) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupListeners() {
        backButton.setOnClickListener { finish() }
        touchInputArea.setOnTouchListener { _, event ->
            handleGameTouch(event)
            true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleGameTouch(event: MotionEvent) {
        if (isShowingAlert) return

        lastX = event.x
        lastY = event.y

        if (!isGameActive && event.action != MotionEvent.ACTION_DOWN) return

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                handler.removeCallbacksAndMessages(null)
                isGameActive = true
                anchorX = lastX
                anchorY = lastY

                if (currentPhase in listOf(
                        GamePhase.INITIAL_HOLD,
                        GamePhase.HOLD,
                        GamePhase.HOLD_1,
                        GamePhase.HOLD_2
                    )) {
                    isActionInProgress = true
                    updateHintAnimated("Holding...")
                    anchorX = lastX
                    anchorY = lastY
                    handler.postDelayed({ onActionSuccess() }, holdDuration)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isActionInProgress) return
                val dx = lastX - anchorX
                val dy = lastY - anchorY
                when (currentPhase) {
                    GamePhase.INITIAL_HOLD,
                    GamePhase.HOLD,
                    GamePhase.HOLD_1,
                    GamePhase.HOLD_2 -> {
                        if (abs(dx) > moveThreshold || abs(dy) > moveThreshold) {
                            endGame(false)
                        }
                    }
                    GamePhase.SWIPE_DOWN -> {
                        if (dy > moveThreshold) {
                            anchorX = lastX
                            anchorY = lastY
                            onActionSuccess()
                        }
                    }
                    GamePhase.SWIPE_UP -> {
                        if (-dy > moveThreshold) {
                            anchorX = lastX
                            anchorY = lastY
                            onActionSuccess()
                        }
                    }
                    else -> {}
                }
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacksAndMessages(null)
                if (isActionInProgress) {
                    endGame(false)
                }
            }
        }
    }

    private fun onActionSuccess() {
        isActionInProgress = false
        handler.removeCallbacksAndMessages(null)

        currentProgress += progressPerPhase
        progressBar.progress = currentProgress

        if (currentProgress >= 100) {
            endGame(true)
            return
        }

        val nextPhase = when (currentPhase) {
            GamePhase.INITIAL_HOLD -> GamePhase.HOLD
            GamePhase.HOLD -> GamePhase.SWIPE_DOWN
            GamePhase.SWIPE_DOWN -> GamePhase.PREPARE_HOLD_1
            GamePhase.PREPARE_HOLD_1 -> GamePhase.HOLD_1
            GamePhase.HOLD_1 -> GamePhase.SWIPE_UP
            GamePhase.SWIPE_UP -> GamePhase.PREPARE_HOLD_2
            GamePhase.PREPARE_HOLD_2 -> GamePhase.HOLD_2
            else -> GamePhase.FAIL
        }

        if (nextPhase == GamePhase.PREPARE_HOLD_1 || nextPhase == GamePhase.PREPARE_HOLD_2) {
            startWarningPhase(nextPhase)
        } else {
            currentPhase = nextPhase
            updateHintAnimated(currentPhase.instruction)
            isActionInProgress = true

            if (currentPhase in listOf(
                    GamePhase.HOLD,
                    GamePhase.HOLD_1,
                    GamePhase.HOLD_2
                )) {
                anchorX = lastX
                anchorY = lastY
                handler.postDelayed({ onActionSuccess() }, holdDuration)
            }
        }
    }

    private fun startWarningPhase(nextPhase: GamePhase) {
        currentPhase = nextPhase
        updateHintAnimated(currentPhase.instruction)
        isActionInProgress = true

        handler.postDelayed({
            if (isGameActive) {
                anchorX = lastX
                anchorY = lastY
                currentPhase = when (nextPhase) {
                    GamePhase.PREPARE_HOLD_1 -> GamePhase.HOLD_1
                    GamePhase.PREPARE_HOLD_2 -> GamePhase.HOLD_2
                    else -> GamePhase.FAIL
                }
                updateHintAnimated(currentPhase.instruction)
                isActionInProgress = true
                handler.postDelayed({ onActionSuccess() }, holdDuration)
            }
        }, warningDuration)
    }

    private fun endGame(isWin: Boolean) {
        if (!isGameActive) return
        isGameActive = false
        isActionInProgress = false
        isShowingAlert = true
        handler.removeCallbacksAndMessages(null)

        val alertMessage = if (isWin) GamePhase.WIN.instruction else GamePhase.FAIL.instruction
        val alertWin = isWin

        resetGame()
        showCustomAlert(alertMessage, alertWin)

        handler.postDelayed({ isShowingAlert = false }, alertDuration)
    }

    private fun resetGame() {
        isGameActive = false
        isActionInProgress = false
        currentProgress = 0
        progressBar.progress = 0
        currentPhase = GamePhase.INITIAL_HOLD
        updateHintAnimated(currentPhase.instruction)
    }

    // *** ИЗМЕНЕНО: Эта функция теперь вызывает анимацию для иконки и текста ***
    private fun updateHintAnimated(newText: String) {
        // Определяем нужную иконку для вертикальных свайпов
        val newIconRes = when (currentPhase) {
            GamePhase.SWIPE_UP, GamePhase.SWIPE_DOWN -> R.drawable.ic_swipe_vertical
            else -> R.drawable.ic_touch_app
        }
        animateIconChange(newIconRes)

        if (hintText.text == newText) return
        animateHintTextChange(newText)
    }

    // *** НОВАЯ ФУНКЦИЯ: Анимированная смена иконки ***
    private fun animateIconChange(newIconRes: Int) {
        if (currentIconResId == newIconRes) return

        currentIconResId = newIconRes

        val fadeOut = ObjectAnimator.ofFloat(hintIcon, "alpha", 1f, 0f).apply {
            duration = 250
            interpolator = AccelerateInterpolator()
        }

        fadeOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                hintIcon.setImageResource(newIconRes)
                ObjectAnimator.ofFloat(hintIcon, "alpha", 0f, 1f).apply {
                    duration = 250
                    interpolator = DecelerateInterpolator()
                    start()
                }
            }
        })
        fadeOut.start()
    }


    private fun animateUIAppearance() {
        topBarContainer.translationY = -200f
        centerHintContainer.alpha = 0f
        touchInputArea.alpha = 0f
        backButton.alpha = 0f
        topBarContainer.animate()
            .translationY(0f)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .setDuration(800)
            .start()
        backButton.animate()
            .alpha(1f)
            .setDuration(600)
            .setStartDelay(200)
            .start()
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(centerHintContainer, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(touchInputArea, "alpha", 0f, 1f)
            )
            interpolator = AccelerateInterpolator()
            startDelay = 400
            duration = 600
            start()
        }
    }

    private fun animateHintTextChange(newText: String) {
        ObjectAnimator.ofFloat(hintText, "alpha", 1f, 0f).apply {
            duration = 300
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    hintText.text = newText
                    ObjectAnimator.ofFloat(hintText, "alpha", 0f, 1f)
                        .setDuration(300)
                        .start()
                }
            })
            start()
        }
    }

    private fun showCustomAlert(message: String, isWin: Boolean) {
        customAlertTextView.text = message
        customAlertTextView.setTextColor(
            if (isWin) Color.parseColor("#99FF99") else Color.parseColor("#FF9999")
        )
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
            startDelay = alertDuration - 400
        }
        AnimatorSet().apply {
            playSequentially(fadeIn, fadeOut)
            start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}