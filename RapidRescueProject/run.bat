@echo off
set JFXLIB=%~dp0..\javafx-sdk\javafx-sdk-25\lib
set SQLITE=%~dp0lib\sqlite-jdbc-3.45.3.0.jar
set SLF4J=%~dp0lib\slf4j-api-2.0.13.jar
set SLF4J_SIMPLE=%~dp0lib\slf4j-simple-2.0.13.jar
set BIN=%~dp0bin
java --module-path "%JFXLIB%" --add-modules javafx.controls,javafx.fxml,javafx.web,javafx.media --enable-native-access=javafx.graphics --add-opens javafx.web/com.sun.webkit=ALL-UNNAMED -cp "%BIN%;%SQLITE%;%SLF4J%;%SLF4J_SIMPLE%" com.rapidrescue.Main
