package com.cloudorz.openmonitor.feature.keyattestation.keystore;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Build;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.system.Os;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;

import rikka.shizuku.ShizukuApiConstants;

/**
 * Shizuku user service that runs as shell/root/system UID and installs the system
 * Android KeyStore provider via fixEnv(). This grants access to the device's original
 * hardware attestation certificate chain (Keymaster 4.1), bypassing RKP.
 */
public class KeyAttestKeyStoreService extends IKeyAttestKeyStore.Stub {
    private static final String TAG = "KAKeyStoreService";

    private final KeyStore keyStore;
    private final KeyPairGenerator keyPairGenerator;

    public KeyAttestKeyStoreService() throws Exception {
        int uid = Os.geteuid();
        if (uid < Process.FIRST_APPLICATION_UID) {
            fixEnv(uid);
        }
        keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
    }

    /**
     * Called when running as shell/root/system UID.
     * Installs the system Android KeyStore provider, enabling access to the
     * device's hardware attestation cert chain.
     */
    private static void fixEnv(int uid) throws Exception {
        // Root → seteuid to system so we can run as system
        if (uid == Process.ROOT_UID) {
            if (Os.gettid() == Os.getpid()) {
                Os.seteuid(Process.SYSTEM_UID);
            } else {
                throw new RuntimeException("ROOT_UID: tid != pid, cannot seteuid");
            }
        }

        // Initialize mainline modules (required on Android 11+ before installing provider)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Class<?> atCls = Class.forName("android.app.ActivityThread");
            Method m = atCls.getDeclaredMethod("initializeMainlineModules");
            m.invoke(null);
        }

        // Install the system Android KeyStore JCA provider.
        // This gives access to the hardware attestation certs burned at manufacture time.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Class<?> cls = Class.forName("android.security.keystore2.AndroidKeyStoreProvider");
            cls.getDeclaredMethod("install").invoke(null);
        } else {
            Class<?> cls = Class.forName("android.security.keystore.AndroidKeyStoreProvider");
            cls.getDeclaredMethod("install").invoke(null);
        }

        // Build a minimal Application context so the keystore provider can operate.
        Class<?> atCls = Class.forName("android.app.ActivityThread");
        Object activityThread = atCls.getDeclaredMethod("systemMain").invoke(null);

        Method getSystemContextMethod = atCls.getDeclaredMethod("getSystemContext");
        Context systemContext = (Context) getSystemContextMethod.invoke(activityThread);

        // Use "android" package if system UID, "com.android.shell" for shell UID
        String pkg = (Os.geteuid() == Process.SYSTEM_UID) ? "android" : "com.android.shell";
        int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
        Context ctx = systemContext.createPackageContext(pkg, flags);

        Field mPackageInfoField = ctx.getClass().getDeclaredField("mPackageInfo");
        mPackageInfoField.setAccessible(true);
        Object loadedApk = mPackageInfoField.get(ctx);

        Method makeApp = loadedApk.getClass().getDeclaredMethod(
                "makeApplication", boolean.class, Instrumentation.class);
        makeApp.setAccessible(true);
        Application app = (Application) makeApp.invoke(loadedApk, true, null);

        Field mInitialApp = atCls.getDeclaredField("mInitialApplication");
        mInitialApp.setAccessible(true);
        mInitialApp.set(activityThread, app);

        Log.d(TAG, "fixEnv done, uid=" + Os.geteuid());
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        if (code == ShizukuApiConstants.USER_SERVICE_TRANSACTION_destroy) {
            System.exit(0);
        }
        return super.onTransact(code, data, reply, flags);
    }

    @Override
    public byte[] generateKeyPair(String alias, String attestKeyAlias, boolean useStrongBox) {
        try {
            var now = new Date();

            // Step 1: create PURPOSE_ATTEST_KEY if not present (persistent, reused across sessions)
            if (attestKeyAlias != null && !keyStore.containsAlias(attestKeyAlias)) {
                var attestBuilder = new KeyGenParameterSpec.Builder(
                        attestKeyAlias, KeyProperties.PURPOSE_ATTEST_KEY)
                        .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setCertificateNotBefore(now)
                        .setAttestationChallenge(now.toString().getBytes());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && useStrongBox) {
                    attestBuilder.setIsStrongBoxBacked(true);
                }
                keyPairGenerator.initialize(attestBuilder.build());
                keyPairGenerator.generateKeyPair();
            }

            // Step 2: create ephemeral leaf PURPOSE_SIGN key
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias);
            var leafBuilder = new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setCertificateNotBefore(now)
                    .setAttestationChallenge(now.toString().getBytes());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && useStrongBox) {
                leafBuilder.setIsStrongBoxBacked(true);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && attestKeyAlias != null) {
                leafBuilder.setAttestKeyAlias(attestKeyAlias);
            }
            keyPairGenerator.initialize(leafBuilder.build());
            keyPairGenerator.generateKeyPair();
            return null; // success
        } catch (Exception e) {
            Log.e(TAG, "generateKeyPair failed", e);
            return serializeException(e);
        }
    }

    @Override
    public byte[] getCertificateChain(String alias) {
        try {
            Certificate[] chain = keyStore.getCertificateChain(alias);
            if (chain == null) return null;
            var buf = new ByteArrayOutputStream(8192);
            for (Certificate cert : chain) {
                buf.write(cert.getEncoded());
            }
            return buf.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "getCertificateChain failed", e);
            throw new IllegalStateException(e.getMessage());
        }
    }

    @Override
    public boolean containsAlias(String alias) {
        try {
            return keyStore.containsAlias(alias);
        } catch (KeyStoreException e) {
            Log.e(TAG, "containsAlias failed", e);
            throw new IllegalStateException(e.getMessage());
        }
    }

    @Override
    public void deleteEntry(String alias) {
        try {
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias);
        } catch (KeyStoreException e) {
            Log.e(TAG, "deleteEntry failed", e);
            throw new IllegalStateException(e.getMessage());
        }
    }

    @Override
    public void deleteAllEntries() {
        try {
            var aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                keyStore.deleteEntry(aliases.nextElement());
            }
        } catch (KeyStoreException e) {
            Log.e(TAG, "deleteAllEntries failed", e);
            throw new IllegalStateException(e.getMessage());
        }
    }

    private static byte[] serializeException(Exception e) {
        var buf = new ByteArrayOutputStream(2048);
        try (var out = new ObjectOutputStream(buf)) {
            out.writeObject(e);
        } catch (IOException ignored) {
        }
        return buf.toByteArray();
    }
}
