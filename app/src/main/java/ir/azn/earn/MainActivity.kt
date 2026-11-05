package ir.azn.earn

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ir.tapsell.plus.AdRequestCallback
import ir.tapsell.plus.AdShowListener
import ir.tapsell.plus.TapsellPlus
import ir.tapsell.plus.TapsellPlusInitListener
import ir.tapsell.plus.TapsellPlusBannerType
import ir.tapsell.plus.model.AdNetworkError
import ir.tapsell.plus.model.AdNetworks
import ir.tapsell.plus.model.TapsellPlusAdModel
import ir.tapsell.plus.model.TapsellPlusErrorModel

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var bannerContainer: FrameLayout

    private var responseId: String? = null
    private var isRequesting = false
    private var sdkReady = false
    private var bannerResponseId: String? = null

    private val appKey =
        "robqfqdmedelfokdmefdltccslflqkgthqgsiaajjjkkhtgpasmmcgggcfafbchcntmqho"

    private val rewardedZoneId =
        "6aec64d1a2434702499bd0dd"

    private val bannerZoneId =
        "6aec65d1a2434702499bd0de"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            addJavascriptInterface(AdsBridge(), "AndroidAds")
        }

        bannerContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(60)
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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun initializeTapsell() {
        TapsellPlus.initialize(
            this,
            appKey,
            object : TapsellPlusInitListener {

                override fun onInitializeSuccess(
                    adNetworks: AdNetworks
                ) {
                    runOnUiThread {
                        sdkReady = true
                        updateStatus("شبکه تبلیغات آماده است")
                        requestBannerAd()
                    }
                }

                override fun onInitializeFailed(
                    adNetworks: AdNetworks,
                    adNetworkError: AdNetworkError
                ) {
                    runOnUiThread {
                        sdkReady = false
                        updateStatus("خطا در راه‌اندازی تبلیغات")
                    }
                }
            }
        )
    }

    private fun requestBannerAd() {
        if (!sdkReady) return

        TapsellPlus.requestStandardBannerAd(
            this,
            bannerZoneId,
            TapsellPlusBannerType.BANNER_320x50,
            object : AdRequestCallback() {

                override fun response(
                    adModel: TapsellPlusAdModel
                ) {
                    super.response(adModel)

                    runOnUiThread {
                        bannerResponseId = adModel.responseId

                        if (!bannerResponseId.isNullOrEmpty()) {
                            showBannerAd()
                        } else {
                            updateStatus("بنر تبلیغاتی آماده نشد")
                        }
                    }
                }

                override fun error(message: String) {
                    runOnUiThread {
                        updateStatus("بنر فعلاً در دسترس نیست")
                    }
                }
            }
        )
    }

    private fun showBannerAd() {
        val id = bannerResponseId ?: return

        TapsellPlus.showStandardBannerAd(
            this,
            id,
            bannerContainer,
            object : AdShowListener() {

                override fun onOpened(
                    ad: TapsellPlusAdModel
                ) {
                    super.onOpened(ad)
                }

                override fun onError(
                    error: TapsellPlusErrorModel
                ) {
                    super.onError(error)
                    runOnUiThread {
                        updateStatus("خطا در نمایش بنر")
                    }
                }
            }
        )
    }

    private fun requestRewardedAd() {
        if (!sdkReady) {
            updateStatus("شبکه تبلیغات هنوز آماده نیست")
            return
        }

        if (isRequesting) return

        isRequesting = true
        responseId = null
        updateStatus("در حال دریافت تبلیغ...")

        TapsellPlus.requestRewardedVideoAd(
            this,
            rewardedZoneId,
            object : AdRequestCallback() {

                override fun response(
                    adModel: TapsellPlusAdModel
                ) {
                    super.response(adModel)

                    runOnUiThread {
                        isRequesting = false
                        responseId = adModel.responseId

                        if (!responseId.isNullOrEmpty()) {
                            updateStatus("تبلیغ آماده نمایش است")
                            showRewardedAd()
                        } else {
                            updateStatus("تبلیغ آماده نشد")
                        }
                    }
                }

                override fun error(message: String) {
                    runOnUiThread {
                        isRequesting = false
                        updateStatus("تبلیغ در دسترس نیست")

                        Toast.makeText(
                            this@MainActivity,
                            message,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }

    private fun showRewardedAd() {
        val id = responseId ?: return

        TapsellPlus.showRewardedVideoAd(
            this,
            id,
            object : AdShowListener() {

                override fun onOpened(
                    ad: TapsellPlusAdModel
                ) {
                    super.onOpened(ad)
                    runOnUiThread {
                        updateStatus("تبلیغ در حال پخش است")
                    }
                }

                override fun onClosed(
                    ad: TapsellPlusAdModel
                ) {
                    super.onClosed(ad)

                    runOnUiThread {
                        responseId = null
                        updateStatus("تبلیغ بسته شد")
                    }
                }

                override fun onRewarded(
                    ad: TapsellPlusAdModel
                ) {
                    super.onRewarded(ad)

                    runOnUiThread {
                        webView.evaluateJavascript(
                            "if(window.onAdRewarded){window.onAdRewarded();}",
                            null
                        )
                        updateStatus("مشاهده تبلیغ کامل شد")
                    }
                }

                override fun onError(
                    error: TapsellPlusErrorModel
                ) {
                    super.onError(error)

                    runOnUiThread {
                        responseId = null
                        updateStatus("خطا در نمایش تبلیغ")
                    }
                }
            }
        )
    }

    private fun updateStatus(message: String) {
        if (::webView.isInitialized) {
            val safeMessage = message
                .replace("\\", "\\\\")
                .replace("'", "\\'")

            webView.evaluateJavascript(
                "if(window.onNativeAdStatus){window.onNativeAdStatus('$safeMessage');}",
                null
            )
        }
    }

    inner class AdsBridge {

        @JavascriptInterface
        fun showRewardedAd() {
            runOnUiThread {
                requestRewardedAd()
            }
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::bannerContainer.isInitialized) {
            TapsellPlus.destroyStandardBanner(
                this,
                bannerContainer
            )
        }

        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("AndroidAds")
            webView.destroy()
        }

        super.onDestroy()
    }
}

