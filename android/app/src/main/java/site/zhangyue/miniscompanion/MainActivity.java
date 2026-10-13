package site.zhangyue.miniscompanion;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.HttpAuthHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Minis Companion v0.1 — thin client for your self-hosted session mirror.
 * Data plane: https://erweima.zhangyue.site/sessions/ (see repo docs).
 * The companion never touches OpenMinis internals.
 */
public class MainActivity extends Activity {
    private static final String MIRROR = "https://erweima.zhangyue.site/";
    private WebView web;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        setContentView(web);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public void onReceivedHttpAuthRequest(WebView v, HttpAuthHandler h, String host, String realm) {
                h.proceed(AUTH_USER, AUTH_PASS);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                return !req.getUrl().toString().startsWith(MIRROR);
            }
        });
        web.loadUrl(MIRROR + "sessions/");
    }

    // Basic credentials for the private mirror (or empty to get a system prompt).
    private static final String AUTH_USER = "@AUTH_USER@";
    private static final String AUTH_PASS = "@AUTH_PASS@";

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
