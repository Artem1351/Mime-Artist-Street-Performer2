package com.galactic.mimeartiststreetperformer

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.webkit.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.concurrent.thread
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    // ===== Настройки "веб-портала" (WebView) =====
    private val portalEndpoint =
        "https://zapasapps.com/api/data?app_key=2gsebk04as99270g3qpl97pk3th25xok" // при необходимости замените

    private var portalView: WebView? = null
    private var portalShown = false
    private var nativeFallbackDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Если нет интернета — сразу открываем нативный экран
        if (!isNetworkAvailable(this)) {
            openNativeStage()
            return
        }

        // Аппаратная кнопка "Назад": сначала пытаемся шагать назад по истории WebView
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (portalShown && portalView?.canGoBack() == true) {
                    portalView?.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Если есть сохранённое состояние — не делаем пред-проверку и восстанавливаем WebView
        if (savedInstanceState != null) {
            revealPortalLayer()
            portalView?.restoreState(savedInstanceState)
            return
        }

        // Пред-проверка доступности URL: считаем ошибкой коды >= 400
        thread {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(portalEndpoint)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    connectTimeout = 3000
                    readTimeout = 3000
                    connect()
                }
                val code = connection.responseCode
                if (code < 400) {
                    runOnUiThread {
                        revealPortalLayer()
                        portalView?.loadUrl(portalEndpoint)
                    }
                } else {
                    runOnUiThread { retreatToNativeStage("HTTP $code") }
                }
            } catch (_: Exception) {
                runOnUiThread { retreatToNativeStage("Preflight failed") }
            } finally {
                try { connection?.disconnect() } catch (_: Exception) {}
            }
        }
    }

    // Сохраняем состояние WebView, чтобы при поворотах/перезапусках страница не перезагружалась
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        portalView?.saveState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Аккуратно уничтожим WebView, если он показан
        try {
            portalView?.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                (parent as? ViewGroup)?.removeView(this)
                destroy()
            }
        } catch (_: Exception) { }
        portalView = null
    }

    // ====== Блок работы с WebView (уникализированные имена) ======

    private fun revealPortalLayer() {
        val container = FrameLayout(this)

        if (portalView == null) {
            portalView = WebView(this).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true

                webViewClient = MimeStageGateClient()
            }
        }

        // Отцепим от прошлого родителя, если есть
        (portalView?.parent as? ViewGroup)?.removeView(portalView)

        portalView?.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        container.addView(portalView)

        // Системные отступы для контейнера
        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom)
            insets
        }

        setContentView(container)
        portalShown = true
    }

    private inner class MimeStageGateClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val clicked = request?.url?.toString().orEmpty()
            // Внешние схемы — наружу
            if (clicked.startsWith("tel:") ||
                clicked.startsWith("mailto:") ||
                clicked.startsWith("tg:") ||
                clicked.startsWith("sms:")
            ) {
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(clicked)))
                    true
                } catch (_: Exception) {
                    true
                }
            }
            // http/https обрабатывает сам WebView
            return false
        }

        // HTTP ошибки (403/404/5xx) только для главной рамки
        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            if (request.isForMainFrame) {
                retreatToNativeStage("HTTP ${errorResponse.statusCode}")
            }
        }

        // Сетевые ошибки (API 23+)
        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            super.onReceivedError(view, request, error)
            if (request.isForMainFrame) {
                retreatToNativeStage("WEB ${error.description}")
            }
        }

        // Сетевые ошибки (старый колбэк для API < 23)
        @Suppress("DEPRECATION")
        override fun onReceivedError(
            view: WebView?,
            errorCode: Int,
            description: String?,
            failingUrl: String?
        ) {
            super.onReceivedError(view, errorCode, description, failingUrl)
            retreatToNativeStage("WEB ${description ?: errorCode}")
        }

        // SSL ошибки — не продолжаем
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            retreatToNativeStage("SSL error")
        }
    }

    private fun retreatToNativeStage(reason: String? = null) {
        if (nativeFallbackDone) return
        nativeFallbackDone = true
        portalShown = false

        // Корректно "сворачиваем" WebView
        try {
            portalView?.let { wv ->
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.clearHistory()
                wv.removeAllViews()
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.destroy()
            }
        } catch (_: Exception) { }
        portalView = null

        openNativeStage()
    }

    // ====== Нативный экран (ваш исходный UI) ======

    private fun openNativeStage() {
        if (isFinishing || isDestroyed) return

        setContentView(R.layout.activity_main)

        // Системные отступы на корневой вью вашей разметки
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.streetPerformerScene)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Анимация булавки
        findViewById<ImageView>(R.id.animatedPinImage)?.let { startPinAnimation(it) }

        // Плавное появление блока кнопок
        findViewById<View>(R.id.mimeArtistActionGroup)?.let { animateButtonsIn(it) }

        // Переходы в ваши экраны
        findViewById<Button>(R.id.buttonTugboat)?.setOnClickListener {
            startActivity(Intent(this, TugboatActivity::class.java))
        }
        findViewById<Button>(R.id.buttonLadder)?.setOnClickListener {
            startActivity(Intent(this, LadderGameActivity::class.java))
        }
        findViewById<Button>(R.id.buttonInvisibleWall)?.setOnClickListener {
            startActivity(Intent(this, InvisibleWallActivity::class.java))
        }
    }

    // ====== Вспомогательные методы ======

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val cap = cm.getNetworkCapabilities(net) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun startPinAnimation(targetView: ImageView) {
        val randomRotation = (-40..40).random().toFloat()
        val randomDuration = (800..1500).random().toLong()
        val animator = ObjectAnimator.ofFloat(targetView, "rotation", randomRotation).apply {
            duration = randomDuration
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
                override fun onAnimationEnd(animation: Animator) {
                    startPinAnimation(targetView)
                }
            })
        }
        animator.start()
    }

    private fun animateButtonsIn(buttonContainer: View) {
        buttonContainer.alpha = 0f
        buttonContainer.translationY = 150f
        buttonContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .setDuration(800)
            .start()
    }
}
