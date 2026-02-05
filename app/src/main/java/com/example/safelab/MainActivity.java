package com.example.safelab;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashSet;
import java.util.Set;

import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.BufferedInputStream;

import java.io.InputStream;


import androidx.appcompat.app.AppCompatActivity;
import androidx.exifinterface.media.ExifInterface;

import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;
import net.dongliu.apk.parser.bean.UseFeature;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_FILE_REQUEST = 1;
    private TextView resultText;
    private Uri lastUri;


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        resultText = findViewById(R.id.resultText);
        Button pickButton = findViewById(R.id.pickButton);

        pickButton.setOnClickListener(v -> {

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");

            String[] mimeTypes = {
                    "application/vnd.android.package-archive",
                    "image/jpeg",
                    "image/png",
                    "application/pdf",
                    "application/zip"
            };
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

            startActivityForResult(
                    Intent.createChooser(intent, "Select file"),
                    PICK_FILE_REQUEST
            );
        });
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            lastUri = uri;

            File tempFile = FileUtil.copyUriToTempFile(this, uri);
            if (tempFile == null) {
                resultText.setText("Failed to access file");
                resultText.setTextColor(Color.RED);
                return;
            }

            String name = tempFile.getName().toLowerCase();
            if (name.endsWith(".apk")) {
                analyzeApk(tempFile);
            } else if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) {
                analyzeImage(tempFile);
            }
            else if (name.endsWith(".pdf")) {
                analyzePdf(tempFile);
            }
            else if (name.endsWith(".zip")) {
                analyzeZip(tempFile);
            }

            else {
                resultText.setText("Unsupported file type");
                resultText.setTextColor(Color.YELLOW);
            }
        }
    }

    // ==================================================
    // MODULE 1: APK ANALYSIS
    // ==================================================
    private void analyzeApk(File apkFileObject) {
        try (ApkFile apkFile = new ApkFile(apkFileObject)) {

            ApkMeta meta = apkFile.getApkMeta();
            StringBuilder result = new StringBuilder();
            int riskScore = 0;
            boolean trusted = isTrustedApp(meta.getPackageName());

            result.append("=== APP IDENTITY ===\n");
            result.append("Name: ").append(meta.getLabel()).append("\n");
            result.append("Package: ").append(meta.getPackageName()).append("\n");
            result.append("Target SDK: ").append(meta.getTargetSdkVersion()).append("\n\n");

            if (trusted) {
                result.append("✔ Trusted Application Detected\n\n");
                riskScore -= 4;
            }

            List<String> features = new ArrayList<>();
            for (UseFeature f : meta.getUsesFeatures()) {
                features.add(f.getName());
            }

            result.append("=== PERMISSION ANALYSIS ===\n");
            for (String perm : meta.getUsesPermissions()) {
                String shortName = perm.substring(perm.lastIndexOf(".") + 1);

                if (perm.equals("android.permission.CAMERA")) {
                    if (features.contains("android.hardware.camera")
                            || features.contains("android.hardware.camera.any")) {
                        result.append("✅ JUSTIFIED: ").append(shortName).append("\n");
                    } else {
                        result.append("⚠️ SUSPICIOUS: ").append(shortName).append("\n");
                        riskScore += 2;
                    }
                } else if (perm.equals("android.permission.RECORD_AUDIO")) {
                    if (features.contains("android.hardware.microphone")) {
                        result.append("✅ JUSTIFIED: ").append(shortName).append("\n");
                    } else {
                        result.append("⚠️ SUSPICIOUS: ").append(shortName).append("\n");
                        riskScore += 2;
                    }
                } else if (isAlwaysDangerous(perm)) {
                    if (trusted) {
                        result.append("⚠ SYSTEM PERMISSION (Trusted): ").append(shortName).append("\n");
                        riskScore += 1;
                    } else {
                        result.append("❌ HIGH RISK: ").append(shortName).append("\n");
                        riskScore += 5;
                    }
                } else if (isDangerousPermission(perm)) {
                    result.append("⚠️ RISKY: ").append(shortName).append("\n");
                    riskScore += 2;
                }
            }

            result.append("\n=== STRUCTURE ANALYSIS ===\n");
            String manifest = apkFile.getManifestXml();
            int services = manifest.split("<service").length - 1;
            int receivers = manifest.split("<receiver").length - 1;

            result.append("Services: ").append(services).append("\n");
            result.append("Receivers: ").append(receivers).append("\n");

            if (!trusted && (services > 5 || receivers > 5)) {
                result.append("⚠ Excessive background components\n");
                riskScore += 3;
            }

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
            resultText.setText("APK analysis failed: " + e.getMessage());
            resultText.setTextColor(Color.RED);
        }
    }

    // ==================================================
    // MODULE 2.1: ADVANCED IMAGE ANALYSIS
    // ==================================================
    private void analyzeImage(File imageFile) {
        try (InputStream is = getContentResolver().openInputStream(lastUri)) {

            ExifInterface exif = new ExifInterface(is);
            StringBuilder result = new StringBuilder();
            int riskScore = 0;

            result.append("=== IMAGE SECURITY ANALYSIS ===\n");
            result.append("File: ").append(imageFile.getName()).append("\n\n");

            // GPS
            String lat = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
            String lon = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);
            if (lat != null && lon != null) {
                double latitude = convertToDegree(lat);
                double longitude = convertToDegree(lon);
                result.append("📍 GPS Location Found\n");
                result.append("Lat: ").append(latitude).append("\n");
                result.append("Lon: ").append(longitude).append("\n");
                riskScore += 4;
            }

            // Timestamp mismatch
            String dt = exif.getAttribute(ExifInterface.TAG_DATETIME);
            String dtOrig = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
            if (dt != null && dtOrig != null && !dt.equals(dtOrig)) {
                result.append("⚠ Timestamp mismatch detected\n");
                riskScore += 2;
            }

            // Orientation
            String orientation = exif.getAttribute(ExifInterface.TAG_ORIENTATION);
            if (orientation != null &&
                    !orientation.equals(String.valueOf(ExifInterface.ORIENTATION_NORMAL))) {
                result.append("⚠ Image orientation modified\n");
                riskScore += 1;
            }

            // Software
            String software = exif.getAttribute(ExifInterface.TAG_SOFTWARE);
            if (software != null) {
                result.append("🛠 Software: ").append(software).append("\n");
                if (software.toLowerCase().contains("editor")) riskScore++;
            }

            // Metadata density
            int metaCount = 0;
            for (String tag : new String[]{
                    ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_SOFTWARE,
                    ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_USER_COMMENT
            }) {
                if (exif.getAttribute(tag) != null) metaCount++;
            }
            if (metaCount >= 4) {
                result.append("⚠ Excessive metadata detected\n");
                riskScore += 2;
            }

            // Hidden comment
            String comment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT);
            if (comment != null && comment.length() > 10) {
                result.append("⚠ Hidden user comment found\n");
                riskScore += 3;
            }

            result.append("\n=== FINAL VERDICT ===\n");
            if (riskScore >= 7) {
                result.append("🔴 HIGH RISK IMAGE\n");
                resultText.setTextColor(Color.RED);
            } else if (riskScore >= 3) {
                result.append("🟠 MEDIUM RISK IMAGE\n");
                resultText.setTextColor(Color.rgb(255,165,0));
            } else {
                result.append("🟢 SAFE IMAGE\n");
                resultText.setTextColor(Color.GREEN);
            }

            resultText.setText(result.toString());

        } catch (Exception e) {
            resultText.setText("Image analysis failed: " + e.getMessage());
            resultText.setTextColor(Color.RED);
        }
    }
    private void analyzePdf(File pdfFile) {
        try (InputStream is = getContentResolver().openInputStream(lastUri)) {

            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            String content = new String(buffer);

            StringBuilder result = new StringBuilder();
            int riskScore = 0;

            result.append("=== PDF SECURITY ANALYSIS ===\n");
            result.append("File: ").append(pdfFile.getName()).append("\n\n");

            // JavaScript
            if (content.contains("/JavaScript") || content.contains("/JS")) {
                result.append("❌ Embedded JavaScript detected\n");
                riskScore += 5;
            }

            // Auto execution
            if (content.contains("/OpenAction") || content.contains("/AA")) {
                result.append("⚠ Auto-execution action detected\n");
                riskScore += 3;
            }

            // 🔗 URL Extraction
            Set<String> urls = extractUrls(content);
            if (!urls.isEmpty()) {
                result.append("\n🔗 URLs Found in PDF:\n");
                for (String url : urls) {
                    result.append(" - ").append(url).append("\n");
                }
                riskScore += 3;
            }

            // Embedded files
            if (content.contains("/EmbeddedFile") || content.contains("/Filespec")) {
                result.append("❌ Embedded file detected\n");
                riskScore += 5;
            }

            // Launch action
            if (content.contains("/Launch")) {
                result.append("❌ Launch action detected\n");
                riskScore += 5;
            }

            // Obfuscation
            if (content.contains("/FlateDecode") || content.contains("/ASCIIHexDecode")) {
                result.append("⚠ Obfuscated content detected\n");
                riskScore += 2;
            }

            // Object count
            int objCount = content.split("obj").length - 1;
            if (objCount > 500) {
                result.append("⚠ Excessive object count (PDF bomb hint)\n");
                riskScore += 3;
            }

            // Encryption
            if (content.contains("/Encrypt")) {
                result.append("⚠ Encrypted PDF detected\n");
                riskScore += 2;
            }

            // Size anomaly
            if (pdfFile.length() > 20 * 1024 * 1024) {
                result.append("⚠ Large PDF size (possible PDF bomb)\n");
                riskScore += 2;
            }

            // Final verdict
            result.append("\n=== FINAL VERDICT ===\n");
            if (riskScore >= 10) {
                result.append("🔴 HIGH RISK PDF\n");
                resultText.setTextColor(Color.RED);
            } else if (riskScore >= 5) {
                result.append("🟠 MEDIUM RISK PDF\n");
                resultText.setTextColor(Color.rgb(255,165,0));
            } else {
                result.append("🟢 SAFE PDF\n");
                resultText.setTextColor(Color.GREEN);
            }

            resultText.setText(result.toString());

        } catch (Exception e) {
            resultText.setText("PDF analysis failed: " + e.getMessage());
            resultText.setTextColor(Color.RED);
        }
    }

    private void analyzeZip(File zipFile) {
        int riskScore = 0;
        int fileCount = 0;
        int nestedZipCount = 0;

        StringBuilder result = new StringBuilder();
        result.append("=== ZIP SECURITY ANALYSIS ===\n");
        result.append("File: ").append(zipFile.getName()).append("\n\n");

        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zipFile)) {

            java.util.Enumeration<? extends ZipEntry> entries = zf.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                fileCount++;

                String name = entry.getName().toLowerCase();
                result.append("• ").append(name).append("\n");

                // 🔴 Dangerous executables
                if (name.endsWith(".exe") || name.endsWith(".apk")
                        || name.endsWith(".bat") || name.endsWith(".sh")
                        || name.endsWith(".cmd") || name.endsWith(".ps1")) {
                    result.append("   ❌ Dangerous executable detected\n");
                    riskScore += 5;
                }

                // 🧨 Nested ZIP
                if (name.endsWith(".zip")) {
                    nestedZipCount++;
                    result.append("   ⚠ Nested ZIP detected\n");
                    riskScore += 3;
                }

                // 🎭 Double-extension trick
                if (name.matches(".*\\.(jpg|png|pdf)\\.(exe|apk|bat|sh)")) {
                    result.append("   ❌ Double-extension attack detected\n");
                    riskScore += 5;
                }
            }

            // 📦 ZIP bomb heuristics
            if (fileCount > 1000) {
                result.append("\n⚠ Excessive file count (possible ZIP bomb)\n");
                riskScore += 4;
            }

            if (nestedZipCount > 5) {
                result.append("⚠ Multiple nested ZIP files detected\n");
                riskScore += 3;
            }

            // FINAL VERDICT
            result.append("\n=== FINAL VERDICT ===\n");
            if (riskScore >= 10) {
                result.append("🔴 HIGH RISK ZIP ARCHIVE\n");
                resultText.setTextColor(Color.RED);
            } else if (riskScore >= 5) {
                result.append("🟠 MEDIUM RISK ZIP ARCHIVE\n");
                resultText.setTextColor(Color.rgb(255,165,0));
            } else {
                result.append("🟢 SAFE ZIP ARCHIVE\n");
                resultText.setTextColor(Color.GREEN);
            }

            resultText.setText(result.toString());

        } catch (Exception e) {
            resultText.setText("ZIP analysis failed: " + e.getMessage());
            resultText.setTextColor(Color.RED);
        }
    }








    // ==================================================
    // HELPERS
    // ==================================================
    private double convertToDegree(String dms) {
        String[] parts = dms.split(",");
        return convertFraction(parts[0])
                + convertFraction(parts[1]) / 60
                + convertFraction(parts[2]) / 3600;
    }

    private double convertFraction(String s) {
        String[] p = s.split("/");
        return Double.parseDouble(p[0]) / Double.parseDouble(p[1]);
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
                "android.permission.READ_SMS"
        ).contains(permission);
    }

    private boolean isTrustedApp(String packageName) {
        return Arrays.asList(
                "com.whatsapp",
                "com.google.android.gm",
                "com.google.android.youtube",
                "com.facebook.katana"
        ).contains(packageName);
    }
    private Set<String> extractUrls(String content) {
        Set<String> urls = new HashSet<>();

        String urlRegex = "(https?:\\/\\/[^\\s<>\"']+|www\\.[^\\s<>\"']+)";
        Pattern pattern = Pattern.compile(urlRegex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            urls.add(matcher.group());
        }
        return urls;
    }

}
