🔐 SAFELAB: A Context-Aware Rule-Based Android APK Security Analysis System
📌 Overview

SAFELAB is an Android-based file security analysis system designed to detect potential threats in multiple file formats such as APK, PDF, images, and ZIP archives.

The system uses a context-aware rule-based approach to identify suspicious behavior like risky permissions, hidden metadata, embedded scripts, and malicious content.

It provides real-time file monitoring, risk classification, and log-based reporting, making it suitable for both technical and non-technical users.

🚀 Features
📱 APK Security Analysis
Detects dangerous permissions (SMS, Camera, Location)
Extracts app details (name, package, version)
🖼 Image Security Analysis
Detects GPS location metadata
Identifies hidden EXIF information
📄 PDF Security Analysis
Detects JavaScript and embedded scripts
Extracts suspicious URLs
🗜 ZIP Malware Analysis
Detects executable files inside ZIP
Identifies hidden and compressed threats
⚡ Real-Time File Monitoring
Automatically scans newly added files
Uses Android FileObserver
📊 Risk Classification System
🟢 Safe
🟡 Warning
🔴 Dangerous
📝 Logging System
Stores scan results in text files
Maintains scan history for future reference
📂 Manual File Scanning
Users can select and scan files manually
🛠 Technologies Used
Component	Technology
Programming Language	Java
Platform	Android
IDE	Android Studio
Libraries	APK Parser, ExifInterface
File Monitoring	FileObserver
Storage	Text-based log files
📱 Working of the System
User grants storage permissions
Application starts monitoring device storage
When a new file is detected:
File type is identified
Corresponding analysis module is triggered
Risk score is calculated based on predefined rules
File is classified as:
Safe / Warning / Dangerous
Results are displayed and saved as logs
🧩 Project Structure
SafeLab/
│── app/
│   ├── java/com/example/safelab/
│   │   └── MainActivity.java
│   ├── res/layout/
│   │   └── activity_main.xml
│── AndroidManifest.xml
│── README.md
🔑 Permissions Required
READ_EXTERNAL_STORAGE
MANAGE_EXTERNAL_STORAGE (Android 11+)
📦 Installation
Clone the repository:
git clone https://github.com/your-username/safelab.git
Open in Android Studio
Build and run on an Android device
Grant storage permissions
📊 Sample Output
📱 APK ANALYSIS RESULTS

App Name: Example App  
Permissions:
 - CAMERA
 - READ_SMS

FINAL VERDICT: HIGH RISK  
Risk Score: 10/100
⚠️ Limitations
Rule-based detection (no AI integration yet)
Requires full storage access permission
Limited depth monitoring to avoid performance issues
