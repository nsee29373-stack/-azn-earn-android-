
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
        private const val TAPSELL_APP_KEY = "robqfqdmedelfokdmefdltccslflqkgthqgsiaajjjkkhtgpasmmcgggcfafbchcntmqho"
        private const val REWARDED_ZONE_ID = "6aec64d1a2434702499bd0dd"
        private const val BANNER_ZONE_ID = "6aec65d1a2434702499bd0de"
        private const val PREFS = "azn_earn_data"
        private const val COMPLETED_ADS = "completed_rewarded_ads"
        private const val ACKED_REWARDS = "acked_rewarded_ads"
    }

    private lateinit var webView: WebView
    private lateinit var bannerContainer: FrameLayout

    private var rewardedResponseId: String? = null
    private var bannerResponseId: String? = null
    private var rewardedAdLoading = false
    private var rewardedAdShowing = false
    private var tapsellInitialized = false
    @Volatile private var rewardReceived = false
    @Volatile private var pageLoaded = false

    private val pendingJavaScript = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeRewardAcknowledgement()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    pageLoaded = true

                    val messages = synchronized(pendingJavaScript) {
                        pendingJavaScript.toList().also { pendingJavaScript.clear() }
                    }
                    messages.forEach { evaluateJavascript(it, null) }

                    notifyRewardedCount(getCompletedAdsCount())
                    deliverPendingRewards()

                    if (tapsellInitialized) {
                        notifyWebViewStatus("اتصال به تپسل برقرار شد.")
                    }
                }
            }
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

    private fun initializeRewardAcknowledgement() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.contains(ACKED_REWARDS)) {
            prefs.edit()
                .putInt(ACKED_REWARDS, getCompletedAdsCount())
                .commit()
        }
    }

    private fun initializeTapsell() {
        if (tapsellInitialized) return

        TapsellPlus.initialize(
            this,
            TAPSELL_APP_KEY,
            object : TapsellPlusInitListener {
                override fun onInitializeSuccess(adNetworks: AdNetworks) {
                    tapsellInitialized = true
                    Log.d(TAG, "Tapsell initialized")
                    runOnUiThread {
                        notifyWebViewStatus("اتصال به تپسل برقرار شد.")
                        requestBannerAd()
                        requestRewardedAd()
                    }
                }

                override fun onInitializeFailed(
                    adNetworks: AdNetworks,
                    adNetworkError: AdNetworkError
                ) {
                    Log.e(TAG, "Initialization failed: ${adNetworkError.errorMessage}")
                    notifyWebViewStatus("خطا در اتصال تپسل: ${adNetworkError.errorMessage}")
                }
            }
        )
    }

    private fun requestBannerAd() {
        if (!tapsellInitialized || bannerResponseId != null) return

        TapsellPlus.requestStandardBannerAd(
            this,
            BANNER_ZONE_ID,
            TapsellPlusBannerType.BANNER_320x50,
            object : AdRequestCallback() {
                override fun response(adModel: TapsellPlusAdModel) {
                    super.response(adModel)
                    bannerResponseId = adModel.responseId
                    Log.d(TAG, "Banner ready")
                    runOnUiThread { showBannerAd() }
                }

                override fun error(message: String) {
                    Log.e(TAG, "Banner request error: $message")
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
                    Log.e(TAG, "Banner show error: $errorModel")
                    bannerResponseId = null
                    requestBannerAd()
                }
            }
        )
    }

    private fun requestRewardedAd() {
        if (!tapsellInitialized || rewardedAdLoading ||
            rewardedResponseId != null || rewardedAdShowing
        ) return

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
                    notifyWebViewStatus("خطا در دریافت تبلیغ: $message")
                    notifyWebView("onRewardedAdError")
                }
            }
        )
    }

    private fun showRewardedAd() {
        if (rewardedAdShowing) return

        val responseId = rewardedResponseId
        if (responseId == null) {
            notifyWebView("onRewardedAdNotReady")
            requestRewardedAd()
            return
        }

        rewardedResponseId = null
        rewardedAdShowing = true
        rewardReceived = false

        TapsellPlus.showRewardedVideoAd(
            this,
            responseId,
            object : AdShowListener() {
                override fun onOpened(adModel: TapsellPlusAdModel) {
                    super.onOpened(adModel)
                    Log.d(TAG, "Rewarded ad opened")
                    notifyWebView("onRewardedAdOpened")
                }

                override fun onRewarded(adModel: TapsellPlusAdModel) {
                    super.onRewarded(adModel)

                    synchronized(this@MainActivity) {
                        if (rewardReceived) return
                        rewardReceived = true
                    }

                    val newCount = recordCompletedReward()
                    if (newCount > 0) {
                        Log.d(TAG, "Verified reward count: $newCount")
                        notifyRewardedCount(newCount)
                        notifyWebViewReward(newCount)
                    } else {
                        Log.e(TAG, "Could not persist rewarded ad count")
                        notifyWebViewStatus("ذخیره پاداش ناموفق بود؛ دوباره برنامه را بررسی کن.")
                    }
                }

                override fun onClosed(adModel: TapsellPlusAdModel) {
                    super.onClosed(adModel)
                    Log.d(TAG, "Ad closed. Reward: $rewardReceived")
                    rewardedAdShowing = false
                    notifyWebView("onRewardedAdClosed")
                    requestRewardedAd()
                }

                override fun onError(errorModel: TapsellPlusErrorModel) {
                    super.onError(errorModel)
                    rewardedAdShowing = false
                    Log.e(TAG, "Rewarded show error: $errorModel")
                    notifyWebViewStatus("خطای نمایش تبلیغ: $errorModel")
                    notifyWebView("onRewardedAdError")
                    requestRewardedAd()
                }
            }
        )
    }

    @Synchronized
    private fun recordCompletedReward(): Int {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val current = prefs.getInt(COMPLETED_ADS, 0)
        if (current == Int.MAX_VALUE) return -1

        val next = current + 1
        val saved = prefs.edit().putInt(COMPLETED_ADS, next).commit()
        return if (saved) next else -1
    }

    @Synchronized
    private fun getCompletedAdsCount(): Int {
        return getSharedPreferences(PREFS, MODE_PRIVATE)
            .getInt(COMPLETED_ADS, 0).coerceAtLeast(0)
    }

    @Synchronized
    private fun getAcknowledgedRewardCount(): Int {
        return getSharedPreferences(PREFS, MODE_PRIVATE)
            .getInt(ACKED_REWARDS, 0).coerceAtLeast(0)
    }

    private fun deliverPendingRewards() {
        val completed = getCompletedAdsCount()
        val acknowledged = getAcknowledgedRewardCount()
        if (completed > acknowledged) notifyWebViewReward(completed)
    }

    @Synchronized
    private fun acknowledgeReward(count: Int) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val completed = prefs.getInt(COMPLETED_ADS, 0)
        val acknowledged = prefs.getInt(ACKED_REWARDS, 0)

        if (count <= acknowledged || count > completed || count < 0) return

        val saved = prefs.edit().putInt(ACKED_REWARDS, count).commit()
        if (saved) {
            Log.d(TAG, "Reward acknowledged by WebView: $count")
        } else {
            Log.e(TAG, "Failed to persist reward acknowledgement: $count")
        }
    }

    private fun queueOrRun(script: String) {
        if (!::webView.isInitialized) return
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            if (pageLoaded) {
                webView.evaluateJavascript(script, null)
            } else {
                synchronized(pendingJavaScript) {
                    pendingJavaScript.add(script)
                }
            }
        }
    }

    private fun notifyRewardedCount(count: Int) {
        queueOrRun(
            "if(typeof window.onCompletedAdsCount==='function'){" +
                "window.onCompletedAdsCount($count);}"
        )
    }

    private fun notifyWebViewReward(count: Int) {
        queueOrRun(
            "if(typeof window.onAdRewarded==='function'){" +
                "window.onAdRewarded($count);}"
        )
    }

    private fun notifyWebView(functionName: String) {
        val allowedFunctions = setOf(
            "onRewardedAdReady",
            "onRewardedAdNotReady",
            "onRewardedAdOpened",
            "onRewardedAdClosed",
            "onRewardedAdError"
        )
        if (functionName !in allowedFunctions) return
        queueOrRun(
            "if(typeof window.$functionName==='function'){" +
                "window.$functionName();}"
        )
    }

    private fun notifyWebViewStatus(message: String) {
        val safeMessage = org.json.JSONObject.quote(message)
        queueOrRun(
            "if(typeof window.onNativeAdStatus==='function'){" +
                "window.onNativeAdStatus($safeMessage);}"
        )
    }

    inner class AndroidAds {
        @JavascriptInterface
        fun getCompletedAdsCount(): Int = this@MainActivity.getCompletedAdsCount()

        @JavascriptInterface
        fun getAcknowledgedRewardCount(): Int =
            this@MainActivity.getAcknowledgedRewardCount()

        @JavascriptInterface
        fun showRewardedAd() {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    this@MainActivity.showRewardedAd()
                }
            }
        }

        @JavascriptInterface
        fun acknowledgeReward(count: Int) {
            this@MainActivity.acknowledgeReward(count)
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
        pageLoaded = false
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("AndroidAds")
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun Int.dp(): Int =
        (this * resources.displayMetrics.density).toInt()
}
