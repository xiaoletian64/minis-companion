package site.zhangyue.miniscompanion;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.InputType;
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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * Minis Companion v0.2 — mirror browser + auto backup uploader.
 *
 * Flow: user picks the Minis backup folder once (SAF) -> any *.minisbak that
 * appears there is uploaded automatically to the server's DAV endpoint
 * (/sessions-upload/) and merged into the mirror by merge_worker.
 * Launch PIN gate + FLAG_SECURE kept from v0.1.1.
 */
public class MainActivity extends Activity {
    private static final String MIRROR = "https://erweima.zhangyue.site/";
    private static final String UPLOAD_URL = MIRROR + "sessions-upload/";
    private static final String AUTH_USER = "@AUTH_USER@";
    private static final String AUTH_PASS = "@AUTH_PASS@";
    private static final String PIN_SHA256 = "@PIN_SHA256@";

    private static final int REQ_FOLDER = 41;
    private static final int REQ_INSTALL_PKG = 42;

    private WebView web;
    private boolean unlocked = false;
    private Uri backupTreeUri; // persisted via prefs each launch

    // ---------------- OTA self-update ----------------
    // Manifest is a tiny JSON served next to the APK:
    //   {"versionCode":3,"versionName":"0.2.0","url":"https://d.zhangyue.site/minis-companion.apk","sha256":"<hex>","notes":"..."}
    private static final String OTA_URL = "https://d.zhangyue.site/minis-companion.json";
    private long downloadId = -1;

    private void checkOta() {
        new Thread(() -> {
            try {
                java.net.URL u = new java.net.URL(OTA_URL);
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) u.openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream()));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = r.readLine()) != null) sb.append(line.trim());
                r.close();
                org.json.JSONObject m = new org.json.JSONObject(sb.toString());
                final int vc = m.getInt("versionCode");
                final String vn = m.optString("versionName", "");
                final String notes = m.optString("notes", "");
                final String url = m.getString("url");
                final String sha = m.optString("sha256", "");
                if (vc <= getPackageManager().getPackageInfo(getPackageName(), 0).versionCode) return;
                runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                        .setTitle("发现新版本 " + vn)
                        .setMessage(notes)
                        .setPositiveButton("更新", (d, w) -> startOtaDownload(url, sha))
                        .setNegativeButton("以后", null)
                        .show());
            } catch (Exception ignored) { }
        }).start();
    }

    private void startOtaDownload(String url, final String sha) {
        try {
            if (Build.VERSION.SDK_INT >= 26 &&
                    !getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(this, "请允许「安装未知应用」后重试更新", Toast.LENGTH_LONG).show();
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())), REQ_INSTALL_PKG);
                return;
            }
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url))
                    .setTitle("Minis Companion 更新")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "update.apk");
            downloadId = dm.enqueue(req);
            Toast.makeText(this, "开始下载…", Toast.LENGTH_SHORT).show();
            watchDownload(sha);
        } catch (Exception e) {
            Toast.makeText(this, "下载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void watchDownload(final String sha) {
        new Thread(() -> {
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            for (int i = 0; i < 120; i++) { // up to ~10 min
                try { Thread.sleep(5000); } catch (Exception ignored) { }
                Cursor cur = dm.query(new DownloadManager.Query().setFilterById(downloadId));
                if (cur == null) continue;
                if (cur.moveToFirst()) {
                    int st = cur.getInt(cur.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    if (st == DownloadManager.STATUS_SUCCESSFUL) {
                        cur.close();
                        String path = dm.getUriForDownloadedFile(downloadId).getPath();
                        if (sha.isEmpty() || sha256hex(new java.io.File(path)).equalsIgnoreCase(sha)) {
                            installApk(dm.getUriForDownloadedFile(downloadId));
                        } else {
                            show("校验失败，已放弃安装");
                        }
                        return;
                    } else if (st == DownloadManager.STATUS_FAILED) { cur.close(); show("下载失败"); return; }
                }
                cur.close();
            }
        }).start();
    }

    private void installApk(Uri apkUri) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(apkUri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) { show("无法安装: " + e.getMessage()); }
    }

    private static String sha256hex(java.io.File f) {
        try {
            java.io.InputStream in = new java.io.FileInputStream(f);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            in.close();
            StringBuilder b = new StringBuilder();
            for (byte x : md.digest()) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) { return ""; }
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        if (!unlocked) { showGate(); return; }
        initWeb();
        maybePickFolder();
        checkOta();
    }

    /** Offer one-time SAF folder pick for Minis backup auto-upload. */
    private void maybePickFolder() {
        if (getPreferences(MODE_PRIVATE).contains("backup_tree")) return; // already set
        Toast.makeText(this, "选择 Minis 备份文件夹以开启自动同步（可跳过）", Toast.LENGTH_LONG).show();
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_FOLDER);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FOLDER && res == RESULT_OK && data != null) {
            backupTreeUri = data.getData();
            getContentResolver().takePersistableUriPermission(backupTreeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getPreferences(MODE_PRIVATE).edit().putString("backup_tree", backupTreeUri.toString()).apply();
            scanAndUpload();
        }
    }

    /** Scan picked folder for *.minisbak not yet uploaded (tracked in prefs). */
    private void scanAndUpload() {
        if (backupTreeUri == null) return;
        new Thread(() -> {
            try {
                java.util.Set<String> done = getPreferences(MODE_PRIVATE).getStringSet("uploaded", new java.util.HashSet<>());
                java.util.List<Uri> found = new java.util.ArrayList<>();
                android.net.Uri doc = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                        backupTreeUri, android.provider.DocumentsContract.getTreeDocumentId(backupTreeUri));
                android.database.Cursor c = getContentResolver().query(doc,
                        new String[]{android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
                if (c != null) {
                    while (c.moveToNext()) {
                        String name = c.getString(1);
                        if (name != null && name.toLowerCase().endsWith(".minisbak") && !done.contains(name)) {
                            found.add(android.provider.DocumentsContract.buildDocumentUriUsingTree(
                                    backupTreeUri, c.getString(0)));
                        }
                    }
                    c.close();
                }
                java.util.Set<String> now = new java.util.HashSet<>(done);
                for (Uri u : found) {
                    String name = queryName(u);
                    boolean ok = httpPutStream(u, name);
                    if (ok) { now.add(name); show("已同步: " + name); }
                    else show("同步失败: " + name);
                }
                getPreferences(MODE_PRIVATE).edit().putStringSet("uploaded", now).apply();
            } catch (Exception e) {
                show("扫描失败: " + e.getMessage());
            }
        }).start();
    }

    private String queryName(Uri u) {
        android.database.Cursor c = getContentResolver().query(u, null, null, null, null);
        String n = "backup.minisbak";
        if (c != null) { if (c.moveToFirst()) n = c.getString(c.getColumnIndex(
                android.provider.OpenableColumns.DISPLAY_NAME)); c.close(); }
        return n;
    }

    /** Stream the SAF document to the DAV endpoint with Basic auth. */
    private boolean httpPutStream(Uri u, String name) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(UPLOAD_URL + java.net.URLEncoder.encode(name, "UTF-8"))
                    .openConnection();
            conn.setRequestMethod("PUT");
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode((int) querySize(u));
            String auth = android.util.Base64.encodeToString(
                    (AUTH_USER + ":" + AUTH_PASS).getBytes(), android.util.Base64.NO_WRAP);
            conn.setRequestProperty("Authorization", "Basic " + auth);
            OutputStream os = conn.getOutputStream();
            java.io.InputStream in = getContentResolver().openInputStream(u);
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            os.close(); in.close();
            return conn.getResponseCode() < 300;
        } catch (Exception e) { return false; }
        finally { if (conn != null) conn.disconnect(); }
    }

    private long querySize(Uri u) {
        android.database.Cursor c = getContentResolver().query(u, null, null, null, null);
        long s = 0;
        if (c != null) { if (c.moveToFirst()) s = c.getLong(c.getColumnIndex(
                android.provider.OpenableColumns.SIZE)); c.close(); }
        return s;
    }

    private void show(final String msg) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
    }

    // ---------- PIN gate (v0.1.1) ----------

    private void showGate() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(android.graphics.Color.parseColor("#102027"));
        box.setPadding(dp(36), 0, dp(36), 0);
        TextView title = new TextView(this);
        title.setText("Minis Companion");
        title.setTextColor(android.graphics.Color.parseColor("#F4EDDD"));
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        TextView sub = new TextView(this);
        sub.setText("输入访问密码");
        sub.setTextColor(android.graphics.Color.parseColor("#95AAAC"));
        sub.setTextSize(14);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(8), 0, dp(28));
        final EditText pin = new EditText(this);
        pin.setHint("密码");
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setGravity(Gravity.CENTER);
        pin.setTextColor(android.graphics.Color.WHITE);
        pin.setHintTextColor(android.graphics.Color.parseColor("#5A7076"));
        pin.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#E4C88B")));
        pin.setMaxLines(1);
        Button go = new Button(this);
        go.setText("解锁");
        go.setTextColor(android.graphics.Color.parseColor("#182B30"));
        go.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#E4C88B")));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        bp.topMargin = dp(22);
        go.setOnClickListener(v -> tryUnlock(pin));
        pin.setOnEditorActionListener((tv, i, k) -> { tryUnlock(pin); return true; });
        box.addView(title); box.addView(sub); box.addView(pin); box.addView(go, bp);
        setContentView(box);
    }

    private void tryUnlock(EditText pin) {
        if (check(pin.getText().toString())) { unlocked = true; initWeb(); maybePickFolder(); }
        else { Toast.makeText(this, "密码不对", Toast.LENGTH_SHORT).show(); pin.setText(""); }
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

    // ---------- WebView ----------

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
