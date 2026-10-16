# JS bridge: names are looked up reflectively by the WebView (window.PdfCraftAndroid).
-keepattributes *Annotation*
-keep class com.galiehneh.pdfcraft.AndroidBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class * { @android.webkit.JavascriptInterface <methods>; }

# androidx.webkit loads WebView support-library boundary interfaces reflectively.
-keep class org.chromium.support_lib_boundary.** { *; }
-keep class androidx.webkit.** { *; }
-dontwarn org.chromium.support_lib_boundary.**
