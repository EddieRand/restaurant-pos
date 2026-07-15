// SUNMI inner printer service AIDL interface.
// Source: SUNMI Developer Platform — https://developer.sunmi.com
// Drop the official woyou.market.factoryservice AAR into core/hardware/libs/ for production builds.

package woyou.aidlservice.jiuiv5;

import woyou.aidlservice.jiuiv5.ICallback;

interface IWoyouService {
    // Status
    int getPrinterStatus();
    int printerStatus();

    // Control
    void printerInit(in ICallback callback);
    void setPrinterStyle(int key, in ICallback callback);

    // Text
    void printText(String text, in ICallback callback);
    void printColumnsText(
        in String[] texts,
        in int[] widths,
        in int[] aligns,
        in ICallback callback
    );

    // Feed / cut
    void lineWrap(int n, in ICallback callback);
    void cutPaper(in ICallback callback);
}
