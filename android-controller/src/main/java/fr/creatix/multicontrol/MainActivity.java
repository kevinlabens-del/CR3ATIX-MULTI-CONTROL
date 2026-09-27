package fr.creatix.multicontrol;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SSLSocket socket;
    private OutputStreamWriter output;
    private TextView status;
    private EditText host, pin, fingerprint, textInput;
    private boolean remote = false;
    private float lastX, lastY, scrollY;
    private long downTime;
    private int oldButtons;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setPadding(22, 22, 22, 22);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(11, 17, 30));
        scroll.addView(root);
        addLabel(root, "CR3@TIX MULTI CONTROL", 25);
        addLabel(root, "Receiver Windows · même réseau Wi-Fi · TLS avec empreinte vérifiée", 13);
        host = field(root, "IP locale du PC (ex. 192.168.1.42)", "host");
        pin = field(root, "Code temporaire affiché sur le PC", "pin");
        fingerprint = field(root, "Empreinte SHA-256 affichée sur le PC (64 caractères)", "fingerprint");
        android.content.SharedPreferences prefs = getPreferences(0);
        host.setText(prefs.getString("host", ""));
        fingerprint.setText(prefs.getString("fingerprint", ""));
        button(root, "CONNECTER", () -> connect());
        status = addLabel(root, "LOCAL · non connecté", 16);
        button(root, "LOCAL ↔ DISTANT", () -> {
            remote = !remote && output != null;
            status.setText(remote ? "DISTANT · " + host.getText() : "LOCAL · contrôle du téléphone");
            if (remote) getWindow().getDecorView().requestPointerCapture();
            else getWindow().getDecorView().releasePointerCapture();
        });
        addLabel(root, "TOUCHPAD · glisser pour déplacer · toucher pour cliquer · deux doigts pour défiler", 14);
        View pad = new View(this);
        pad.setBackgroundColor(Color.rgb(27, 52, 78));
        root.addView(pad, new LinearLayout.LayoutParams(-1, 260));
        pad.setOnTouchListener((v, e) -> {
            if (!remote) return false;
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                lastX = e.getX(); lastY = e.getY(); downTime = System.currentTimeMillis();
                return true;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (e.getPointerCount() >= 2) {
                    int step = Math.round((scrollY - e.getY()) / 28);
                    if (step != 0) { send("scroll", "steps", step); scrollY = e.getY(); }
                } else {
                    sendMove(Math.round((e.getX() - lastX) * 1.5f), Math.round((e.getY() - lastY) * 1.5f));
                }
                lastX = e.getX(); lastY = e.getY(); return true;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) { scrollY = e.getY(); return true; }
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                if (System.currentTimeMillis() - downTime < 230 && Math.abs(e.getX()-lastX) < 12) send("click", "button", "left");
                return true;
            }
            return true;
        });
        LinearLayout row = new LinearLayout(this); root.addView(row);
        button(row, "Clic droit", () -> send("click", "button", "right"));
        button(row, "Milieu", () -> send("click", "button", "middle"));
        textInput = field(root, "Texte à envoyer", "text");
        button(root, "ENVOYER TEXTE", () -> { send("text", "text", textInput.getText().toString()); textInput.setText(""); });
        row = new LinearLayout(this); root.addView(row);
        for (String key : new String[]{"enter", "esc", "tab", "backspace"}) {
            final String k = key; button(row, key, () -> send("key", "key", k));
        }
        row = new LinearLayout(this); root.addView(row);
        for (String key : new String[]{"playpause", "nexttrack", "prevtrack", "volumeup"}) {
            final String k = key; button(row, key, () -> send("media", "key", k));
        }
        button(root, "DICTÉE / COMMANDE VOCALE", () -> {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            try { startActivityForResult(intent, 1); } catch (Exception ex) { toast("Reconnaissance vocale indisponible"); }
        });
        button(root, "DÉCONNECTER", () -> disconnect());
        setContentView(scroll);
        getWindow().getDecorView().setOnCapturedPointerListener((v, e) -> {
            if (!remote) return false;
            if (e.getAction() == MotionEvent.ACTION_MOVE) {
                sendMove(Math.round(e.getX()), Math.round(e.getY())); return true;
            }
            return false;
        });
    }

    private TextView addLabel(LinearLayout root, String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextColor(Color.rgb(220, 241, 255));
        view.setTextSize(size); view.setPadding(0, 12, 0, 10); root.addView(view); return view;
    }
    private EditText field(LinearLayout root, String hint, String id) {
        EditText field = new EditText(this); field.setHint(hint); field.setSingleLine(true);
        field.setTextColor(Color.WHITE); field.setHintTextColor(0xff9caac0);
        root.addView(field, new LinearLayout.LayoutParams(-1, -2)); return field;
    }
    private void button(LinearLayout root, String label, Runnable callback) {
        Button button = new Button(this); button.setText(label); root.addView(button);
        button.setOnClickListener(v -> callback.run());
    }
    private void toast(String message) { runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show()); }
    private static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder(); for (byte b : bytes) s.append(String.format(Locale.ROOT, "%02X", b & 255)); return s.toString();
    }
    private void connect() {
        final String ip = host.getText().toString().trim();
        final String code = pin.getText().toString().trim();
        final String expected = fingerprint.getText().toString().replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.ROOT);
        if (expected.length() != 64 || !code.matches("[0-9]{6}")) { toast("Saisis le code et les 64 caractères de l'empreinte TLS"); return; }
        io.execute(() -> {
            try {
                disconnectSocket();
                TrustManager[] trust = { new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                }};
                SSLContext context = SSLContext.getInstance("TLS"); context.init(null, trust, new SecureRandom());
                SSLSocket connected = (SSLSocket) context.getSocketFactory().createSocket();
                connected.connect(new InetSocketAddress(ip, 45721), 5000); connected.setSoTimeout(8000);
                connected.startHandshake();
                String actual = hex(MessageDigest.getInstance("SHA-256").digest(connected.getSession().getPeerCertificates()[0].getEncoded()));
                if (!MessageDigest.isEqual(expected.getBytes("US-ASCII"), actual.getBytes("US-ASCII"))) {
                    connected.close(); throw new SecurityException("Empreinte TLS incorrecte : vérifie-la sur le PC");
                }
                OutputStreamWriter writer = new OutputStreamWriter(connected.getOutputStream(), "UTF-8");
                writer.write(new JSONObject().put("type", "pair").put("pin", code).toString() + "\n"); writer.flush();
                JSONObject reply = new JSONObject(new BufferedReader(new InputStreamReader(connected.getInputStream(), "UTF-8")).readLine());
                if (!reply.optBoolean("ok")) { connected.close(); throw new SecurityException("Code refusé"); }
                socket = connected; output = writer;
                getPreferences(0).edit().putString("host", ip).putString("fingerprint", expected).apply();
                runOnUiThread(() -> { remote = true; status.setText("DISTANT · connecté à " + ip); });
            } catch (Exception ex) { toast("Connexion : " + ex.getMessage()); }
        });
    }
    private void send(String type, String field, Object value) {
        if (!remote) return;
        try { JSONObject msg = new JSONObject().put("type", type).put(field, value); transmit(msg); } catch (Exception ignored) {}
    }
    private void sendMove(int dx, int dy) {
        if (!remote || dx == 0 && dy == 0) return;
        try { transmit(new JSONObject().put("type", "move").put("dx", dx).put("dy", dy)); } catch (Exception ignored) {}
    }
    private void transmit(JSONObject msg) {
        io.execute(() -> {
            try { if (output != null) { output.write(msg.toString() + "\n"); output.flush(); } }
            catch (Exception ex) { runOnUiThread(() -> { remote = false; status.setText("Connexion perdue"); }); disconnectSocket(); }
        });
    }
    private void disconnectSocket() {
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        socket = null; output = null;
    }
    private void disconnect() {
        remote = false; status.setText("LOCAL · déconnecté");
        getWindow().getDecorView().releasePointerCapture(); io.execute(this::disconnectSocket);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (remote && e.getAction() == KeyEvent.ACTION_DOWN) {
            int code = e.getKeyCode(); String special = null;
            switch(code) {
                case KeyEvent.KEYCODE_ENTER: special="enter"; break;
                case KeyEvent.KEYCODE_DEL: special="backspace"; break;
                case KeyEvent.KEYCODE_ESCAPE: special="esc"; break;
                case KeyEvent.KEYCODE_TAB: special="tab"; break;
                case KeyEvent.KEYCODE_DPAD_UP: special="up"; break;
                case KeyEvent.KEYCODE_DPAD_DOWN: special="down"; break;
                case KeyEvent.KEYCODE_DPAD_LEFT: special="left"; break;
                case KeyEvent.KEYCODE_DPAD_RIGHT: special="right"; break;
            }
            if (special != null) { send("key", "key", special); return true; }
            int unicode = e.getUnicodeChar();
            if (unicode >= 32 && unicode <= 0x10FFFF) { send("text", "text", new String(Character.toChars(unicode))); return true; }
        }
        return super.dispatchKeyEvent(e);
    }
    @Override public boolean onGenericMotionEvent(MotionEvent e) {
        if (remote && (e.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) {
            if (e.getAction() == MotionEvent.ACTION_SCROLL) {
                send("scroll", "steps", Math.round(e.getAxisValue(MotionEvent.AXIS_VSCROLL))); return true;
            }
            int buttons = e.getButtonState();
            if ((buttons & MotionEvent.BUTTON_PRIMARY) != 0 && (oldButtons & MotionEvent.BUTTON_PRIMARY) == 0) send("click", "button", "left");
            if ((buttons & MotionEvent.BUTTON_SECONDARY) != 0 && (oldButtons & MotionEvent.BUTTON_SECONDARY) == 0) send("click", "button", "right");
            oldButtons = buttons;
        }
        return super.onGenericMotionEvent(e);
    }
    @Override public void onPointerCaptureChanged(boolean captured) { super.onPointerCaptureChanged(captured); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 1 && result == RESULT_OK && data != null) {
            java.util.ArrayList<String> words = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (words == null || words.isEmpty()) return;
            String spoken = words.get(0).trim().toLowerCase(Locale.ROOT);
            if (spoken.equals("local")) { disconnect(); return; }
            if (spoken.equals("clic")) { send("click", "button", "left"); return; }
            if (spoken.equals("clic droit")) { send("click", "button", "right"); return; }
            if (spoken.equals("haut") || spoken.equals("bas") || spoken.equals("gauche") || spoken.equals("droite")) {
                sendMove(spoken.equals("droite") ? 60 : spoken.equals("gauche") ? -60 : 0,
                        spoken.equals("bas") ? 60 : spoken.equals("haut") ? -60 : 0); return;
            }
            send("text", "text", words.get(0));
        }
    }
    @Override protected void onDestroy() { disconnectSocket(); io.shutdownNow(); super.onDestroy(); }
}
