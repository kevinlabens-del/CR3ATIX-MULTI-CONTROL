package fr.creatix.multicontrol;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.security.auth.x500.X500Principal;

/** Foreground, visible, LAN-only receiver; stops when the user taps stop. */
public class AndroidReceiverService extends Service {
    static volatile String pin = "";
    static volatile String fingerprint = "";
    static volatile String error = "";
    private final ExecutorService workers = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private ServerSocket server;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel("receiver", "Receiver Multi Control", NotificationManager.IMPORTANCE_LOW));
        Intent stop = new Intent(this, AndroidReceiverService.class).setAction("STOP");
        PendingIntent pending = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification notification = new Notification.Builder(this, "receiver")
                .setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle("CR3@TIX Receiver actif")
                .setContentText("Connexion locale visible · toucher Arrêter pour couper")
                .addAction(android.R.drawable.ic_delete, "Arrêter", pending).build();
        startForeground(31, notification);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (!running) { running = true; workers.execute(this::serve); }
        return START_NOT_STICKY;
    }
    private SSLContext tls() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias("multicontrol_receiver")) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder("multicontrol_receiver", KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setCertificateSubject(new X500Principal("CN=CR3ATIX Android Receiver"))
                    .build());
            generator.generateKeyPair(); store.load(null);
        }
        Certificate cert = store.getCertificate("multicontrol_receiver");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
        StringBuilder encoded = new StringBuilder();
        for (byte b : digest) encoded.append(String.format(Locale.ROOT, "%02X", b & 255));
        fingerprint = encoded.toString();
        KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(store, null);
        SSLContext context = SSLContext.getInstance("TLS"); context.init(managers.getKeyManagers(), null, new SecureRandom());
        return context;
    }
    private void serve() {
        try {
            SSLContext context = tls();
            pin = String.format(Locale.ROOT, "%06d", new SecureRandom().nextInt(1000000));
            server = context.getServerSocketFactory().createServerSocket(45721);
            server.setSoTimeout(1000);
            while (running) {
                try {
                    Socket connection = server.accept();
                    workers.execute(() -> handle((SSLSocket)connection));
                } catch (java.net.SocketTimeoutException ignored) {}
            }
        } catch (Exception ex) { error = ex.getMessage(); Log.e("MultiControl", "Receiver", ex); }
        finally { stopSelf(); }
    }
    private void handle(SSLSocket socket) {
        try (SSLSocket peer = socket) {
            peer.setSoTimeout(30000);
            BufferedReader input = new BufferedReader(new InputStreamReader(peer.getInputStream(), "UTF-8"));
            OutputStreamWriter output = new OutputStreamWriter(peer.getOutputStream(), "UTF-8");
            String hello = input.readLine();
            if (hello == null || hello.length() > 8192) return;
            JSONObject pair = new JSONObject(hello);
            if (!pair.optString("type").equals("pair") || !MessageDigest.isEqual(
                    pair.optString("pin").getBytes("UTF-8"), pin.getBytes("UTF-8"))) {
                output.write("{\"ok\":false}\n"); output.flush(); return;
            }
            output.write("{\"ok\":true}\n"); output.flush();
            String line;
            while (running && (line = input.readLine()) != null) {
                if (line.length() > 8192) break;
                final JSONObject command = new JSONObject(line);
                main.post(() -> {
                    ReceiverAccessibility accessibility = ReceiverAccessibility.active;
                    if (accessibility != null) accessibility.command(command);
                });
            }
        } catch (Exception ex) { Log.w("MultiControl", "Connection closed", ex); }
    }
    @Override public void onDestroy() {
        running = false; pin = "";
        try { if (server != null) server.close(); } catch (Exception ignored) {}
        workers.shutdownNow(); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
