package com.vyapar.businessmanager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.Toast;
import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int PERMISSION_REQUEST   = 1002;
    private ValueCallback<Uri[]> filePathCallback;

    @SuppressLint({"SetJavaScriptEnabled","JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setSupportZoom(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setDatabasePath(getApplicationContext().getDir("databases",0).getPath());

        webView.addJavascriptInterface(new AndroidBridge(), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String url = r.getUrl().toString();
                if (url.startsWith("file://") || url.startsWith("http")) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
                return true;
            }
            @Override
            public void onPageFinished(WebView v, String url) {
                super.onPageFinished(v, url);
                v.evaluateJavascript(
                    "(function(){" +
                    "window.IS_ANDROID_APP=true;" +
                    "var m=document.querySelector('meta[name=viewport]');" +
                    "if(m)m.content='width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no';" +
                    "})();", null);
            }
            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {}
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb, FileChooserParams p) {
                filePathCallback = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(Intent.createChooser(i,"File Select"), FILE_CHOOSER_REQUEST);
                return true;
            }
            @Override
            public boolean onJsAlert(WebView v, String url, String msg, JsResult r) {
                new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                    .setTitle("વ્યાપાર").setMessage(msg)
                    .setPositiveButton("OK",(d,w)->r.confirm())
                    .setOnCancelListener(d->r.cancel()).show();
                return true;
            }
            @Override
            public boolean onJsConfirm(WebView v, String url, String msg, JsResult r) {
                new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                    .setTitle("Confirm").setMessage(msg)
                    .setPositiveButton("હા",(d,w)->r.confirm())
                    .setNegativeButton("ના",(d,w)->r.cancel()).show();
                return true;
            }
            @Override public boolean onConsoleMessage(ConsoleMessage m) { return true; }
        });

        requestPermissions();
        webView.loadUrl("file:///android_asset/index.html");
    }

    // ================================================================
    public class AndroidBridge {

        @JavascriptInterface
        public boolean isAndroid() { return true; }

        @JavascriptInterface
        public void showToast(String msg) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }

        // PDF via Android Print Manager (saves as PDF too)
        @JavascriptInterface
        public void printPage() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                    PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter("VyaparBill");
                    PrintAttributes.Builder b = new PrintAttributes.Builder();
                    b.setMediaSize(PrintAttributes.MediaSize.ISO_A4);
                    pm.print("VyaparBill", adapter, b.build());
                }
            });
        }

        // Save JSON backup — ALWAYS SAME FILE (overwrite)
        @JavascriptInterface
        public void saveBackupFile(String jsonData, String fileName) {
            new Thread(() -> {
                try {
                    // Use internal app files dir — always overwrites, no permission needed
                    File dir = new File(getExternalFilesDir(null), "VyaparBackup");
                    if (!dir.exists()) dir.mkdirs();
                    File file = new File(dir, fileName);
                    // false = overwrite (not append)
                    try (FileOutputStream fos = new FileOutputStream(file, false)) {
                        fos.write(jsonData.getBytes("UTF-8"));
                    }

                    // Also copy to Downloads via MediaStore (best effort)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            // Delete ALL existing files with same name first
                            String[] proj = { MediaStore.Downloads._ID };
                            String sel = MediaStore.Downloads.DISPLAY_NAME + "=?";
                            String[] args = { fileName };
                            android.database.Cursor c = getContentResolver().query(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI, proj, sel, args, null);
                            if (c != null) {
                                while (c.moveToNext()) {
                                    long id = c.getLong(0);
                                    Uri del = android.content.ContentUris.withAppendedId(
                                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, id);
                                    getContentResolver().delete(del, null, null);
                                }
                                c.close();
                            }
                            // Insert fresh
                            ContentValues cv = new ContentValues();
                            cv.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                            cv.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                            cv.put(MediaStore.Downloads.RELATIVE_PATH,
                                   Environment.DIRECTORY_DOWNLOADS + "/VyaparBackup");
                            Uri uri = getContentResolver().insert(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                            if (uri != null) {
                                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                                    if (os != null) os.write(jsonData.getBytes("UTF-8"));
                                }
                            }
                        } catch (Exception ignored) {}
                    } else {
                        File dlDir = new File(
                            Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOWNLOADS), "VyaparBackup");
                        if (!dlDir.exists()) dlDir.mkdirs();
                        File dlFile = new File(dlDir, fileName);
                        try (FileOutputStream fos2 = new FileOutputStream(dlFile, false)) {
                            fos2.write(jsonData.getBytes("UTF-8"));
                        }
                    }

                    final String path = file.getAbsolutePath();
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "✅ Backup Updated!\n" + fileName,
                        Toast.LENGTH_SHORT).show());
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "❌ Backup Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        }

        // Share backup via WhatsApp / Gmail / Drive
        @JavascriptInterface
        public void shareBackupFile(String jsonData, String fileName) {
            runOnUiThread(() -> {
                try {
                    File cacheDir = new File(getCacheDir(), "backup");
                    if (!cacheDir.exists()) cacheDir.mkdirs();
                    File file = new File(cacheDir, fileName);
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        fos.write(jsonData.getBytes("UTF-8"));
                    }
                    Uri fileUri = FileProvider.getUriForFile(
                        MainActivity.this, getPackageName() + ".provider", file);
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("application/json");
                    intent.putExtra(Intent.EXTRA_STREAM, fileUri);
                    intent.putExtra(Intent.EXTRA_SUBJECT, "Vyapar Backup - " + fileName);
                    intent.putExtra(Intent.EXTRA_TEXT, "વ્યાપાર App Backup File");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent, "Backup Share કરો"));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this,
                        "Share Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public String getDeviceInfo() {
            return Build.MANUFACTURER + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")";
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("બહાર નીકળો?").setMessage("App બંધ કરવું છે?")
                .setPositiveButton("હા", (d,w) -> finish())
                .setNegativeButton("ના", (d,w) -> d.dismiss()).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] results = null;
            if (res == Activity.RESULT_OK && data != null && data.getData() != null)
                results = new Uri[]{data.getData()};
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    private void requestPermissions() {
        String[] perms = { Manifest.permission.INTERNET,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA };
        boolean need = false;
        for (String p : perms)
            if (ContextCompat.checkSelfPermission(this,p) != PackageManager.PERMISSION_GRANTED)
                { need=true; break; }
        if (need) ActivityCompat.requestPermissions(this, perms, PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(req, p, r);
        if (req == PERMISSION_REQUEST) webView.reload();
    }

    @Override protected void onResume()  { super.onResume();  webView.onResume(); }
    @Override protected void onPause()   { super.onPause();   webView.onPause(); }
    @Override protected void onDestroy() { super.onDestroy(); webView.destroy(); }
}
