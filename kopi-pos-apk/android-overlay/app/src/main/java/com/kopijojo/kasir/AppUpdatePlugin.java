package com.kopijojo.kasir;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Dipanggil dari web: Capacitor.nativePromise('AppUpdate', 'getInfo' | 'check'). */
@CapacitorPlugin(name = "AppUpdate")
public class AppUpdatePlugin extends Plugin {

    @PluginMethod
    public void getInfo(PluginCall call) {
        JSObject r = new JSObject();
        r.put("versionCode", AppUpdater.currentVersionCode(getContext()));
        r.put("versionName", AppUpdater.currentVersionName(getContext()));
        r.put("repo", AppUpdater.repo(getContext()));
        call.resolve(r);
    }

    @PluginMethod
    public void check(PluginCall call) {
        getActivity().runOnUiThread(() -> AppUpdater.get().check(getActivity(), true));
        call.resolve();
    }
}
