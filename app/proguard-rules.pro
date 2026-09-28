# org.json and Media3 ship their own keep rules; nothing app-specific is reflected on.

# The YouTube player page calls these from JavaScript.
-keepclassmembers class com.samstream.app.player.YouTubeBridge {
    @android.webkit.JavascriptInterface <methods>;
}
