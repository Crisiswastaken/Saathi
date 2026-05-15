# Saathi — AI-Powered Healthcare Platform for Underserved Communities

## Project Report

---

## 1. Executive Summary

**Saathi** is an AI-driven healthcare platform built to address the critical gap in accessible, affordable, and early-stage medical care for rural and semi-urban populations across India. It combines four converging healthcare problem domains into a single, unified mobile experience:

1. **AI-based Medical Imaging Analysis** — Leveraging multimodal vision models to interpret X-ray, CT, and MRI imagery shared by users or healthcare workers, generating preliminary diagnostic observations for radiologist review.
2. **Predictive Analytics for Disease Outbreaks** — A real-time community disease mapping system that aggregates anonymized, geolocated health reports from users to forecast emerging disease clusters and enable proactive public health response.
3. **Wearable-Grade Health Monitoring** — Camera-based Photoplethysmography (PPG) that transforms any smartphone into a vital-sign monitor, continuously tracking Heart Rate, SpO₂ (blood oxygen saturation), HRV (Heart Rate Variability), and physiological stress — functionality traditionally limited to dedicated wearable devices.
4. **AI Chatbot for Primary Healthcare Support** — A multilingual, voice-first conversational AI assistant that performs symptom intake, provides preliminary health guidance, generates structured medical reports, and directs patients to appropriate care — all while preserving user privacy through on-device memory.

Saathi is designed with extreme accessibility in mind. Beyond the full-featured Android application, a **KaiOS-compatible variant** ensures that users on basic button phones (feature phones running KaiOS) can access core healthcare features — including the AI chatbot and camera-based heart rate and SpO₂ monitoring using the phone's rear flash — bringing essential health tools to the most resource-constrained populations.

---

## 2. Problem Landscape & Healthcare Context

### 2.1. The Rural Healthcare Crisis

India has approximately 1 doctor per 1,511 people in rural areas, far below the WHO-recommended ratio of 1:1,000. Diagnostic delays, misdiagnosis, and lack of access to specialist radiologists compound the burden. By the time patients in remote regions reach tertiary care, diseases have often progressed beyond early intervention windows.

### 2.2. Why a Unified Platform Matters

These four problem statements are not independent — they form a continuous healthcare pipeline:

- A user experiencing symptoms first interacts with the **AI chatbot** for preliminary guidance.
- The chatbot can trigger **vital sign monitoring** (PPG) to collect objective physiological data.
- If the user reports or is predicted to have a communicable disease, their anonymized data feeds the **disease outbreak map**, alerting the surrounding community.
- For cases involving imaging (e.g., a chest X-ray photo taken at a rural clinic), the **medical imaging analysis** module provides preliminary observations that can be relayed to a remote radiologist.

Saathi ties these four pillars together into a single, voice-driven experience accessible to users with low literacy, limited connectivity, and basic mobile devices.

---

## 3. System Architecture Overview

Saathi is built using modern Android development paradigms: **Kotlin, Jetpack Compose, MVVM Architecture, and Coroutines/Flows**. The platform targets **Android 8.0+ (API 26)** with a target SDK of **Android 15 (API 36)**, and is designed for a lightweight KaiOS port covering core healthcare features.

### High-Level Module Map

| Module | Healthcare Function | Key Technologies |
|--------|-------------------|-----------------|
| AI Voice Assistant | Primary healthcare chatbot | Groq LLaMA-3.3-70B, Sarvam AI STT/TTS |
| Symptom Intelligence Engine | Structured symptom intake & disease prediction | Multi-turn LLM conversation, JSON state machine |
| Camera PPG Vital Monitor | Heart Rate, SpO₂, HRV, Stress Index | CameraX, Biquad Bandpass Filter, Beer-Lambert Law |
| Disease Outbreak Radar | Predictive community disease mapping | Firebase Firestore, Haversine clustering, Google Maps |
| Medical Imaging Analysis | X-ray / CT / MRI interpretation | Multimodal vision (LLaMA-4 Scout), Base64 frame pipeline |
| On-Device RAG Memory | Privacy-preserving patient conversation history | ONNX all-MiniLM-L6-v2, local vector store |
| Health Report System | Structured medical report generation | Local CRUD, template-driven field population |
| KaiOS Lightweight Client | Button-phone healthcare access | Minimal AI chat, flash-based PPG |

---

## 4. Module Deep Dives

### 4.1. AI Chatbot for Primary Healthcare Support

This is the central interaction surface of Saathi and the module most critical to the "AI chatbot for primary healthcare" problem statement.

#### 4.1.1. Voice-First, Multilingual Design

The chatbot is designed for users who may be illiterate or have limited smartphone experience. The entire interaction is voice-driven:

- **Speech-to-Text**: Sarvam AI's `saaras:v3` model processes recorded audio. On first use, the language is set to `"unknown"`, enabling automatic detection of the user's spoken language from 8 supported Indian languages: Hindi, Kannada, Tamil, Telugu, English, Marathi, Bengali, and Gujarati.
- **Language Persistence**: Once detected, the language is saved to `SharedPreferences` and used for all subsequent interactions. Users can manually override via an in-app language selector.
- **Text-to-Speech**: Sarvam AI's `bulbul:v3` model generates natural-sounding audio responses in the detected language. A fallback to Android's system TTS engine (with correct locale mapping) ensures responses even when connectivity is poor.
- **Continuous Conversation Mode (Live Mode)**: Inspired by Google Gemini Live, Saathi supports hands-free continuous conversation. After the first mic tap, adaptive Voice Activity Detection (VAD) with a calibrated noise floor automatically detects speech onset and 1.4-second silence gaps to segment turns — no repeated tapping required. This is essential for users with mobility constraints or those performing other tasks.

#### 4.1.2. Healthcare-Specific LLM Behavior

The AI assistant is powered by **Groq's ultra-low-latency inference** of the **LLaMA-3.3-70B** model. The system prompt is carefully engineered for primary healthcare:

- The AI identifies itself as a "caring health assistant for rural Indian users."
- It uses "extremely simple words" and speaks "like a helpful neighbor, not a doctor."
- Responses are capped at 3 sentences for clarity over low-bandwidth voice.
- It never claims to "diagnose" — always using hedged language like "this could be" or "this might be."
- It advises visiting a doctor for every health concern after providing preliminary guidance.
- Non-health queries (government schemes, general knowledge) are handled briefly but gently redirected to health topics.

#### 4.1.3. Visual Aid Generation

For cases where voice-only guidance is insufficient (e.g., identifying a skin rash, a pest on crops, or a medicinal plant), the chatbot can generate photorealistic images on-demand using **Hugging Face's FLUX.1-schnell** model. This is strictly gated — the LLM only triggers image generation when a user is asking what a physical object looks like and a voice description would genuinely fail.

#### 4.1.4. Privacy & On-Device Memory (RAG Pipeline)

User conversations contain sensitive health information. Saathi implements a fully on-device Retrieval-Augmented Generation (RAG) system to provide personalized, historically aware responses without sending personal data to external servers:

- **Local Embedding Generation**: The `all-MiniLM-L6-v2` ONNX model runs on-device, generating 384-dimensional sentence embeddings. The model is loaded from the APK's assets directory into an ONNX Runtime session at app startup.
- **WordPiece Tokenization**: A complete BERT-compatible tokenizer is implemented in Kotlin, including normalization, pre-tokenization, and subword splitting with `##` continuation prefixes — no external tokenizer dependency.
- **Conversation Chunking**: Both user queries and AI responses are split into semantic chunks by `ChunkingManager` and embedded independently.
- **Vector Store**: Chunks with their embeddings are persisted to a local binary file (`conversation_chunks.bin`) using Java serialization. Brute-force cosine similarity search (dot product of unit vectors) retrieves the top-50 candidates, which are then reranked to the top-10 most relevant chunks.
- **Context Injection**: The `ContextBuilder` formats retrieved chunks chronologically with role labels (`[User said: ...]`, `[Assistant said: ...]`) and injects them into the LLM's system prompt, capped at 2,000 characters to stay within token limits.

This means the AI remembers past health conversations — a user who mentioned diabetes last week will receive contextually appropriate guidance this week, all without cloud storage of personal health data.

---

### 4.2. Wearable-Grade Health Monitoring (Camera PPG)

This module addresses the "wearable health monitoring systems" problem statement by turning the smartphone camera into a medical-grade sensor, eliminating the need for dedicated wearable hardware.

#### 4.2.1. Photoplethysmography Principle

When a user places their fingertip over the rear camera lens and flash, the LED illuminates subcutaneous blood vessels. With each heartbeat, arterial blood volume changes, modulating the amount of light absorbed. The camera captures these micro-variations in reflected light intensity across the Red, Green, and Blue channels.

#### 4.2.2. Signal Processing Pipeline

The signal processing engine (`PpgSignalProcessor`) implements a sophisticated DSP pipeline:

1. **ROI Extraction**: A 100×100 pixel region of interest (ROI) is extracted from the center of each RGBA_8888 camera frame. Average R, G, B values are computed per frame.
2. **Finger Detection**: Finger-on-camera detection uses a dual threshold: Red channel must exceed 50 AND the red ratio (R/(R+G+B)) must exceed 0.35. Five consecutive qualifying frames are required before measurement begins, preventing false starts.
3. **Bandpass Filtering**: A 4th-order Biquad IIR bandpass filter (0.5–4.0 Hz, centered at ~1.5 Hz, Q≈0.8 at 30 Hz sample rate) isolates the cardiac pulse frequency range (30–240 BPM), rejecting both baseline drift (DC) and high-frequency noise.
4. **Detrending**: Rolling mean subtraction with a configurable window (default: 1 second at 30 Hz) removes residual baseline wander.
5. **Adaptive Peak Detection**: Systolic peaks are identified using a local maximum test (must exceed both neighbors on each side) combined with an adaptive threshold set at 30% of the local peak-to-trough range within a 2-second window. A minimum inter-peak distance of 350ms (~170 BPM) prevents double-counting.

#### 4.2.3. Vital Parameters Extracted

| Parameter | Method | Clinical Relevance |
|-----------|--------|-------------------|
| **Heart Rate (BPM)** | Median of instantaneous HR from inter-beat intervals computed using actual timestamps. Range-validated to 40–200 BPM. | Resting HR, tachycardia/bradycardia detection |
| **SpO₂ (%)** | Windowed Ratio-of-Ratios method using Red and Blue channels. 5-second sliding windows with 50% overlap compute per-window RoR values; median is taken for robustness. Calibration: SpO₂ = 110 − 25 × R (Kanva et al., 2014). | Hypoxemia detection, respiratory distress screening |
| **HRV — RMSSD (ms)** | Root Mean Square of Successive Differences of inter-beat intervals (physiological range 300–2000ms). | Autonomic nervous system health, cardiac risk |
| **HRV — SDNN (ms)** | Standard Deviation of NN (normal-to-normal) intervals. | Long-term heart rate variability assessment |
| **Stress Index (0–100)** | Derived from RMSSD: lower RMSSD indicates higher sympathetic activation. Calibrated thresholds map RMSSD ranges to stress levels. | Mental health screening, chronic stress monitoring |
| **Signal Quality Index (0–100)** | Composite score from skewness (0–30), kurtosis (0–30), and peak regularity (0–40). Rejects readings from poor finger placement. | Measurement reliability gating |

#### 4.2.4. Measurement Protocol

- **Session Duration**: 30 seconds of continuous recording.
- **Live Feedback**: During measurement, live BPM and SpO₂ estimates are updated every 30 frames (approximately 1 second), along with a real-time waveform visualization.
- **Error Handling**: If the finger is lifted during measurement (10+ consecutive frames without detection), the session terminates with a user-friendly error message.

---

### 4.3. Predictive Analytics & Disease Outbreak Mapping

This module addresses the "predictive analytics for disease outbreaks" problem statement through a community-driven, AI-augmented disease surveillance system.

#### 4.3.1. AI-Powered Symptom-to-Disease Pipeline

The Symptom Intelligence Engine (`SymptomViewModel`) implements a multi-stage state machine:

1. **Health Mode Detection**: The LLM's system prompt includes a highest-priority rule that triggers health mode whenever a user describes any physical symptom, names a disease, or asks to report a condition on the health map. The LLM returns a structured JSON signal (`{"healthMode": true}`) instead of a conversational response.

2. **Direct Disease Report Detection**: A separate LLM call determines if the user is directly reporting a known disease ("I have dengue") versus describing ambiguous symptoms ("I have a headache and fever"). Direct reports bypass the intake process with a 75–90% confidence score.

3. **Conversational Symptom Intake**: For ambiguous symptoms, a follow-up questioning loop (3–5 turns) collects structured information:
   - Symptom onset and duration
   - Severity and body location
   - Accompanying symptoms and fever
   - Recent travel or contact with sick people
   
   The LLM asks one focused question per turn in the user's language, maintaining a compassionate tone. It signals readiness to predict by returning a `{"readyToPredict": true, "collectedSymptoms": [...]}` JSON.

4. **Disease Prediction**: A one-shot prediction prompt receives the full symptom list and returns structured JSON:
   - Disease name (e.g., "Viral Fever", "Dengue Fever")
   - Confidence percentage (40–95%)
   - Key driving symptoms
   - Reasoning (one sentence)
   - Category: FEVER, RESPIRATORY, GASTROINTESTINAL, SKIN, NEUROLOGICAL, or UNKNOWN

5. **Community Map Contribution**: After prediction, the user is asked (in their language, using warm and friendly phrasing) whether they want to share this anonymously on the community health map. Affirmative responses in multiple Indian languages are detected ("haan", "houdu", "avunu", "aamam", etc.).

#### 4.3.2. Geospatial Disease Intelligence

The Disease Outbreak Radar (`DiseaseMapViewModel`) provides real-time geospatial visualization:

- **Data Model**: Each `DiseaseReport` contains latitude, longitude, disease name, confidence score, symptoms, category, timestamp, language, and a 1km radius.
- **Cloud Sync**: Reports are stored in **Firebase Firestore** with real-time snapshot listeners that push updates to all connected clients instantly.
- **Zone Aggregation**: Reports are clustered into `DiseaseZone` objects using Haversine distance calculations. Reports of the same disease within 1km of each other are merged into a single zone with averaged coordinates and confidence scores.
- **Interactive Map**: Google Maps integration renders disease zones as color-coded overlays, with tap-to-inspect functionality showing zone details, report counts, and category breakdowns.

#### 4.3.3. Predictive Value

While individual reports have limited diagnostic accuracy, the aggregation of many user reports creates a crowd-sourced epidemiological dataset. Geographic clustering of similar disease predictions (e.g., multiple dengue reports within a 1km radius over a short time window) serves as an early warning signal for health authorities. The system enables:

- **Temporal trend detection**: Timestamp-ordered data reveals acceleration of disease spread.
- **Spatial hotspot identification**: Haversine-based clustering highlights geographic concentrations.
- **Category-level surveillance**: Filtering by disease category (FEVER, RESPIRATORY, etc.) enables targeted public health responses.

---

### 4.4. AI-Based Medical Imaging Analysis

Saathi extends its diagnostic capabilities to medical imaging through its **multimodal vision pipeline**. When a healthcare worker or user shares a medical image (X-ray, CT scan, MRI) during a conversation — either by taking a photo of a physical film or uploading a digital image — the system can provide preliminary observations.

#### 4.4.1. Live Screen Context & Vision Pipeline

The **Screen Share Service** (`ScreenShareService`) enables the AI to "see" medical images displayed on the user's screen:

- **MediaProjection API**: A foreground service captures the device screen using Android's `MediaProjection`, creating a `VirtualDisplay` that mirrors the display to an `ImageReader`.
- **Frame Processing**: Captured frames are resized to a maximum width of 720px, compressed to JPEG (quality 68), and Base64-encoded. Frame capture is throttled to one frame per 650ms to prevent memory bloat.
- **Multimodal LLM**: The Base64-encoded screenshot is sent to **LLaMA-4 Scout 17B** (a vision-capable model) via Groq's API, packaged as an `image_url` content part alongside the user's spoken query. This allows the AI to interpret radiological images, photos of skin conditions, or any visual health content displayed on-screen.

#### 4.4.2. Voice Bubble Overlay

During screen-share sessions, a floating, draggable voice bubble (`VoiceBubbleOverlayView`) is drawn over other apps using `TYPE_APPLICATION_OVERLAY`. The bubble:

- Animates based on audio amplitude to indicate listening/speaking states.
- Is fully draggable with a tap-to-toggle close button.
- Enables hands-free interaction while viewing medical images in a gallery or DICOM viewer.

#### 4.4.3. Clinical Guardrails

The system prompt explicitly instructs the AI to:

- Never claim definitive diagnosis from imaging.
- Use language like "this could indicate" or "this area may warrant further review."
- Always recommend professional radiologist confirmation.
- Provide step-by-step guidance on what to look for, rather than standalone verdicts.

---

### 4.5. Health Report Generation & Document Management

Saathi includes a structured health reporting system that bridges the gap between AI-assisted conversations and formal medical documentation.

#### 4.5.1. Report Template

The General Health Summary template captures:

| Field | Purpose |
|-------|---------|
| Patient Summary | Overview of the patient's reported condition |
| Symptoms | Catalogued symptoms from conversation intake |
| Medications | Current medications mentioned by the user |
| Allergies | Known allergies reported during interaction |
| Vitals | Heart rate, SpO₂, and other measurements from the PPG module |
| Medical History | Chronic conditions and past diagnoses |
| Lifestyle Notes | Sleep, hydration, activity levels, dietary habits |
| Doctor Notes | AI-generated clinical notes for healthcare provider review |
| Recommendations | Actionable health advice and next steps |
| Follow-Up Plan | Scheduled review timeline |

#### 4.5.2. Local-First Storage

All reports and uploaded documents are stored entirely on-device using `SharedPreferences`-backed JSON serialization (`LocalWorkspaceRepository`). This ensures:

- **Privacy**: No health documents leave the device without explicit user action.
- **Offline Access**: Reports are available even without internet connectivity.
- **Full CRUD**: Users can create, read, update, and delete reports and uploaded documents.

#### 4.5.3. Document Upload

Users can upload existing medical documents (prescriptions, lab reports, previous diagnoses) through the document management system. Uploaded files are copied to the app's internal storage with metadata tracking (name, MIME type, size, creation date).

---

## 5. KaiOS Compatibility — Healthcare for Button Phones

### 5.1. Why KaiOS Matters

Over 150 million KaiOS devices are in active use globally, predominantly in the hands of users in the lowest income segments — exactly the population most underserved by healthcare infrastructure. These are button-operated feature phones (e.g., JioPhone, Nokia 8110) with limited processing power, small screens, and no touchscreen.

Saathi is designed to work on KaiOS with a minimal feature set that still delivers meaningful healthcare value through the phone's hardware capabilities.

### 5.2. KaiOS-Compatible Features

| Feature | KaiOS Implementation | Hardware Used |
|---------|---------------------|---------------|
| **AI Health Chatbot** | Text-based chat interface navigable via D-pad. User types symptoms using T9/predictive keyboard; AI responds with simple health guidance in the user's language. | Keypad input, basic display |
| **Heart Rate Monitoring** | Camera-based PPG using the rear camera and LED flash. User places finger over camera, presses a button to start 30-second measurement. BPM result displayed on screen. | Rear camera + LED flash |
| **SpO₂ Estimation** | Same PPG pipeline as Android, using Red and Blue channel Ratio-of-Ratios from the rear camera with flash illumination. | Rear camera + LED flash |
| **Emergency Health Alerts** | Simple SMS-based alerts that can notify a pre-configured emergency contact with the user's health status and location. | SMS capability, basic GPS |
| **Disease Reporting** | Minimal form to report a disease name and location, contributing to the community outbreak map via lightweight API calls. | Keypad input, mobile data |

### 5.3. Design Principles for KaiOS

- **Minimal UI**: All screens are navigable using only the D-pad (up, down, left, right, select) and soft keys. No touch or swipe gestures.
- **Low Memory Footprint**: The KaiOS app excludes heavy modules (ONNX RAG, screen share, Google Maps rendering) and relies on server-side inference for the chatbot.
- **Offline-First PPG**: The heart rate and SpO₂ measurement algorithms run entirely on-device with no network dependency — the same `PpgSignalProcessor` DSP pipeline is portable to KaiOS's limited runtime.
- **SMS Fallback**: In areas with no mobile data, critical health alerts can be sent via SMS to a healthcare helpline or emergency contact.
- **Battery Efficiency**: KaiOS devices have limited batteries. The PPG session is capped at 30 seconds, and the chatbot only connects to the network during active queries.

### 5.4. Bridging the Digital Health Divide

By supporting KaiOS, Saathi ensures that the most vulnerable populations — those who cannot afford smartphones — still have access to:

- **A health advisor** that speaks their language and understands their symptoms.
- **Basic vital sign monitoring** using hardware every KaiOS phone already has (camera + flash).
- **Community disease awareness** through simple reporting mechanisms.

This aligns directly with the problem statements' emphasis on accessibility, early detection, and preventive healthcare.

---

## 6. Technical Stack & Infrastructure

### 6.1. Core Technologies

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Language | Kotlin (JVM 11) | Primary development language |
| UI Framework | Jetpack Compose + Material 3 | Declarative, reactive UI |
| Architecture | MVVM + StateFlow/SharedFlow | Unidirectional data flow |
| LLM Inference | Groq API (LLaMA-3.3-70B, LLaMA-4 Scout 17B) | Ultra-low-latency cloud AI |
| Voice Services | Sarvam AI (saaras:v3 STT, bulbul:v3 TTS) | Multilingual Indian language support |
| On-Device ML | ONNX Runtime (all-MiniLM-L6-v2) | Local embedding generation for RAG |
| Image Generation | Hugging Face FLUX.1-schnell | On-demand visual aid creation |
| Camera | CameraX 1.4.1 (RGBA_8888 analysis) | PPG vital sign acquisition |
| Database | Firebase Firestore (real-time sync) | Disease report cloud storage |
| Authentication | Firebase Phone Auth + AppCheck | SMS-based login with bot prevention |
| Maps | Google Maps Compose 4.3.3 | Disease outbreak visualization |
| Networking | Retrofit 2 + OkHttp | API communication layer |
| Location | Google Play Services Location | GPS for disease geo-tagging |

### 6.2. Security & Privacy

- **API Key Isolation**: All API keys (Groq, Sarvam, Hugging Face, Google Maps) are resolved from `local.properties` or `.env` files at build time and injected into `BuildConfig` — never hardcoded in source or committed to version control.
- **On-Device Health Data**: Conversation history, health reports, and uploaded documents are stored entirely on-device. The RAG vector store persists to internal app storage, inaccessible to other applications.
- **Anonymous Disease Reporting**: Disease reports submitted to Firestore contain only coordinates, disease metadata, and an anonymized user ID — no personally identifiable health information.
- **Firebase AppCheck**: Play Integrity verification prevents unauthorized API access and bot-generated disease reports.

### 6.3. Build Configuration

- **Min SDK**: Android 8.0 (API 26) — covers 95%+ of active Android devices in India.
- **Target SDK**: Android 15 (API 36) — latest platform features and security patches.
- **ONNX Model Packaging**: The `model.onnx` file is bundled uncompressed in the APK (`noCompress += "onnx"`) for direct memory-mapped loading.

---

## 7. Supported Languages

Saathi supports 8 Indian languages with full voice interaction (STT + TTS), localized UI status text, and language-specific LLM prompting:

| Language | Script | Code | Sarvam Code |
|----------|--------|------|-------------|
| Hindi | हिंदी | hi | hi-IN |
| Kannada | ಕನ್ನಡ | kn | kn-IN |
| Tamil | தமிழ் | ta | ta-IN |
| Telugu | తెలుగు | te | te-IN |
| English | English | en | en-IN |
| Marathi | मराठी | mr | mr-IN |
| Bengali | বাংলা | bn | bn-IN |
| Gujarati | ગુજરાતી | gu | gu-IN |

The yes/no detection for disease map confirmation works across all supported languages, recognizing affirmative tokens in English, Hindi, Kannada, Telugu, Tamil, Marathi, Bengali, and Gujarati.

---

## 8. User Journey — End-to-End Healthcare Flow

```
1. ONBOARDING
   User opens Saathi → Phone number entry (+91) → OTP verification → Main dashboard

2. SYMPTOM CONSULTATION (AI Chatbot)
   User taps Saathi voice → "Mujhe sir mein dard hai aur bukhar hai"
   → AI auto-detects Hindi → Asks: "Kab se hai?" → Collects 3-5 symptoms
   → Predicts: "Viral Fever (78% confidence)"
   → Advises doctor visit → Asks to share on community map

3. VITAL SIGN CHECK (Camera PPG)
   User navigates to Pulse → Selects Heart Rate or SpO₂ mode
   → Places finger on camera + flash → 30-second measurement
   → Results: HR 72 BPM, SpO₂ 97%, Stress Index 35/100

4. COMMUNITY DISEASE ALERT (Outbreak Radar)
   User agrees to share → Anonymous report added to Firestore
   → Map shows: "3 Viral Fever reports within 1km of your area"
   → Nearby users see the cluster on their Disease Radar

5. MEDICAL IMAGE REVIEW (Screen Share Mode)
   Healthcare worker opens chest X-ray photo → Activates Saathi Live
   → AI analyzes the visible image → "This area of opacity could indicate
      consolidation. A radiologist should review for possible pneumonia."

6. HEALTH REPORT
   AI generates structured report from conversation history
   → Patient Summary, Symptoms, Recommendations, Follow-Up Plan
   → Stored locally, available offline for doctor visits
```

---

## 9. Impact & Alignment with Problem Statements

| Problem Statement | Saathi's Solution | Key Differentiator |
|------------------|------------------|-------------------|
| AI-based medical imaging analysis | Multimodal vision LLM (LLaMA-4 Scout) interprets medical images shared via screen capture, providing preliminary observations | Voice-driven, works without specialized DICOM software |
| Predictive analytics for disease outbreaks | Community disease map aggregates anonymized AI predictions with geospatial clustering to detect emerging hotspots | Crowd-sourced from primary care conversations, not institutional data |
| Wearable health monitoring | Camera PPG extracts HR, SpO₂, HRV, and Stress Index using only the phone's camera and flash — no wearable hardware required | Works on KaiOS button phones, zero additional cost |
| AI chatbot for primary healthcare | Multilingual voice chatbot with symptom intake, disease prediction, health reports, and on-device privacy-preserving memory | 8 Indian languages, continuous conversation, works on feature phones |

---

## 10. Conclusion

Saathi is not merely a health app — it is a healthcare delivery platform designed for the 800+ million Indians who lack reliable access to primary medical care. By unifying AI-powered diagnostics, vital sign monitoring, disease surveillance, and multilingual conversational health guidance into a single application that runs on both smartphones and KaiOS button phones, Saathi demonstrates that advanced healthcare technology can be made accessible to the populations that need it most.

The platform's architecture — modular, privacy-first, and designed for low-resource environments — ensures that each of the four problem domains (medical imaging, disease outbreak prediction, wearable monitoring, and AI chatbot support) is addressed not in isolation but as part of an integrated healthcare continuum, where each module's output enriches the others.

---

*Built with Kotlin, Jetpack Compose, Groq AI, Sarvam AI, ONNX Runtime, CameraX, Firebase, and Google Maps.*
*Designed for Android 8.0+ and KaiOS feature phones.*
