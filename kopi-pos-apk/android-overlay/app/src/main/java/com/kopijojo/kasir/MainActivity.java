package com.kopijojo.kasir;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

/**
 * Kopi Jojo POS.
 * Tampilan aplikasi dimuat langsung dari https://kasir.kopijojo.com (lihat capacitor.config.json),
 * jadi setiap deploy web di Vercel otomatis jadi "update OTA" tanpa pasang ulang APK.
 * Kalau bagian native (APK) diperbarui, AppUpdater mengecek GitHub Releases dan menawarkan update.
 */
public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(NonaPrintPlugin.class);
        registerPlugin(KopiFilePlugin.class);
        registerPlugin(AppUpdatePlugin.class);
        super.onCreate(savedInstanceState);
        AppUpdater.get().check(this, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        AppUpdater.get().onResume(this);
    }
}
