package com.phoneai.overlay;

import java.io.File;

/** 互通測試用：Java 客戶端去連 Python 伺服器。參數：埠 金鑰(hex) 動作(ping|list|find|get|put) 參數 額外。 */
public class JavaClient {
    public static void main(String[] a) throws Exception {
        TransferClient c = new TransferClient("127.0.0.1", Integer.parseInt(a[0]), "pyphone", "JavaPhone", TransferCrypto.unhex(a[1]));
        switch (a[2]) {
            case "ping": System.out.println("OUT " + c.ping()); break;
            case "list": System.out.print("OUT\n" + c.list(a[3])); break;
            case "find": System.out.print("OUT\n" + c.find(a[3])); break;
            case "get": System.out.println("OUT " + c.download(a[3], new File(a[4]), null)); break;
            case "put": System.out.println("OUT " + c.put(new File(a[3]), null)); break;
            default: throw new IllegalArgumentException(a[2]);
        }
    }
}
