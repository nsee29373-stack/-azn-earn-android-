package ir.azn.earn

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import ir.tapsell.plus.AdRequestCallback
import ir.tapsell.plus.AdShowListener
import ir.tapsell.plus.TapsellPlus
import ir.tapsell.plus.TapsellPlusBannerType
import ir.tapsell.plus.TapsellPlusInitListener
import ir.tapsell.plus.model.AdNetworkError
import ir.tapsell.plus.model.AdNetworks
import ir.tapsell.plus.model.TapsellPlusAdModel
import ir.tapsell.plus.model.TapsellPlusErrorModel

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AZN_EARN"

        private const val TAPSELL_APP_KEY =
            "robqfqdmedelfokdmefdltccslflqkgthqgsiaajjjkkhtgpasmmcgggcfafbchcntmqho"

        private const val REWARDED_ZONE_ID =
            "6aec64d1a2434702499bd0dd"

        private const val BANNER_ZONE_ID =
            "6aec65d1a2434702499bd0de"
    }

    private lateinit var webView: WebView
    private lateinit var bannerContainer: FrameLayout

    private var rewardedResponseId: String? = null
    private var bannerResponseId: String? = null
    private var rewardedAdLoading = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            addJavascriptInterface(AndroidAds(), "AndroidAds")
        }

        bannerContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                50.dp()
            )
        }

        root.addView(
            webView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        root.addView(bannerContainer)
        setContentView(root)

        webView.loadUrl("file:///android_asset/index.html")

        initializeTapsell()
    }

    private fun initializeTapsell() {
        TapsellPlus.initialize(
            this,
            TAPSELL_APP_KEY,
            object : TapsellPlusInitListener {

                override fun onInitializeSuccess(
                    adNetworks: AdNetworks
                ) {
                    Log.d(TAG, "Tapsell initialized")

                    runOnUiThread {
                        requestBannerAd()
                        requestRewardedAd()
                    }
                }

                override fun onInitializeFailed(
                    adNetworks: AdNetworks,
                    adNetworkError: AdNetworkError
                ) {
                    Log.e(
                        TAG,
                        "Initialization failed: ${adNetworkError.errorMessage}"
                    )
                    notifyWebViewStatus(
                        "اتصال به شبکه تبلیغات ناموفق بود."
                    )
                }
            }
        )
    }

    private fun requestBannerAd() {
        TapsellPlus.requestStandardBannerAd(
            this,
            BANNER_ZONE_ID,
            TapsellPlusBannerType.BANNER_320x50,
            object : AdRequestCallback() {

                override fun response(adModel: TapsellPlusAdModel) {
                    super.response(adModel)
                    bannerResponseId = adModel.responseId

                    runOnUiThread {
                        showBannerAd()
                    }
                }

                override fun error(message: String) {
                    Log.e(TAG, "Banner error: $message")
                }
            }
        )
    }

    private fun showBannerAd() {
        val responseId = bannerResponseId ?: return

        TapsellPlus.showStandardBannerAd(
            this,
            responseId,
            bannerContainer,
            object : AdShowListener() {

                override fun onOpened(adModel: TapsellPlusAdModel) {
                    super.onOpened(adModel)
                    Log.d(TAG, "Banner opened")
                }

                override fun onError(errorModel: TapsellPlusErrorModel) {
                    super.onError(errorModel)
                    Log.e(TAG, "Banner error: $errorModel")
                }
            }
        )
    }

    private fun requestRewardedAd() {
        if (rewardedAdLoading || rewardedResponseId != null) return

        rewardedAdLoading = true

        TapsellPlus.requestRewardedVideoAd(
            this,
            REWARDED_ZONE_ID,
            object : AdRequestCallback() {

                override fun response(adModel: TapsellPlusAdModel) {
                    super.response(adModel)

                    rewardedAdLoading = false
                    rewardedResponseId = adModel.responseId

                    Log.d(TAG, "Rewarded ad ready")
                    notifyWebView("onRewardedAdReady")
                }

                override fun error(message: String) {
                    rewardedAdLoading = false
                    Log.e(TAG, "Rewarded request error: $message")

                    notifyWebView("onRewardedAdError")
                }
            }
        )
    }

    private fun showRewardedAd() {
        val responseId = rewardedResponseId

        if (responseId == null) {
            notifyWebView("onRewardedAdNotReady")
            requestRewardedAd()
            return
        }

        rewardedResponseId = null

        TapsellPlus.showRewardedVideoAd(
            this,
            responseId,
            object : AdShowListener() {

                override fun onOpened(adModel: TapsellPlusAdModel) {
                    super.onOpened(adModel)
                    notifyWebView("onRewardedAdOpened")
                }

                override fun onClosed(adModel: TapsellPlusAdModel) {
                    super.onClosed(adModel)

                    notifyWebView("onRewardedAdClosed")
                    requestRewardedAd()
                }

                override fun onRewarded(adModel: TapsellPlusAdModel) {
                    super.onRewarded(adModel)

                    Log.d(TAG, "Reward callback received")
                    notifyWebView("onAdRewarded")
                }

                override fun onError(errorModel: TapsellPlusErrorModel) {
                    super.onError(errorModel)

                    Log.e(TAG, "Rewarded show error: $errorModel")
                    notifyWebView("onRewardedAdError")
                    requestRewardedAd()
                }
            }
        )
    }

    private fun notifyWebView(functionName: String) {
        if (!::webView.isInitialized) return

        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                webView.evaluateJavascript(
                    "if(typeof window.$functionName === 'function'){" +
                        "window.$functionName();" +
                        "}",
                    null
                )
            }
        }
    }

    private fun notifyWebViewStatus(message: String) {
        if (!::webView.isInitialized) return

        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                val safeMessage = org.json.JSONObject.quote(message)

                webView.evaluateJavascript(
                    "if(typeof window.onNativeAdStatus === 'function'){" +
                        "window.onNativeAdStatus($safeMessage);" +
                        "}",
                    null
                )
            }
        }
    }

    inner class AndroidAds {

        @JavascriptInterface
        fun showRewardedAd() {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    this@MainActivity.showRewardedAd()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("AndroidAds")
            webView.stopLoading()
            webView.destroy()
        }

        super.onDestroy()
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}
