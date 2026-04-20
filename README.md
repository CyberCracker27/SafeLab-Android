# 🔐 SAFELAB: A Context-Aware Rule-Based Android APK Security Analysis System

## 📌 Overview
SAFELAB is an Android-based file security analysis system designed to detect potential threats in multiple file formats such as APK, PDF, images, and ZIP files.  
It uses a rule-based approach to identify suspicious behavior like risky permissions, hidden metadata, embedded scripts, and malicious content.  
The system also supports real-time file monitoring and log-based reporting.

---

## 🚀 Features
- APK Security Analysis (permission checking)
- Image Analysis (GPS & metadata detection)
- PDF Analysis (scripts & URL extraction)
- ZIP Analysis (malicious file detection)
- Real-time file monitoring using FileObserver
- Risk classification (Safe / Warning / Dangerous)
- Text-based log storage
- Manual file scanning support

---

## 🛠 Technologies Used
- Language: Java
- Platform: Android
- IDE: Android Studio
- Libraries: APK Parser, ExifInterface
- Monitoring: FileObserver
- Storage: Text-based logs

---

## 📱 System Workflow
1. User grants storage permissions  
2. App monitors storage  
3. New file detected  
4. File type identified  
5. File analyzed  
6. Risk calculated  
7. Result displayed and logged  

---

## 🧩 Project Structure
SafeLab/
│── app/
│   ├── java/com/example/safelab/MainActivity.java
│   ├── res/layout/activity_main.xml
│── AndroidManifest.xml
│── README.md

---

## 🔑 Permissions Required
- READ_EXTERNAL_STORAGE  
- MANAGE_EXTERNAL_STORAGE (Android 11+)

---

## 📦 Installation
git clone https://github.com/your-username/safelab.git

1. Open in Android Studio  
2. Build & Run  
3. Grant permissions  

---

## 📊 Sample Output
APK ANALYSIS RESULTS

App Name: Example App  
Permissions:
- CAMERA
- READ_SMS  

FINAL VERDICT: HIGH RISK  
Risk Score: 10/100  

---

## ⚠️ Limitations
- Rule-based detection only  
- Requires storage permissions  
- Limited monitoring scope  

---
