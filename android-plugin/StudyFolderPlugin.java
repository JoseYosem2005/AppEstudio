package __PKG__;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import androidx.activity.result.ActivityResult;
import androidx.documentfile.provider.DocumentFile;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Plugin propio: la app trabaja dentro de UNA carpeta que elige el usuario (Storage Access Framework).
 * El permiso se guarda (takePersistableUriPermission) y sigue vigente entre aperturas y reinicios.
 * Todas las rutas son relativas a esa carpeta, con "/" como separador. Ruta vacía = la carpeta raíz.
 */
@CapacitorPlugin(name = "StudyFolder")
public class StudyFolderPlugin extends Plugin {
    private static final String PREFS = "study_folder";
    private static final String KEY = "root_uri";

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
    }

    /** Devuelve la carpeta raíz si el permiso guardado sigue vigente; si no, null. */
    private DocumentFile root() {
        String saved = prefs().getString(KEY, null);
        if (saved == null) return null;
        Uri uri = Uri.parse(saved);
        boolean granted = false;
        for (UriPermission p : getContext().getContentResolver().getPersistedUriPermissions()) {
            if (p.getUri().equals(uri) && p.isReadPermission() && p.isWritePermission()) {
                granted = true;
                break;
            }
        }
        if (!granted) return null;
        DocumentFile r = DocumentFile.fromTreeUri(getContext(), uri);
        return (r != null && r.exists() && r.canWrite()) ? r : null;
    }

    /** Busca (o crea, si create=true) el directorio/archivo de una ruta relativa. */
    private DocumentFile find(DocumentFile root, String path, boolean create) {
        DocumentFile cur = root;
        if (path == null || path.isEmpty()) return cur;
        for (String part : path.split("/")) {
            if (part.isEmpty()) continue;
            DocumentFile next = cur.findFile(part);
            if (next == null) {
                if (!create) return null;
                next = cur.createDirectory(part);
                if (next == null) return null;
            }
            cur = next;
        }
        return cur;
    }

    @PluginMethod
    public void status(PluginCall call) {
        DocumentFile r = root();
        JSObject o = new JSObject();
        o.put("ok", r != null);
        o.put("name", r != null && r.getName() != null ? r.getName() : "");
        call.resolve(o);
    }

    @PluginMethod
    public void pickRoot(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(call, i, "pickRootResult");
    }

    @ActivityCallback
    private void pickRootResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        Intent data = result.getData();
        if (result.getResultCode() != Activity.RESULT_OK || data == null || data.getData() == null) {
            call.reject("cancelled");
            return;
        }
        Uri uri = data.getData();
        getContext().getContentResolver().takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        prefs().edit().putString(KEY, uri.toString()).apply();
        DocumentFile r = DocumentFile.fromTreeUri(getContext(), uri);
        JSObject o = new JSObject();
        o.put("name", r != null && r.getName() != null ? r.getName() : "");
        call.resolve(o);
    }

    @PluginMethod
    public void list(PluginCall call) {
        DocumentFile r = root();
        if (r == null) { call.reject("no-root"); return; }
        DocumentFile d = find(r, call.getString("path", ""), false);
        if (d == null || !d.isDirectory()) { call.reject("not-found"); return; }
        JSArray arr = new JSArray();
        for (DocumentFile f : d.listFiles()) {
            if (f.getName() == null) continue;
            JSObject e = new JSObject();
            e.put("name", f.getName());
            e.put("isDir", f.isDirectory());
            arr.put(e);
        }
        JSObject o = new JSObject();
        o.put("entries", arr);
        call.resolve(o);
    }

    @PluginMethod
    public void mkdir(PluginCall call) {
        DocumentFile r = root();
        if (r == null) { call.reject("no-root"); return; }
        if (find(r, call.getString("path", ""), true) == null) {
            call.reject("mkdir-failed");
            return;
        }
        call.resolve();
    }

    @PluginMethod
    public void remove(PluginCall call) {
        DocumentFile r = root();
        if (r == null) { call.reject("no-root"); return; }
        String path = call.getString("path", "");
        if (path.isEmpty()) { call.reject("empty-path"); return; }
        DocumentFile f = find(r, path, false);
        if (f != null) f.delete();
        call.resolve();
    }

    @PluginMethod
    public void open(PluginCall call) {
        DocumentFile r = root();
        if (r == null) { call.reject("no-root"); return; }
        DocumentFile f = find(r, call.getString("path", ""), false);
        if (f == null || f.isDirectory()) { call.reject("not-found"); return; }
        String mime = f.getType();
        if (mime == null) mime = "*/*";
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(f.getUri(), mime);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            getActivity().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("no-app");
        }
    }

    @PluginMethod
    public void readText(PluginCall call) {
        DocumentFile r = root();
        if (r == null) { call.reject("no-root"); return; }
        DocumentFile f = find(r, call.getString("path", ""), false);
        JSObject o = new JSObject();
        if (f == null || !f.isFile()) {
            o.put("text", "");
            call.resolve(o);
            return;
        }
        try (InputStream in = getContext().getContentResolver().openInputStream(f.getUri())) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            o.put("text", new String(bo.toByteArray(), StandardCharsets.UTF_8));
            call.resolve(o);
        } catch (Exception e) {
            call.reject("read-failed");
        }
    }

    @PluginMethod
    public void writeText(PluginCall call) {
        DocumentFile r = root();
        if (r == null) { call.reject("no-root"); return; }
        String path = call.getString("path", "");
        String text = call.getString("text", "");
        int idx = path.lastIndexOf('/');
        DocumentFile dir = find(r, idx < 0 ? "" : path.substring(0, idx), true);
        String name = path.substring(idx + 1);
        if (dir == null || name.isEmpty()) { call.reject("write-failed"); return; }
        DocumentFile f = dir.findFile(name);
        if (f == null) f = dir.createFile("application/json", name);
        if (f == null) { call.reject("write-failed"); return; }
        try (OutputStream out = getContext().getContentResolver().openOutputStream(f.getUri(), "wt")) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            call.resolve();
        } catch (Exception e) {
            call.reject("write-failed");
        }
    }

    /** Abre el selector del sistema y COPIA los archivos elegidos a la carpeta "dir" (sin pasar por JS). */
    @PluginMethod
    public void importFiles(PluginCall call) {
        if (root() == null) { call.reject("no-root"); return; }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(call, i, "importResult");
    }

    @ActivityCallback
    private void importResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        final JSObject o = new JSObject();
        final JSArray names = new JSArray();
        Intent data = result.getData();
        if (result.getResultCode() != Activity.RESULT_OK || data == null) {
            o.put("files", names);
            call.resolve(o);
            return;
        }
        final List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int k = 0; k < data.getClipData().getItemCount(); k++) {
                uris.add(data.getClipData().getItemAt(k).getUri());
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        final String dirPath = call.getString("dir", "");
        // La copia puede tardar con archivos grandes: se hace fuera del hilo principal.
        new Thread(() -> {
            try {
                DocumentFile r = root();
                DocumentFile dir = r == null ? null : find(r, dirPath, true);
                if (dir == null) { call.reject("no-root"); return; }
                for (Uri u : uris) {
                    String mime = getContext().getContentResolver().getType(u);
                    if (mime == null) mime = "application/octet-stream";
                    DocumentFile target = dir.createFile(mime, displayName(u));
                    if (target == null) continue;
                    try (InputStream in = getContext().getContentResolver().openInputStream(u);
                         OutputStream out = getContext().getContentResolver().openOutputStream(target.getUri())) {
                        byte[] b = new byte[65536];
                        int n;
                        while ((n = in.read(b)) > 0) out.write(b, 0, n);
                        names.put(target.getName());
                    } catch (Exception e) {
                        target.delete();
                    }
                }
                o.put("files", names);
                call.resolve(o);
            } catch (Exception e) {
                call.reject("import-failed");
            }
        }).start();
    }

    private String displayName(Uri u) {
        String name = null;
        try (Cursor c = getContext().getContentResolver().query(
                u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (Exception ignored) {
        }
        return name != null ? name : "archivo";
    }
}
