package lv.ritmi.app;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQ_OPEN = 1, REQ_SAVE = 2;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private String pendingSave;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.parseColor("#c62828"));
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try { startActivityForResult(i, REQ_OPEN); } catch (Exception e) { fileCallback = null; return false; }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        if (state == null) web.loadUrl("file:///android_asset/index.html");
        else web.restoreState(state);
        setContentView(web);
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        web.evaluateJavascript("window.dispatchEvent(new Event('ritmi-theme'))", null);
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_OPEN) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(res == RESULT_OK && data != null && data.getData() != null ? new Uri[]{data.getData()} : null);
                fileCallback = null;
            }
        } else if (req == REQ_SAVE && res == RESULT_OK && data != null && pendingSave != null) {
            try (OutputStream os = getContentResolver().openOutputStream(data.getData())) {
                os.write(pendingSave.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "✓", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            }
            pendingSave = null;
        }
    }

    private class Bridge {
        @JavascriptInterface
        public void saveFile(String name, String text, String mime) {
            runOnUiThread(() -> {
                pendingSave = text;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(mime);
                i.putExtra(Intent.EXTRA_TITLE, name);
                startActivityForResult(i, REQ_SAVE);
            });
        }

        @JavascriptInterface
        public void shareFile(String name, String text, String mime) {
            runOnUiThread(() -> {
                try {
                    File dir = new File(getCacheDir(), "share");
                    dir.mkdirs();
                    File f = new File(dir, name.replaceAll("[/\\\\]", "_"));
                    try (FileOutputStream os = new FileOutputStream(f)) { os.write(text.getBytes(StandardCharsets.UTF_8)); }
                    Uri uri = Uri.parse("content://lv.ritmi.app.files/" + Uri.encode(f.getName()));
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType(mime);
                    i.putExtra(Intent.EXTRA_STREAM, uri);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(i, name));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void shareText(String text) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(i, null));
            });
        }

        @JavascriptInterface
        public void copy(String text) {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Ritmi", text));
        }

        @JavascriptInterface
        public void syncState(String json) {
            getSharedPreferences("ritmi", MODE_PRIVATE).edit().putString("state", json).apply();
        }

        @JavascriptInterface
        public void enableNotify() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3);
                }
                Reminder.schedule(MainActivity.this);
            });
        }

        @JavascriptInterface
        public boolean isDark() {
            return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        }

        @JavascriptInterface
        public void setBarColor(String color) {
            runOnUiThread(() -> { try { getWindow().setStatusBarColor(Color.parseColor(color)); } catch (Exception ignored) { } });
        }

        @JavascriptInterface
        public void disableNotify() {
            Reminder.cancel(MainActivity.this);
        }
    }
}
