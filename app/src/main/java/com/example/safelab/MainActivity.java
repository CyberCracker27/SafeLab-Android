package com.example.safelab;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;
import net.dongliu.apk.parser.bean.UseFeature;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_FILE_REQUEST = 1;
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int MANAGE_FILES_REQUEST_CODE = 200;

    private TextView resultText;
    private Uri lastUri;
    private Map<String, FileObserver> observers = new HashMap<>();
    private boolean isMonitoringStarted = false;

    // Logging related
    private static final String LOG_DIR_NAME = "SafeLab_Logs";
    private static final String LOG_FILE_NAME = "SafeLab_Scan_History.txt";
    private File logFile;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());

    // Thread-safe set to track scanned files
    private Set<String> scannedFiles = ConcurrentHashMap.newKeySet();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        resultText = findViewById(R.id.resultText);
        Button pickButton = findViewById(R.id.pickButton);
        Button viewLogsButton = findViewById(R.id.viewLogsButton);
        Button clearLogsButton = findViewById(R.id.clearLogsButton);

        // Initialize log file
        initializeLogFile();

        checkPermissions();

        pickButton.setOnClickListener(v -> {
            if (checkStoragePermission()) {
                openFilePicker();
            } else {
                checkPermissions();
            }
        });

        viewLogsButton.setOnClickListener(v -> viewLogs());
        clearLogsButton.setOnClickListener(v -> clearLogs());
    }

    // ================= LOGGING FUNCTIONS =================

    private void initializeLogFile() {
        try {
            // Create logs directory in external storage
            File logDir;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                logDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), LOG_DIR_NAME);
            } else {
                logDir = new File(Environment.getExternalStorageDirectory(), LOG_DIR_NAME);
            }

            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            logFile = new File(logDir, LOG_FILE_NAME);
            if (!logFile.exists()) {
                logFile.createNewFile();
                writeLogHeader();
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback to app-specific storage
            logFile = new File(getExternalFilesDir(null), LOG_FILE_NAME);
            try {
                if (!logFile.exists()) {
                    logFile.createNewFile();
                    writeLogHeader();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void writeLogHeader() {
        String header = "===============================================================================\n" +
                "                            SAFELAB SCAN HISTORY LOG\n" +
                "===============================================================================\n" +
                "App Version    : 1.0\n" +
                "Device         : " + Build.MANUFACTURER + " " + Build.MODEL + "\n" +
                "Android Version: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n" +
                "Log Created    : " + dateFormat.format(new Date()) + "\n" +
                "Log Location   : " + logFile.getAbsolutePath() + "\n" +
                "===============================================================================\n\n";
        appendToLog(header);
    }

    private synchronized void appendToLog(String text) {
        try (FileWriter fw = new FileWriter(logFile, true)) {
            fw.write(text);
            fw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void logScanStart(String fileName, String fileType) {
        String logEntry = "\n" +
                "-------------------------------------------------------------------------------\n" +
                "SCAN STARTED\n" +
                "-------------------------------------------------------------------------------\n" +
                "Date & Time : " + dateFormat.format(new Date()) + "\n" +
                "File Name   : " + fileName + "\n" +
                "File Type   : " + fileType.toUpperCase() + "\n" +
                "-------------------------------------------------------------------------------\n";
        appendToLog(logEntry);
    }

    private void logScanResult(String fileName, String result, String riskLevel, int riskScore, String details) {
        String logEntry =
                "SCAN RESULT\n" +
                        "-------------------------------------------------------------------------------\n" +
                        "File Name     : " + fileName + "\n" +
                        "Risk Level    : " + riskLevel + "\n" +
                        "Risk Score    : " + riskScore + "/100\n" +
                        "-------------------------------------------------------------------------------\n" +
                        "DETAILS:\n" + details + "\n" +
                        "-------------------------------------------------------------------------------\n" +
                        "FINAL VERDICT : " + result + "\n" +
                        "===============================================================================\n\n";
        appendToLog(logEntry);
    }

    private void logError(String fileName, String error) {
        String logEntry =
                "ERROR SCANNING FILE\n" +
                        "-------------------------------------------------------------------------------\n" +
                        "Date & Time : " + dateFormat.format(new Date()) + "\n" +
                        "File Name   : " + fileName + "\n" +
                        "Error       : " + error + "\n" +
                        "===============================================================================\n\n";
        appendToLog(logEntry);
    }

    private void logSystemEvent(String event) {
        String logEntry =
                "SYSTEM EVENT\n" +
                        "-------------------------------------------------------------------------------\n" +
                        "Date & Time : " + dateFormat.format(new Date()) + "\n" +
                        "Event       : " + event + "\n" +
                        "===============================================================================\n\n";
        appendToLog(logEntry);
    }

    // FIXED: View logs method with proper FileProvider
    private void viewLogs() {
        if (logFile != null && logFile.exists()) {
            try {
                // Use FileProvider to get a content URI (works on all Android versions)
                Uri contentUri = FileProvider.getUriForFile(this,
                        getPackageName() + ".fileprovider",
                        logFile);

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(contentUri, "text/plain");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                // Add extra flags for better compatibility
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                // Try to open the file
                startActivity(Intent.createChooser(intent, "Open log file with"));

            } catch (Exception e) {
                e.printStackTrace();
                // Fallback method - try to open with simple URI
                try {
                    Uri uri = Uri.fromFile(logFile);
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, "text/plain");
                    startActivity(Intent.createChooser(intent, "Open log file with"));
                } catch (Exception ex) {
                    Toast.makeText(this, "Cannot open log file: " + e.getMessage(), Toast.LENGTH_LONG).show();

                    // Show the log file path to user
                    String message = "Log file saved at:\n" + logFile.getAbsolutePath() +
                            "\n\nYou can open it manually with a file manager app.";
                    resultText.setText(message);
                }
            }
        } else {
            Toast.makeText(this, "No log file found", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearLogs() {
        if (logFile != null && logFile.exists()) {
            try {
                // Create backup with timestamp
                String backupName = "SafeLab_Scan_History_Backup_" + fileDateFormat.format(new Date()) + ".txt";
                File backupFile = new File(logFile.getParent(), backupName);

                // Copy current log to backup
                if (logFile.renameTo(backupFile)) {
                    // Create new empty log file
                    logFile.createNewFile();
                    writeLogHeader();
                    Toast.makeText(this, "Logs cleared. Backup created: " + backupName, Toast.LENGTH_LONG).show();

                    // Log the clear event
                    logSystemEvent("Log file cleared. Backup saved as: " + backupName);

                    // Update UI
                    resultText.setText("Logs have been cleared.\nBackup saved as: " + backupName);
                } else {
                    // If rename fails, just overwrite
                    try (FileWriter fw = new FileWriter(logFile, false)) {
                        writeLogHeader();
                        Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show();
                        resultText.setText("Logs have been cleared.");
                    }
                }
            } catch (IOException e) {
                Toast.makeText(this, "Error clearing logs: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this, "No log file to clear", Toast.LENGTH_SHORT).show();
        }
    }

    private String getRiskLevelText(int riskScore) {
        if (riskScore >= 10) return "CRITICAL RISK";
        else if (riskScore >= 5) return "HIGH RISK";
        else if (riskScore > 0) return "MEDIUM RISK";
        else return "LOW RISK";
    }

    private int getRiskColor(int riskScore) {
        if (riskScore >= 10) return Color.RED;
        else if (riskScore >= 5) return Color.rgb(255, 165, 0); // Orange
        else if (riskScore > 0) return Color.YELLOW;
        else return Color.GREEN;
    }

    // ================= PERMISSION HANDLING =================

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, MANAGE_FILES_REQUEST_CODE);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, MANAGE_FILES_REQUEST_CODE);
                }
                resultText.setText("Please grant 'Allow access to manage all files' permission");
                return;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
            } else {
                startMonitoring();
            }
        } else {
            startMonitoring();
        }
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                resultText.setText("Storage permission granted");
                resultText.setTextColor(Color.GREEN);
                logSystemEvent("Storage permission granted");
                startMonitoring();
            } else {
                resultText.setText("Storage permission denied - Cannot monitor files");
                resultText.setTextColor(Color.RED);
                Toast.makeText(this, "Storage permission is required for full functionality", Toast.LENGTH_LONG).show();
                logSystemEvent("Storage permission denied");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == MANAGE_FILES_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    resultText.setText("Storage permission granted");
                    logSystemEvent("MANAGE_EXTERNAL_STORAGE permission granted");
                    startMonitoring();
                } else {
                    resultText.setText("Storage permission still denied");
                    resultText.setTextColor(Color.RED);
                    logSystemEvent("MANAGE_EXTERNAL_STORAGE permission denied");
                }
            }
        } else if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null) {
            handleSelectedFile(data);
        }
    }

    private void openFilePicker() {
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
        startActivityForResult(intent, PICK_FILE_REQUEST);
    }

    private void handleSelectedFile(Intent data) {
        Uri uri = data.getData();
        if (uri == null) return;

        lastUri = uri;
        String fileName = getFileName(uri);
        File tempFile = copyUriToTempFile(uri);

        if (tempFile == null || !tempFile.exists()) {
            String errorMsg = "Failed to access file";
            resultText.setText(errorMsg);
            resultText.setTextColor(Color.RED);
            logError(fileName != null ? fileName : "Unknown", errorMsg);
            return;
        }

        String name = tempFile.getName().toLowerCase();
        resultText.setText(""); // Clear previous text

        // Log scan start
        logScanStart(fileName != null ? fileName : tempFile.getName(),
                name.substring(name.lastIndexOf(".") + 1));

        // Perform analysis based on file type
        if (name.endsWith(".apk")) {
            analyzeApk(tempFile, fileName);
        } else if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) {
            analyzeImage(tempFile, uri, fileName);
        } else if (name.endsWith(".pdf")) {
            analyzePdf(tempFile, uri, fileName);
        } else if (name.endsWith(".zip")) {
            analyzeZip(tempFile, fileName);
        } else {
            String errorMsg = "Unsupported file type";
            resultText.setText(errorMsg);
            resultText.setTextColor(Color.YELLOW);
            logError(fileName != null ? fileName : tempFile.getName(), errorMsg);
        }

        // Add to scanned files set
        if (fileName != null) {
            scannedFiles.add(fileName + "_" + dateFormat.format(new Date()));
        }

        // Clean up temp file
        if (tempFile.exists()) {
            tempFile.delete();
        }
    }

    // ================= FILE UTILITY =================

    private File copyUriToTempFile(Uri uri) {
        File tempFile = null;
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) return null;

            String fileName = getFileName(uri);
            if (fileName == null) {
                fileName = "temp_" + System.currentTimeMillis();
            }

            tempFile = new File(getCacheDir(), fileName);

            try (OutputStream outputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            return tempFile;
        } catch (IOException e) {
            e.printStackTrace();
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
            return null;
        }
    }

    private String getFileName(Uri uri) {
        String fileName = null;
        String[] projection = {android.provider.MediaStore.MediaColumns.DISPLAY_NAME};
        try (android.database.Cursor cursor = getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (fileName == null) {
            fileName = uri.getLastPathSegment();
        }

        return fileName;
    }

    private void startMonitoring() {
        if (isMonitoringStarted) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                return;
            }
        }

        try {
            File root = Environment.getExternalStorageDirectory();
            if (root.exists() && root.canRead()) {
                monitorAllFolders(root);
                isMonitoringStarted = true;
                String status = "File monitoring started\nLogs saved to: " + logFile.getAbsolutePath();
                resultText.setText(status);
                logSystemEvent("File monitoring started");
            } else {
                resultText.setText("Cannot access storage root");
                logSystemEvent("Failed to start monitoring: Cannot access storage root");
            }
        } catch (SecurityException e) {
            resultText.setText("Security exception: " + e.getMessage());
            logSystemEvent("Security exception: " + e.getMessage());
        }
    }

    // ================= APK ANALYSIS =================

    private void analyzeApk(File apkFile, String originalFileName) {
        String fileName = originalFileName != null ? originalFileName : apkFile.getName();

        try (ApkFile apkFileParser = new ApkFile(apkFile)) {
            ApkMeta meta = apkFileParser.getApkMeta();

            StringBuilder details = new StringBuilder();
            int riskScore = 0;
            List<String> riskyPermissions = new ArrayList<>();

            details.append("  App Name: ").append(meta.getLabel()).append("\n");
            details.append("  Package: ").append(meta.getPackageName()).append("\n");
            details.append("  Version: ").append(meta.getVersionName()).append("\n");
            details.append("  Min SDK: ").append(meta.getMinSdkVersion()).append("\n");
            details.append("\n  PERMISSIONS:\n");

            if (meta.getUsesPermissions() != null && !meta.getUsesPermissions().isEmpty()) {
                for (String perm : meta.getUsesPermissions()) {
                    details.append("    • ").append(perm).append("\n");
                    if (perm.contains("SEND_SMS") || perm.contains("RECEIVE_SMS") ||
                            perm.contains("READ_SMS") || perm.contains("SYSTEM_ALERT_WINDOW") ||
                            perm.contains("BIND_ACCESSIBILITY_SERVICE") || perm.contains("RECORD_AUDIO") ||
                            perm.contains("CAMERA") || perm.contains("ACCESS_FINE_LOCATION")) {
                        riskScore += 5;
                        riskyPermissions.add(perm);
                    }
                }
            } else {
                details.append("    No permissions declared\n");
            }

            details.append("\n  FEATURES:\n");
            if (meta.getUsesFeatures() != null) {
                for (UseFeature feature : meta.getUsesFeatures()) {
                    details.append("    • ").append(feature.getName()).append("\n");
                }
            }

            if (!riskyPermissions.isEmpty()) {
                details.append("\n  RISKY PERMISSIONS DETECTED:\n");
                for (String perm : riskyPermissions) {
                    details.append("    • ").append(perm).append("\n");
                }
            }

            String riskLevel = getRiskLevelText(riskScore);
            String finalVerdict;

            if (riskScore >= 10) {
                finalVerdict = "CRITICAL RISK - Malicious indicators found";
                resultText.setTextColor(Color.RED);
            } else if (riskScore >= 5) {
                finalVerdict = "HIGH RISK - Proceed with caution";
                resultText.setTextColor(Color.rgb(255, 165, 0));
            } else if (riskScore > 0) {
                finalVerdict = "MEDIUM RISK - Some concerning permissions";
                resultText.setTextColor(Color.YELLOW);
            } else {
                finalVerdict = "LOW RISK - No immediate risks detected";
                resultText.setTextColor(Color.GREEN);
            }

            StringBuilder result = new StringBuilder();
            result.append("📱 APK ANALYSIS RESULTS\n\n");
            result.append(details.toString());
            result.append("\nFINAL VERDICT: ").append(finalVerdict).append("\n");
            result.append("Risk Score: ").append(riskScore).append("/100");

            resultText.setText(result.toString());

            // Log the result
            logScanResult(fileName, finalVerdict, riskLevel, riskScore, details.toString());

        } catch (Exception e) {
            String errorMsg = "APK analysis failed: " + e.getMessage();
            resultText.setText("❌ " + errorMsg);
            resultText.setTextColor(Color.RED);
            logError(fileName, errorMsg);
            e.printStackTrace();
        }
    }

    // ================= IMAGE ANALYSIS =================

    private void analyzeImage(File imageFile, Uri uri, String originalFileName) {
        String fileName = originalFileName != null ? originalFileName : imageFile.getName();

        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) {
                String errorMsg = "Cannot open image file";
                resultText.setText(errorMsg);
                logError(fileName, errorMsg);
                return;
            }

            ExifInterface exif = new ExifInterface(is);

            StringBuilder details = new StringBuilder();

            String lat = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
            String latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF);
            String lon = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);
            String lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF);
            String datetime = exif.getAttribute(ExifInterface.TAG_DATETIME);
            String make = exif.getAttribute(ExifInterface.TAG_MAKE);
            String model = exif.getAttribute(ExifInterface.TAG_MODEL);

            details.append("  Camera Info:\n");
            if (make != null && model != null) {
                details.append("    Device: ").append(make).append(" ").append(model).append("\n");
            }

            if (datetime != null) {
                details.append("    Taken: ").append(datetime).append("\n");
            }

            details.append("\n  GPS Location:\n");
            int riskScore = 0;
            String finalVerdict;

            if (lat != null && lon != null) {
                details.append("    ⚠ GPS METADATA FOUND!\n");
                details.append("    Latitude: ").append(lat).append(" ").append(latRef).append("\n");
                details.append("    Longitude: ").append(lon).append(" ").append(lonRef).append("\n");
                riskScore = 8;
                finalVerdict = "PRIVACY RISK: Location data embedded in image";
                resultText.setTextColor(Color.RED);
            } else {
                details.append("    No GPS data found\n");
                finalVerdict = "SAFE: No location data detected";
                resultText.setTextColor(Color.GREEN);
            }

            StringBuilder result = new StringBuilder();
            result.append("🖼 IMAGE ANALYSIS\n\n");
            result.append(details.toString());
            result.append("\nFINAL VERDICT: ").append(finalVerdict);

            resultText.setText(result.toString());

            // Log the result
            String riskLevel = getRiskLevelText(riskScore);
            logScanResult(fileName, finalVerdict, riskLevel, riskScore, details.toString());

        } catch (Exception e) {
            String errorMsg = "Image analysis failed: " + e.getMessage();
            resultText.setText("❌ " + errorMsg);
            resultText.setTextColor(Color.RED);
            logError(fileName, errorMsg);
        }
    }

    // ================= PDF ANALYSIS =================

    private void analyzePdf(File pdfFile, Uri uri, String originalFileName) {
        String fileName = originalFileName != null ? originalFileName : pdfFile.getName();

        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) {
                String errorMsg = "Cannot open PDF file";
                resultText.setText(errorMsg);
                logError(fileName, errorMsg);
                return;
            }

            byte[] buffer = new byte[Math.min(is.available(), 1024 * 1024)]; // Max 1MB for analysis
            int bytesRead = is.read(buffer);
            String content = new String(buffer, 0, bytesRead > 0 ? bytesRead : 0);

            int riskScore = 0;
            List<String> risks = new ArrayList<>();
            StringBuilder details = new StringBuilder();

            // Check for JavaScript
            if (content.contains("/JavaScript") || content.contains("/JS")) {
                riskScore += 5;
                risks.add("JavaScript detected");
            }

            // Check for embedded files
            if (content.contains("/EmbeddedFile") || content.contains("/F")) {
                riskScore += 3;
                risks.add("Embedded files detected");
            }

            // Check for launch actions
            if (content.contains("/Launch") || content.contains("/Win")) {
                riskScore += 5;
                risks.add("Launch actions detected");
            }

            // Check for URLs
            Set<String> urls = extractUrls(content);
            if (!urls.isEmpty()) {
                details.append("  URLs Found:\n");
                for (String url : urls) {
                    details.append("    • ").append(url).append("\n");
                }
                riskScore += 2;
                risks.add("Contains external URLs");
            }

            if (!risks.isEmpty()) {
                details.append("\n  Detected Risks:\n");
                for (String risk : risks) {
                    details.append("    • ").append(risk).append("\n");
                }
            }

            String riskLevel = getRiskLevelText(riskScore);
            String finalVerdict;

            if (riskScore >= 8) {
                finalVerdict = "MALICIOUS PDF - Multiple security risks";
                resultText.setTextColor(Color.RED);
            } else if (riskScore >= 5) {
                finalVerdict = "SUSPICIOUS PDF - Proceed with caution";
                resultText.setTextColor(Color.rgb(255, 165, 0));
            } else if (riskScore > 0) {
                finalVerdict = "WARNING - Minor risks detected";
                resultText.setTextColor(Color.YELLOW);
            } else {
                finalVerdict = "SAFE PDF - No immediate risks";
                resultText.setTextColor(Color.GREEN);
            }

            StringBuilder result = new StringBuilder();
            result.append("📄 PDF ANALYSIS\n\n");
            result.append(details.toString());
            result.append("\nFINAL VERDICT: ").append(finalVerdict).append("\n");
            result.append("Risk Score: ").append(riskScore).append("/100");

            resultText.setText(result.toString());

            // Log the result
            logScanResult(fileName, finalVerdict, riskLevel, riskScore, details.toString());

        } catch (Exception e) {
            String errorMsg = "PDF analysis failed: " + e.getMessage();
            resultText.setText("❌ " + errorMsg);
            resultText.setTextColor(Color.RED);
            logError(fileName, errorMsg);
        }
    }

    // ================= ZIP ANALYSIS =================

    private void analyzeZip(File zipFile, String originalFileName) {
        String fileName = originalFileName != null ? originalFileName : zipFile.getName();

        try (ZipFile zf = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();

            int riskScore = 0;
            List<String> suspiciousFiles = new ArrayList<>();
            Set<String> fileExtensions = new HashSet<>();
            StringBuilder details = new StringBuilder();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().toLowerCase();
                String extension = name.contains(".") ? name.substring(name.lastIndexOf(".")) : "[no extension]";
                fileExtensions.add(extension);

                // Check for executable files
                if (name.endsWith(".exe") || name.endsWith(".bat") || name.endsWith(".sh") ||
                        name.endsWith(".vbs") || name.endsWith(".jar") || name.endsWith(".apk") ||
                        name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".bin")) {
                    riskScore += 5;
                    suspiciousFiles.add("Executable: " + name);
                }

                // Check for hidden files
                if (name.contains("/.") || name.startsWith(".")) {
                    riskScore += 2;
                    suspiciousFiles.add("Hidden file: " + name);
                }

                // Check file size (compressed vs uncompressed)
                if (entry.getCompressedSize() > 0 && entry.getSize() > 0) {
                    double ratio = (double) entry.getCompressedSize() / entry.getSize();
                    if (ratio < 0.1 && entry.getSize() > 1024 * 1024) { // Suspicious compression ratio
                        riskScore += 3;
                        suspiciousFiles.add("Suspicious compression: " + name);
                    }
                }
            }

            details.append("  Archive Statistics:\n");
            details.append("    Total entries: ").append(zf.size()).append("\n");
            details.append("    File types: ").append(fileExtensions).append("\n\n");

            if (!suspiciousFiles.isEmpty()) {
                details.append("  Suspicious Files Found:\n");
                for (String file : suspiciousFiles) {
                    details.append("    • ").append(file).append("\n");
                }
            }

            String riskLevel = getRiskLevelText(riskScore);
            String finalVerdict;

            if (riskScore >= 10) {
                finalVerdict = "MALICIOUS ARCHIVE - Contains executable files";
                resultText.setTextColor(Color.RED);
            } else if (riskScore >= 5) {
                finalVerdict = "SUSPICIOUS ARCHIVE - Review contents carefully";
                resultText.setTextColor(Color.rgb(255, 165, 0));
            } else {
                finalVerdict = "SAFE ARCHIVE - No immediate threats detected";
                resultText.setTextColor(Color.GREEN);
            }

            StringBuilder result = new StringBuilder();
            result.append("🗜 ZIP ARCHIVE ANALYSIS\n\n");
            result.append(details.toString());
            result.append("\nFINAL VERDICT: ").append(finalVerdict).append("\n");
            result.append("Risk Score: ").append(riskScore).append("/100");

            resultText.setText(result.toString());

            // Log the result
            logScanResult(fileName, finalVerdict, riskLevel, riskScore, details.toString());

        } catch (Exception e) {
            String errorMsg = "ZIP analysis failed: " + e.getMessage();
            resultText.setText("❌ " + errorMsg);
            resultText.setTextColor(Color.RED);
            logError(fileName, errorMsg);
        }
    }

    // ================= FILE MONITORING =================

    private void monitorAllFolders(File dir) {
        if (dir == null || !dir.isDirectory() || !dir.canRead()) return;

        // Don't monitor too many folders to avoid performance issues
        if (observers.size() > 100) return;

        try {
            FileObserver observer = new FileObserver(dir.getAbsolutePath(),
                    FileObserver.CREATE | FileObserver.MOVED_TO) {
                @Override
                public void onEvent(int event, String path) {
                    if (path == null) return;

                    File newFile = new File(dir, path);
                    if (!newFile.exists() || !newFile.canRead()) return;

                    runOnUiThread(() -> {
                        String name = newFile.getName().toLowerCase();
                        if (name.endsWith(".apk") || name.endsWith(".zip") ||
                                name.endsWith(".pdf") || name.endsWith(".jpg") ||
                                name.endsWith(".jpeg") || name.endsWith(".png")) {

                            // Check if file was recently scanned to avoid duplicates
                            String fileKey = name + "_" + newFile.length() + "_" + newFile.lastModified();
                            if (scannedFiles.contains(fileKey)) {
                                return;
                            }
                            scannedFiles.add(fileKey);

                            // Show notification of new file
                            String fileType = name.substring(name.lastIndexOf(".") + 1);
                            Toast.makeText(MainActivity.this,
                                    "New " + fileType.toUpperCase() + " file detected: " + name,
                                    Toast.LENGTH_SHORT).show();

                            // Log scan start
                            logScanStart(newFile.getName(), fileType);

                            // Analyze the file
                            if (name.endsWith(".apk")) {
                                analyzeApk(newFile, newFile.getName());
                            } else if (name.endsWith(".zip")) {
                                analyzeZip(newFile, newFile.getName());
                            } else if (name.endsWith(".pdf")) {
                                Uri uri = Uri.fromFile(newFile);
                                analyzePdf(newFile, uri, newFile.getName());
                            } else if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) {
                                Uri uri = Uri.fromFile(newFile);
                                analyzeImage(newFile, uri, newFile.getName());
                            }
                        }
                    });
                }
            };

            observer.startWatching();
            observers.put(dir.getAbsolutePath(), observer);

            // Recursively monitor subdirectories (limited depth)
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory() && file.canRead() && !file.getName().startsWith(".")) {
                        monitorAllFolders(file);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore permission issues with specific folders
        }
    }

    // ================= URL EXTRACTION =================

    private Set<String> extractUrls(String content) {
        Set<String> urls = new HashSet<>();
        Pattern pattern = Pattern.compile("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=]+");
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            String url = matcher.group();
            // Clean up the URL
            url = url.replaceAll("[\\])\"'>]", "");
            if (url.length() > 5) { // Minimum valid URL length
                urls.add(url);
            }
        }
        return urls;
    }
}