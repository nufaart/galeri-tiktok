package com.iqbal.galeri;

import android.Manifest;
import android.database.Cursor;
import android.os.Build;
import android.provider.MediaStore;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/** Membaca daftar foto & video dari galeri internal (hanya baca, tidak mengubah file). */
@CapacitorPlugin(
    name = "Gallery",
    permissions = {
        @Permission(alias = "media33", strings = { Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO }),
        @Permission(alias = "legacy", strings = { Manifest.permission.READ_EXTERNAL_STORAGE })
    }
)
public class GalleryPlugin extends Plugin {

    private String alias() {
        return Build.VERSION.SDK_INT >= 33 ? "media33" : "legacy";
    }

    @PluginMethod
    public void list(PluginCall call) {
        if (getPermissionState(alias()) != PermissionState.GRANTED) {
            requestPermissionForAlias(alias(), call, "permCallback");
            return;
        }
        doList(call);
    }

    @PermissionCallback
    private void permCallback(PluginCall call) {
        if (getPermissionState(alias()) == PermissionState.GRANTED) {
            doList(call);
        } else {
            call.reject("DENIED");
        }
    }

    private void doList(PluginCall call) {
        int limit = call.getInt("limit", 3000);
        JSArray out = new JSArray();
        String[] proj = {
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATA
        };
        String sel = MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?,?)";
        String[] args = {
            String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE),
            String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
        };
        String sort = MediaStore.Files.FileColumns.DATE_ADDED + " DESC";
        try (Cursor c = getContext().getContentResolver().query(
            MediaStore.Files.getContentUri("external"), proj, sel, args, sort)) {
            if (c != null) {
                while (c.moveToNext() && out.length() < limit) {
                    String path = c.getString(4);
                    if (path == null) continue;
                    JSObject o = new JSObject();
                    o.put("id", c.getLong(0));
                    o.put("name", c.getString(1));
                    o.put("type", c.getString(2));
                    o.put("added", c.getLong(3) * 1000L);
                    o.put("path", path);
                    out.put(o);
                }
            }
        } catch (Exception e) {
            call.reject("ERR: " + e.getMessage());
            return;
        }
        JSObject res = new JSObject();
        res.put("items", out);
        call.resolve(res);
    }
}
