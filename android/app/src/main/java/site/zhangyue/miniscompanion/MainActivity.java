package site.zhangyue.miniscompanion;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.HttpAuthHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.security.MessageDigest;

/**
 * Minis Companion — thin client for the self-hosted session mirror.
 * v0.1.1: launch PIN gate + FLAG_SECURE (no screenshots / recents preview).
 * Data plane: https://erweima.zhangyue.site/sessions/ (see repo docs).
 */
public class MainActivity extends Activity {
    private static final String MIRROR = "https://erweima.zhangyue.site/";
    // Injected at build time by build-apk.sh (see android/build-apk.sh):
    private static final String AUTH_USER = "@AUTH_USER@";
    private static final String AUTH_PASS = "@AUTH_PASS@";
    private static final String PIN_SHA256 = "@PIN_SHA256@"; // sha256("mc:" + pin)

    private WebView web;
    private boolean unlocked = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Block screenshots and the recents-task preview showing session content.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        if (unlocked) initWeb(); else showGate();
    }

    private void showGate() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.parseColor("#102027"));
        box.setPadding(dp(36), 0, dp(36), 0);

        TextView title = new TextView(this);
        title.setText("Minis Companion");
        title.setTextColor(Color.parseColor("#F4EDDD"));
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        TextView sub = new TextView(this);
        sub.setText("输入访问密码");
        sub.setTextColor(Color.parseColor("#95AAAC"));
        sub.setTextSize(14);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(8), 0, dp(28));

        final EditText pin = new EditText(this);
        pin.setHint("密码");
        pin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setGravity(Gravity.CENTER);
        pin.setTextColor(Color.WHITE);
        pin.setHintTextColor(Color.parseColor("#5A7076"));
        pin.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Color.parseColor("#E4C88B")));
        pin.setMaxLines(1);

        Button go = new Button(this);
        go.setText("解锁");
        go.setTextColor(Color.parseColor("#182B30"));
        go.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Color.parseColor("#E4C88B")));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        bp.topMargin = dp(22);
        go.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { tryUnlock(pin); }
        });
        pin.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(android.widget.TextView tv, int i, android.view.KeyEvent k) {
                tryUnlock(pin); return true;
            }
        });

        box.addView(title);
        box.addView(sub);
        box.addView(pin);
        box.addView(go, bp);
        setContentView(box);
    }

    private void tryUnlock(EditText pin) {
        if (check(pin.getText().toString())) {
            unlocked = true;
            initWeb();
        } else {
            Toast.makeText(this, "密码不对", Toast.LENGTH_SHORT).show();
            pin.setText("");
        }
    }

    private static boolean check(String s) {
        return MessageDigest.isEqual(sha("mc:" + s).getBytes(), PIN_SHA256.getBytes());
    }

    private static String sha(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) { return ""; }
    }

    private void initWeb() {
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

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
