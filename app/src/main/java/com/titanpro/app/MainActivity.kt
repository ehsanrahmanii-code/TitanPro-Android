package com.titanpro.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var splashOverlay: View

    private val handler = Handler(Looper.getMainLooper())
    private var serverReady = false
    private var retryCount = 0
    private val maxRetries = 40          // ~40 seconds
    private val serverUrl = "http://127.0.0.1:8080"

    companion object {
        private const val REQ_STORAGE = 1001
        private const val REQ_NOTIFICATION = 1002
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Immersive dark status bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        webView = findViewById(R.id.webview)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        splashOverlay = findViewById(R.id.splashOverlay)

        setupWebView()
        checkPermissionsAndStart()
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(true)
            builtInZoomControls = false
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = false
            // Important for the heavy dashboard
            setRenderPriority(WebSettings.RenderPriority.HIGH)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (serverReady) {
                    hideSplash()
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true && !serverReady) {
                    // Server not ready yet – keep retrying
                    scheduleRetry()
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                // Keep everything inside the WebView except external links
                if (url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost")) {
                    return false
                }
                // Open external links in browser
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {}
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }
        }
    }

    private fun checkPermissionsAndStart() {
        statusText.text = "در حال بررسی مجوزها…"

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_NOTIFICATION
                )
            }
        }

        // Storage – critical for TITAN MMM folder
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                AlertDialog.Builder(this)
                    .setTitle("مجوز دسترسی به حافظه")
                    .setMessage(
                        "TITAN تمام داده‌ها، کش و لاگ را فقط داخل پوشه\n" +
                        "Internal Storage / MMM\n" +
                        "ذخیره می‌کند. لطفاً دسترسی «مدیریت همه فایل‌ها» را فعال کنید."
                    )
                    .setPositiveButton("باز کردن تنظیمات") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:$packageName")
                            startActivity(intent)
                        } catch (_: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            startActivity(intent)
                        }
                    }
                    .setNegativeButton("بعداً") { _, _ ->
                        // Continue anyway – may fail later
                        startServerService()
                    }
                    .setCancelable(false)
                    .show()
                // We will resume in onResume after user returns
                return
            }
        } else {
            // Android 10 and below
            val needed = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (needed.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_STORAGE)
                return
            }
        }

        startServerService()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STORAGE) {
            startServerService()
        }
    }

    override fun onResume() {
        super.onResume()
        // User may have granted MANAGE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager() && !serverReady) {
                startServerService()
            }
        }
    }

    private fun startServerService() {
        statusText.text = "در حال راه‌اندازی موتور TITAN…"
        val intent = Intent(this, TitanServerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        // Begin polling the local server
        scheduleRetry()
    }

    private fun scheduleRetry() {
        if (serverReady || retryCount >= maxRetries) {
            if (!serverReady) {
                statusText.text = "خطا در راه‌اندازی سرور.\nلطفاً اپ را ببندید و دوباره باز کنید."
                Toast.makeText(this, "سرور محلی بالا نیامد", Toast.LENGTH_LONG).show()
            }
            return
        }
        retryCount++
        statusText.text = "در انتظار سرور محلی… ($retryCount/$maxRetries)"
        handler.postDelayed({
            tryLoadServer()
        }, 1000)
    }

    private fun tryLoadServer() {
        // Simple HEAD request via WebView load – if it fails, onReceivedError will retry
        webView.loadUrl(serverUrl)
        // Also check with a tiny timeout
        handler.postDelayed({
            if (!serverReady) {
                // Force another attempt
                scheduleRetry()
            }
        }, 1500)
    }

    private fun hideSplash() {
        if (serverReady) return
        serverReady = true
        splashOverlay.animate()
            .alpha(0f)
            .setDuration(400)
            .withEndAction {
                splashOverlay.visibility = View.GONE
            }
            .start()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            // Minimize instead of killing the server
            moveTaskToBack(true)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        // Do NOT stop the service here – user may want it running in background
        super.onDestroy()
    }
}
