package com.kopijojo.kasir;

import android.content.Context;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Pengganti window.print() di APK (WebView Android tidak menjalankan window.print()).
 * Web mengirim salinan HTML halaman; dicetak lewat dialog cetak Android (printer / Simpan PDF).
 * Nama plugin "NonaPrint" dipertahankan karena dipakai kode web yang sama dengan Nona POS.
 */
@CapacitorPlugin(name = "NonaPrint")
public class NonaPrintPlugin extends Plugin {
    private WebView printView; // dipegang supaya tidak dibersihkan sebelum dialog cetak selesai

    @PluginMethod
    public void print(final PluginCall call) {
        final String html = call.getString("html");
        final String baseUrl = call.getString("baseUrl", null);
        final String rawName = call.getString("name", "Dokumen");
        if (html == null || html.isEmpty()) {
            call.reject("Isi cetakan kosong");
            return;
        }
        getActivity().runOnUiThread(() -> {
            try {
                final WebView wv = new WebView(getActivity());
                wv.getSettings().setJavaScriptEnabled(false);
                final boolean[] done = {false};
                wv.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        if (done[0]) return;
                        done[0] = true;
                        try {
                            String job = rawName.replaceAll("[^A-Za-z0-9 _.-]", " ").trim();
                            if (job.isEmpty()) job = "Dokumen";
                            PrintManager pm = (PrintManager) getActivity().getSystemService(Context.PRINT_SERVICE);
                            PrintDocumentAdapter adapter = view.createPrintDocumentAdapter(job);
                            pm.print(job, adapter, new PrintAttributes.Builder().build());
                            call.resolve();
                        } catch (Exception e) {
                            call.reject("Gagal membuka dialog cetak: " + e.getMessage());
                        }
                    }
                });
                printView = wv;
                wv.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null);
            } catch (Exception e) {
                call.reject("Gagal menyiapkan cetak: " + e.getMessage());
            }
        });
    }
}
