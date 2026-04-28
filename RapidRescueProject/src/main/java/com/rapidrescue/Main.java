package com.rapidrescue;

import javafx.application.Application;
import com.rapidrescue.ui.DispatchWindow;

public class Main {
    public static void main(String[] args) {
        // Allow JavaFX WebView to load external resources (needed for map tiles)
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
        System.setProperty("javafx.webkit.useNativeUserAgent", "true");
        Application.launch(DispatchWindow.class, args);
    }
}