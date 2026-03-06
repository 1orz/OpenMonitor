package com.cloudorz.monitor.core.common;

interface IShellService {
    void destroy() = 16777114;
    String executeCommand(String command) = 1;
    String readFileContent(String path) = 2;
    boolean writeFileContent(String path, String value) = 3;
}
