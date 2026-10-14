package site.zhangyue.miniscompanion;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.security.MessageDigest;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Minis Companion v0.4 — mirror browser + on-demand auto-sync + early OTA.
 *
 * UX:
 *  - OTA check runs BEFORE the PIN gate (a broken build can update itself),
 *    plus a manual "检查更新" button on the gate.
 *  - No folder picker on launch. A "⇪ 同步" button in the web view explains
 *    and asks once; afterwards it scans + uploads new .minisbak packages.
 *  - PIN gate + FLAG_SECURE kept.
 */
public class MainActivity extends Activity {
    private static final String MIRROR = "https://erweima.zhangyue.site/";
    private static final String UPLOAD_URL = MIRROR + "sessions-upload/";
    private static final String AUTH_USER = "@AUTH_USER@";
    private static final String AUTH_PASS = "@AUTH_PASS@";
    private static final String PIN_SHA256 = "@PIN_SHA256@";

    private static final int REQ_FOLDER = 41;
    private static final int REQ_INSTALL_PKG = 42;
    private static final int REQ_FILE = 43;

    private WebView web;
    private boolean unlocked = false;

    // ---------------- OTA (pre-gate, manual button too) ----------------
    private static final String OTA_URL = "https://d.zhangyue.site/minis-companion.json";
    private long downloadId = -1;

    private void checkOta(final boolean manual) {
        new Thread(() -> {
            String curVn;
            int curVc;
            try {
                android.content.pm.PackageInfo pi =
                        getPackageManager().getPackageInfo(getPackageName(), 0);
                curVn = pi.versionName; curVc = pi.versionCode;
            } catch (Exception e) { curVn = "?"; curVc = 0; }
            final String fVn = curVn; final int fVc = curVc;
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
                if (vc <= fVc) {
                    if (manual) show("已是最新版本 " + fVn);
                    return;
                }
                runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                        .setTitle("发现新版本 " + vn)
                        .setMessage(notes)
                        .setPositiveButton("更新", (d, w) -> startOtaDownload(url, sha))
                        .setNegativeButton("以后", null)
                        .show());
            } catch (Exception e) {
                if (manual) show("检查更新失败（网络？）");
            }
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
            for (int i = 0; i < 120; i++) {
                try { Thread.sleep(5000); } catch (Exception ignored) { }
                Cursor cur = dm.query(new DownloadManager.Query().setFilterById(downloadId));
                if (cur == null) continue;
                if (cur.moveToFirst()) {
                    int st = cur.getInt(cur.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    if (st == DownloadManager.STATUS_SUCCESSFUL) {
                        cur.close();
                        Uri apk = dm.getUriForDownloadedFile(downloadId);
                        String path = apk.getPath();
                        if (sha.isEmpty() || sha256hex(new java.io.File(path)).equalsIgnoreCase(sha)) {
                            installApk(apk);
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

    // ---------------- lifecycle ----------------

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        checkOta(false);            // OTA before the gate — self-heal even if PIN breaks
        if (!unlocked) showGate(); else initWeb();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FOLDER && res == RESULT_OK && data != null) {
            Uri tree = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(tree,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getPreferences(MODE_PRIVATE).edit()
                        .putString("backup_tree", tree.toString()).apply();
                show("已开启自动同步");
                scanAndUpload();
            } catch (Exception e) { show("授权失败: " + e.getMessage()); }
        } else if (req == REQ_FILE && res == RESULT_OK && data != null) {
            final Uri f = data.getData();
            new Thread(() -> {
                String name = queryName(f);
                if (!name.toLowerCase().endsWith(".minisbak")) {
                    show("这不是 .minisbak 备份包: " + name);
                    return;
                }
                if (httpPutStream(f, name)) show("已上传: " + name + "（2分钟内自动合并）");
                else show("上传失败: " + name);
            }).start();
        }
    }

    // ---------------- PIN gate ----------------

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
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setGravity(Gravity.CENTER);
        pin.setTextColor(Color.WHITE);
        pin.setHintTextColor(Color.parseColor("#5A7076"));
        pin.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#E4C88B")));
        pin.setMaxLines(1);
        Button go = new Button(this);
        go.setText("解锁");
        go.setTextColor(Color.parseColor("#182B30"));
        go.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#E4C88B")));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        bp.topMargin = dp(22);

        Button check = new Button(this);
        check.setText("检查更新");
        check.setTextSize(12);
        check.setTextColor(Color.parseColor("#95AAAC"));
        check.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.CENTER_HORIZONTAL;
        cp.topMargin = dp(10);
        check.setOnClickListener(v -> checkOta(true));

        box.addView(title); box.addView(sub); box.addView(pin); box.addView(go, bp);
        box.addView(check, cp);
        setContentView(box);
        go.setOnClickListener(v -> tryUnlock(pin));
        pin.setOnEditorActionListener((tv, i, k) -> { tryUnlock(pin); return true; });
    }

    private void tryUnlock(EditText pin) {
        if (check(pin.getText().toString())) { unlocked = true; initWeb(); }
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

    // ---------------- web + sync button ----------------

    private void initWeb() {
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        FrameLayout fl = new FrameLayout(this);
        fl.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        Button sync = new Button(this);
        sync.setText("⇪ 同步");
        sync.setTextSize(12);
        sync.setTextColor(Color.parseColor("#182B30"));
        sync.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Color.parseColor("#E4C88B")));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        sp.setMargins(0, 0, dp(14), dp(18));
        fl.addView(sync, sp);
        sync.setOnClickListener(v -> onSyncClicked());
        setContentView(fl);
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

    /** Sync button: explain first-run (which folder & why), then pick/scan. */
    private void onSyncClicked() {
        String saved = getPreferences(MODE_PRIVATE).getString("backup_tree", null);
        if (saved != null) { scanAndUpload(); return; }
        new AlertDialog.Builder(this)
                .setTitle("上传备份包到服务器")
                .setMessage(
                    "怎么选？取决于 Minis 备份包在哪：\n\n" +
                    "① 选文件夹（推荐）：如果 Minis 设置→备份与恢复 里添加了「本地文件夹」" +
                    "目的地（如 下载/MinisBackup），选同一个文件夹，以后每次导出自动上传。\n\n" +
                    "② 选单个文件：直接浏览选中 .minisbak 备份包，传完即合并（一次性）。\n\n" +
                    "两个都能用，随时可以换。")
                .setPositiveButton("选文件夹", (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    startActivityForResult(i, REQ_FOLDER);
                })
                .setNeutralButton("选单个文件", (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, REQ_FILE);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------------- backup scan/upload ----------------

    private void scanAndUpload() {
        final Uri tree;
        try {
            tree = Uri.parse(getPreferences(MODE_PRIVATE).getString("backup_tree", ""));
            if (tree == null || tree.toString().isEmpty()) return;
        } catch (Exception e) { return; }
        new Thread(() -> {
            int uploaded = 0, failed = 0;
            try {
                java.util.Set<String> done = getPreferences(MODE_PRIVATE)
                        .getStringSet("uploaded", new java.util.HashSet<>());
                java.util.Set<String> now = new java.util.HashSet<>(done);
                Uri doc = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                        tree, android.provider.DocumentsContract.getTreeDocumentId(tree));
                android.database.Cursor c = getContentResolver().query(doc,
                        new String[]{android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                        null, null, null);
                java.util.List<Uri> found = new java.util.ArrayList<>();
                java.util.List<String> names = new java.util.ArrayList<>();
                if (c != null) {
                    while (c.moveToNext()) {
                        String name = c.getString(1);
                        if (name != null && name.toLowerCase().endsWith(".minisbak")
                                && !done.contains(name)) {
                            found.add(android.provider.DocumentsContract.buildDocumentUriUsingTree(
                                    tree, c.getString(0)));
                            names.add(name);
                        }
                    }
                    c.close();
                }
                for (int i = 0; i < found.size(); i++) {
                    if (httpPutStream(found.get(i), names.get(i))) {
                        now.add(names.get(i)); uploaded++;
                    } else failed++;
                }
                getPreferences(MODE_PRIVATE).edit().putStringSet("uploaded", now).apply();
                final int up = uploaded, fa = failed;
                runOnUiThread(() -> {
                    if (up == 0 && fa == 0) show("没有新的备份包");
                    else if (fa == 0) show("已同步 " + up + " 个备份包");
                    else show("同步 " + up + " 个，失败 " + fa + " 个");
                });
            } catch (Exception e) {
                show("扫描失败: " + e.getMessage());
            }
        }).start();
    }

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
            java.io.OutputStream os = conn.getOutputStream();
            java.io.InputStream in = getContentResolver().openInputStream(u);
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            os.close(); in.close();
            return conn.getResponseCode() < 300;
        } catch (Exception e) { return false; }
        finally { if (conn != null) conn.disconnect(); }
    }

    private String queryName(Uri u) {
        android.database.Cursor c = getContentResolver().query(u, null, null, null, null);
        String n = "backup.minisbak";
        if (c != null) {
            if (c.moveToFirst()) n = c.getString(c.getColumnIndex(
                    android.provider.OpenableColumns.DISPLAY_NAME));
            c.close();
        }
        return n;
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

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
