# 🔐 SAFELAB: A Context-Aware Rule-Based Android APK Security Analysis System

---

## 📌 Overview
**SAFELAB** is an Android-based file security analysis system designed to detect potential threats in multiple file formats such as APK, PDF, images, and ZIP archives.

The system uses a **context-aware rule-based approach** to identify suspicious behavior like risky permissions, hidden metadata, embedded scripts, and malicious content.

It provides **real-time file monitoring**, **risk classification**, and **log-based reporting**, making it suitable for both technical and non-technical users.

---

## 🚀 Features

- 📱 APK Security Analysis  
  - Detects dangerous permissions (SMS, Camera, Location)  
  - Extracts app details (name, package, version)  

- 🖼 Image Security Analysis  
  - Detects GPS location metadata  
  - Identifies hidden EXIF information  

- 📄 PDF Security Analysis  
  - Detects JavaScript and embedded scripts  
  - Extracts suspicious URLs  

- 🗜 ZIP Malware Analysis  
  - Detects executable files inside ZIP  
  - Identifies hidden and compressed threats  

- ⚡ Real-Time File Monitoring  
  - Automatically scans newly added files  
  - Uses Android `FileObserver`  

- 📊 Risk Classification System  
  - 🟢 Safe  
  - 🟡 Warning  
  - 🔴 Dangerous  

- 📝 Logging System  
  - Stores scan results in text files  
  - Maintains scan history  

- 📂 Manual File Scanning  
  - User can select and scan files  

---

## 🛠 Technologies Used

| Component | Technology |
|----------|-----------|
| Programming Language | Java |
| Platform | Android |
| IDE | Android Studio |
| Libraries | APK Parser, ExifInterface |
| Monitoring | FileObserver |
| Storage | Text-based logs |

---

## 📱 System Workflow

1. User grants storage permissions  
2. App monitors device storage  
3. New file detected  
4. File type identified  
5. Analysis module executed  
6. Risk score calculated  
7. Result displayed + logged  

---

## 🧩 Project Structure
SafeLab/
│── app/
│ ├── java/com/example/safelab/
│ │ └── MainActivity.java
│ ├── res/layout/
│ │ └── activity_main.xml
│── AndroidManifest.xml
│── README.md



---

## 🔑 Permissions Required

- READ_EXTERNAL_STORAGE  
- MANAGE_EXTERNAL_STORAGE (Android 11+)  

---

## 📦 Installation

```bash
git clone https://github.com/your-username/safelab.git

APK ANALYSIS RESULTS

App Name: Example App  
Permissions:
 - CAMERA
 - READ_SMS

FINAL VERDICT: HIGH RISK  
Risk Score: 10/100
