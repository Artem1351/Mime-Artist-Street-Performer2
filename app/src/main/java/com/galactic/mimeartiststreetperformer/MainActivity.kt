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

class MainActivity : AppCompatActivity() {

    private val portalEndpoint =
        "https://mimeartiststreet.pics/mimeartist-privacy"

    private var portalView: WebView? = null
    private var portalShown = false
    private var nativeFallbackDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!isNetworkAvailable(this)) {
            openNativeStage()
            return
        }

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

        if (savedInstanceState != null) {
            revealPortalLayer()
            portalView?.restoreState(savedInstanceState)
            return
        }

        revealPortalLayer()
        portalView?.loadUrl(portalEndpoint)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        portalView?.saveState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
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
            return false
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            // Если это главный фрейм и, например, 404/410/500 — уходим в натив (ничего не открываем)
            if (request.isForMainFrame) {
                retreatToNativeStage("HTTP ${errorResponse.statusCode}")
            }
        }

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

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            retreatToNativeStage("SSL error")
        }
    }

    private fun retreatToNativeStage(reason: String? = null) {
        if (nativeFallbackDone) return
        nativeFallbackDone = true
        portalShown = false

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
