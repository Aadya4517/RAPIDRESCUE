package com.rapidrescue;

import javafx.application.Application;
import com.rapidrescue.ui.DispatchWindow;

public class Main {
    public static void main(String[] args) {
        // allow webview network access
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
        System.setProperty("javafx.webkit.useNativeUserAgent", "true");
        Application.launch(DispatchWindow.class, args);
    }
}
