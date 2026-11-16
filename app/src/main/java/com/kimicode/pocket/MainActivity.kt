package com.kimicode.pocket

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.kimicode.pocket.databinding.ActivityPairingBinding
import com.kimicode.pocket.databinding.ActivityRemoteBinding
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var pairingStore: PairingStore
    private var pairingBinding: ActivityPairingBinding? = null
    private var remoteBinding: ActivityRemoteBinding? = null
    private var activeRemoteUrl: String? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var loginDialog: Dialog? = null
    private var loginWebView: WebView? = null
    private var isClosingLogin = false
    private var connectionFailed = false
    private var qrDecodeDialog: AlertDialog? = null
    private val qrDecodeExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let(::confirmPairing)
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let(::decodeQrFromGallery)
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pairingStore = PairingStore(this)
        WebView.setWebContentsDebuggingEnabled(false)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (loginDialog?.isShowing == true) {
                    completeLoginAndReturn()
                    return
                }
                val webView = remoteBinding?.remoteWebView
                if (webView?.canGoBack() == true) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        val savedUrl = pairingStore.getUrl()
        if (savedUrl != null) {
            showRemote(savedUrl)
        } else {
            showPairing()
        }
    }

    override fun onResume() {
        super.onResume()
        remoteBinding?.remoteWebView?.onResume()
        loginWebView?.onResume()
    }

    override fun onPause() {
        loginWebView?.onPause()
        remoteBinding?.remoteWebView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        qrDecodeDialog?.dismiss()
        qrDecodeDialog = null
        qrDecodeExecutor.shutdownNow()
        destroyRemoteView()
        super.onDestroy()
    }

    private fun showPairing() {
        destroyRemoteView()
        activeRemoteUrl = null
        val binding = ActivityPairingBinding.inflate(layoutInflater)
        pairingBinding = binding
        setContentView(binding.root)
        binding.scanButton.setOnClickListener { startScan() }
        binding.galleryButton.setOnClickListener { startGalleryScan() }
        binding.pasteButton.setOnClickListener { showPasteDialog() }
    }

    private fun startScan() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(listOf("QR_CODE"))
            setPrompt("将桌面端二维码放入取景框")
            setBeepEnabled(false)
            setBarcodeImageEnabled(false)
            setOrientationLocked(true)
        }
        barcodeLauncher.launch(options)
    }

    private fun startGalleryScan() {
        val request = PickVisualMediaRequest.Builder()
            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
            .build()
        galleryLauncher.launch(request)
    }

    private fun decodeQrFromGallery(uri: Uri) {
        if (qrDecodeDialog?.isShowing == true) return
        qrDecodeDialog = AlertDialog.Builder(this)
            .setTitle("正在识别二维码")
            .setMessage("正在读取所选图片，请稍候…")
            .setCancelable(false)
            .create()
            .also { it.show() }

        qrDecodeExecutor.execute {
            val result = try {
                GalleryQrDecoder.decode(contentResolver, uri)
            } catch (_: Exception) {
                null
            } catch (_: OutOfMemoryError) {
                null
            }
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                qrDecodeDialog?.dismiss()
                qrDecodeDialog = null
                if (result.isNullOrBlank()) {
                    AlertDialog.Builder(this)
                        .setTitle("没有识别到二维码")
                        .setMessage("请选择清晰、完整且未被裁切的桌面端二维码图片，也可以改用相机扫描或粘贴配对链接。")
                        .setPositiveButton("知道了", null)
                        .show()
                } else {
                    confirmPairing(result)
                }
            }
        }
    }

    private fun showPasteDialog() {
        val input = EditText(this).apply {
            hint = "https://…"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(false)
            minLines = 2
            maxLines = 4
        }
        val container = FrameLayout(this).apply {
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle("粘贴配对链接")
            .setMessage("请粘贴 Kimi Code 桌面端生成的远程控制链接。")
            .setView(container)
            .setNegativeButton("取消", null)
            .setPositiveButton("下一步") { _, _ -> confirmPairing(input.text.toString()) }
            .show()
    }

    private fun confirmPairing(rawUrl: String) {
        val url = PairingValidator.normalize(rawUrl)
        val host = PairingValidator.displayHost(rawUrl)
        if (url == null || host == null) {
            AlertDialog.Builder(this)
                .setTitle("无法识别配对信息")
                .setMessage("请扫描桌面端二维码，或输入完整的 HTTPS 远程控制链接。")
                .setPositiveButton("知道了", null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("连接这台桌面端？")
            .setMessage("连接地址：$host\n\n请确认二维码或链接来自你的 Kimi Code Desktop。")
            .setNegativeButton("取消", null)
            .setPositiveButton("保存并连接") { _, _ ->
                pairingStore.saveUrl(url)
                showRemote(url)
            }
            .show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showRemote(url: String) {
        destroyRemoteView()
        pairingBinding = null
        activeRemoteUrl = url
        connectionFailed = false
        val binding = ActivityRemoteBinding.inflate(layoutInflater)
        remoteBinding = binding
        setContentView(binding.root)

        binding.retryButton.setOnClickListener { reloadRemote() }

        val webView = binding.remoteWebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            setSupportZoom(false)
            builtInZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        }
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val uri = request.url
                if (uri.scheme.equals("https", ignoreCase = true)) return false
                if (uri.scheme.equals("http", ignoreCase = true)) {
                    Toast.makeText(this@MainActivity, "已拦截不安全的 HTTP 链接", Toast.LENGTH_SHORT).show()
                    return true
                }
                return openExternal(uri)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                connectionFailed = false
                binding.errorView.visibility = View.GONE
                binding.loadingProgress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String) {
                binding.loadingProgress.visibility = View.GONE
                if (connectionFailed) return
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    showConnectionError("页面加载失败。请确认电脑端在线，然后重试。")
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                    showConnectionError("远程服务返回 ${errorResponse.statusCode}。请检查电脑端状态，或点「重新连接」重试。")
                }
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                handler.cancel()
                showConnectionError("安全证书校验失败，已停止连接。请确认配对链接来自官方桌面端。")
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message
            ): Boolean {
                openLoginWindow(resultMsg)
                return true
            }

            override fun onCloseWindow(window: WebView) {
                completeLoginAndReturn()
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress >= 100) {
                    binding.loadingProgress.visibility = View.GONE
                }
            }

            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                return try {
                    fileChooserLauncher.launch(fileChooserParams.createIntent())
                    true
                } catch (_: ActivityNotFoundException) {
                    filePathCallback = null
                    Toast.makeText(this@MainActivity, "没有可用的文件选择器", Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }

        webView.loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openLoginWindow(resultMsg: android.os.Message) {
        closeLoginWindow()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.WHITE)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (60 * resources.displayMetrics.density).toInt()
            )
            val padding = (18 * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
        }
        val title = TextView(this).apply {
            text = "登录 Kimi"
            textSize = 18f
            setTextColor(android.graphics.Color.rgb(23, 25, 31))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val doneButton = TextView(this).apply {
            text = "完成并返回"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.rgb(49, 87, 213))
            setPadding(24, 24, 0, 24)
            setOnClickListener { completeLoginAndReturn() }
        }
        topBar.addView(title)
        topBar.addView(doneButton)
        root.addView(topBar)

        val childWebView = WebView(this)
        childWebView.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        childWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = true
            // 登录页依赖移动端 viewport；不开启时会按桌面宽度排版后被挤进手机屏幕。
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = false
            displayZoomControls = false
        }
        childWebView.setBackgroundColor(android.graphics.Color.WHITE)
        CookieManager.getInstance().setAcceptThirdPartyCookies(childWebView, true)
        childWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val scheme = request.url.scheme.orEmpty()
                if (scheme.equals("https", ignoreCase = true)) return false
                if (scheme.equals("http", ignoreCase = true)) {
                    Toast.makeText(this@MainActivity, "已拦截不安全的 HTTP 链接", Toast.LENGTH_SHORT).show()
                    return true
                }
                // 某些 OAuth 流程以自定义 scheme 作为完成信号。
                completeLoginAndReturn()
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                checkLoginCompletion(view)
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                handler.cancel()
                Toast.makeText(this@MainActivity, "登录页安全证书校验失败", Toast.LENGTH_SHORT).show()
            }
        }
        childWebView.webChromeClient = object : WebChromeClient() {
            override fun onCloseWindow(window: WebView) {
                completeLoginAndReturn()
            }
        }
        root.addView(childWebView)

        val dialog = Dialog(this, android.R.style.Theme_Material_Light_NoActionBar)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(false)
        loginDialog = dialog
        loginWebView = childWebView

        dialog.show()
        dialog.window?.apply {
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            statusBarColor = android.graphics.Color.WHITE
            navigationBarColor = android.graphics.Color.WHITE
            decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }

        // 弹窗是全屏的，标题栏与「完成并返回」会压到状态栏图标下面，这里按系统栏高度让出空间。
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // 等弹窗完成布局后再让页面开始加载，避免登录页按 0×0 尺寸排版。
        childWebView.post {
            if (loginWebView !== childWebView || !dialog.isShowing) return@post
            val transport = resultMsg.obj as WebView.WebViewTransport
            transport.webView = childWebView
            resultMsg.sendToTarget()
        }
    }

    private fun checkLoginCompletion(view: WebView) {
        view.evaluateJavascript(
            "(function(){return document.body ? document.body.innerText : '';})();"
        ) { text ->
            if (LoginFlow.isCompletionPageText(text)) {
                view.post { completeLoginAndReturn() }
            }
        }
    }

    private fun completeLoginAndReturn() {
        if (isClosingLogin) return
        CookieManager.getInstance().flush()
        closeLoginWindow()
        val binding = remoteBinding ?: return
        val url = activeRemoteUrl ?: return
        connectionFailed = false
        binding.errorView.visibility = View.GONE
        binding.loadingProgress.visibility = View.VISIBLE
        binding.remoteWebView.clearHistory()
        binding.remoteWebView.loadUrl(url)
    }

    private fun closeLoginWindow() {
        if (isClosingLogin) return
        isClosingLogin = true
        loginDialog?.setOnDismissListener(null)
        loginDialog?.dismiss()
        loginDialog = null
        loginWebView?.apply {
            stopLoading()
            webChromeClient = null
            webViewClient = WebViewClient()
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
        loginWebView = null
        isClosingLogin = false
    }

    private fun openExternal(uri: Uri): Boolean {
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: ActivityNotFoundException) {
            true
        }
    }

    private fun reloadRemote() {
        val binding = remoteBinding ?: return
        val url = activeRemoteUrl ?: return
        connectionFailed = false
        binding.errorView.visibility = View.GONE
        binding.loadingProgress.visibility = View.VISIBLE
        if (binding.remoteWebView.url.isNullOrBlank()) {
            binding.remoteWebView.loadUrl(url)
        } else {
            binding.remoteWebView.reload()
        }
    }

    private fun showConnectionError(message: String) {
        val binding = remoteBinding ?: return
        connectionFailed = true
        binding.loadingProgress.visibility = View.GONE
        binding.errorMessage.text = message
        binding.errorView.visibility = View.VISIBLE
    }

    private fun destroyRemoteView() {
        closeLoginWindow()
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        remoteBinding?.remoteWebView?.apply {
            stopLoading()
            webChromeClient = null
            webViewClient = WebViewClient()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        remoteBinding = null
    }

}
