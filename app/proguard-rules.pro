# Keep WebView JavaScript interface
-keepclassmembers class com.vyapar.businessmanager.MainActivity$AndroidBridge {
    public *;
}
-keepattributes JavascriptInterface
-keep public class com.vyapar.businessmanager.MainActivity$AndroidBridge
