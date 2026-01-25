package com.example.safelab;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;
import net.dongliu.apk.parser.bean.UseFeature;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_APK_REQUEST = 1;
    private TextView resultText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        resultText = findViewById(R.id.resultText);
        Button pickButton = findViewById(R.id.pickButton);

        pickButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/vnd.android.package-archive");
            startActivityForResult(intent, PICK_APK_REQUEST);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_APK_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                File apkFile = FileUtil.copyUriToTempFile(this, uri);
                if (apkFile != null) {
                    analyzeApk(apkFile);
                }
            }
        }
    }

    // ================= MAIN ANALYSIS =================
    private void analyzeApk(File apkFileObject) {

        try (ApkFile apkFile = new ApkFile(apkFileObject)) {

            ApkMeta meta = apkFile.getApkMeta();
            StringBuilder result = new StringBuilder();
            int riskScore = 0;

            boolean trusted = isTrustedApp(meta.getPackageName());

            // ================= APP IDENTITY =================
            result.append("=== APP IDENTITY ===\n");
            result.append("Name: ").append(meta.getLabel()).append("\n");
            result.append("Package: ").append(meta.getPackageName()).append("\n");
            result.append("Target SDK: ").append(meta.getTargetSdkVersion()).append("\n\n");

            if (trusted) {
                result.append("✔ Trusted Application Detected\n\n");
                riskScore -= 4; // dampening for trusted apps
            }

            // ================= FEATURE MAP =================
            List<String> featureNames = new ArrayList<>();
            for (UseFeature f : meta.getUsesFeatures()) {
                featureNames.add(f.getName());
            }

            // ================= PERMISSION ANALYSIS =================
            result.append("=== PERMISSION ANALYSIS ===\n");

            for (String perm : meta.getUsesPermissions()) {
                String shortName = perm.substring(perm.lastIndexOf(".") + 1);

                // CAMERA
                if (perm.equals("android.permission.CAMERA")) {
                    if (featureNames.contains("android.hardware.camera")
                            || featureNames.contains("android.hardware.camera.any")) {
                        result.append("✅ JUSTIFIED: ").append(shortName).append("\n");
                    } else {
                        result.append("⚠️ SUSPICIOUS: ").append(shortName).append("\n");
                        riskScore += 2;
                    }
                }

                // MICROPHONE
                else if (perm.equals("android.permission.RECORD_AUDIO")) {
                    if (featureNames.contains("android.hardware.microphone")) {
                        result.append("✅ JUSTIFIED: ").append(shortName).append("\n");
                    } else {
                        result.append("⚠️ SUSPICIOUS: ").append(shortName).append("\n");
                        riskScore += 2;
                    }
                }

                // SYSTEM-LEVEL PERMISSIONS
                else if (isAlwaysDangerous(perm)) {
                    if (trusted) {
                        result.append("⚠ SYSTEM PERMISSION (Trusted Context): ")
                                .append(shortName).append("\n");
                        riskScore += 1;
                    } else {
                        result.append("❌ HIGH RISK: ").append(shortName).append("\n");
                        riskScore += 5;
                    }
                }

                // NORMAL DANGEROUS
                else if (isDangerousPermission(perm)) {
                    result.append("⚠️ RISKY: ").append(shortName).append("\n");
                    riskScore += 2;
                }
            }

            // ================= STRUCTURE ANALYSIS =================
            result.append("\n=== STRUCTURE ANALYSIS ===\n");
            String manifest = apkFile.getManifestXml();

            int activityCount = manifest.split("<activity").length - 1;
            int serviceCount = manifest.split("<service").length - 1;
            int receiverCount = manifest.split("<receiver").length - 1;

            result.append("Activities: ").append(activityCount).append("\n");
            result.append("Services: ").append(serviceCount).append("\n");
            result.append("Receivers: ").append(receiverCount).append("\n");

            if (!trusted && (serviceCount > 5 || receiverCount > 5)) {
                result.append("⚠ Excessive background components detected\n");
                riskScore += 3;
            } else if (trusted && (serviceCount > 5 || receiverCount > 5)) {
                result.append("ℹ Large enterprise app structure (Allowed)\n");
            }

            // ================= SIGNATURE CHECK =================
            result.append("\n=== SIGNATURE ===\n");
            if (!apkFile.getApkSingers().isEmpty()) {
                result.append("Signed APK: YES\n");
            } else {
                result.append("Signed APK: NO\n");
                riskScore += 5;
            }

            // ================= FINAL VERDICT =================
            result.append("\n=== FINAL VERDICT ===\n");

            if (riskScore >= 12) {
                result.append("🔴 HIGH RISK APPLICATION\n");
                resultText.setTextColor(Color.RED);
            } else if (riskScore >= 6) {
                result.append("🟠 MEDIUM RISK APPLICATION\n");
                resultText.setTextColor(Color.rgb(255, 165, 0));
            } else {
                result.append("🟢 LOW / TRUSTED APPLICATION\n");
                resultText.setTextColor(Color.GREEN);
            }

            resultText.setText(result.toString());

        } catch (Exception e) {
            resultText.setText("Analysis Failed: " + e.getMessage());
            resultText.setTextColor(Color.RED);
        }
    }

    private boolean isAlwaysDangerous(String permission) {
        return Arrays.asList(
                "android.permission.SEND_SMS",
                "android.permission.RECEIVE_BOOT_COMPLETED",
                "android.permission.SYSTEM_ALERT_WINDOW",
                "android.permission.REQUEST_INSTALL_PACKAGES"
        ).contains(permission);
    }

    private boolean isDangerousPermission(String permission) {
        return Arrays.asList(
                "android.permission.READ_CONTACTS",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.READ_SMS",
                "android.permission.RECORD_AUDIO"
        ).contains(permission);
    }

    private boolean isTrustedApp(String packageName) {
        List<String> trustedApps = Arrays.asList(
                "com.whatsapp",
                "com.google.android.gm",
                "com.google.android.youtube",
                "com.facebook.katana"
        );
        return trustedApps.contains(packageName);
    }
}
