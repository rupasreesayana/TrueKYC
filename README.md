# TrueKYC

### Privacy-First On-Device KYC Verification

TrueKYC is an Android-based KYC verification prototype designed to help detect replay-based identity attacks during online verification.

## Problem

During online KYC, a fraudster may attempt to replay a recorded verification video using another phone or display instead of presenting the genuine customer.

## Solution

TrueKYC combines face and liveness verification with secondary-screen detection to identify suspicious replay attempts during the KYC process. When a possible replay attack is detected, verification is placed on hold and a security notification can be sent to the registered user, adding an additional layer of protection.

## Key Features

- Face and liveness verification
- Replay attack detection
- Secondary-screen / phone detection
- On-device AI processing
- Local LLM-based security analysis
- Original user security notification on suspicious verification activity
- Verification hold after detecting a possible replay attack
- Privacy-first approach
- Offline-capable security analysis
- No biometric video upload to the cloud

## Technology Stack

- Android
- Kotlin
- CameraX
- Google ML Kit
- MediaPipe
- On-device AI models
- Gemma 3 270M local LLM

## Privacy

TrueKYC is designed with a privacy-first approach. Security analysis is performed locally on the device, reducing the need to send sensitive biometric video to external cloud services.

## Security Workflow

1. Customer starts the KYC verification.
2. TrueKYC performs face and liveness verification.
3. The system checks for a possible secondary screen or replay attempt.
4. If suspicious activity is detected, the verification is placed on hold.
5. A security notification can be sent to the registered/original user.
6. The suspicious verification attempt can be recorded as a security event.

## Demo

Demo video:  
[YouTube Demo](https://youtube.com/shorts/Mnha0pW9xVA?si=zp8mz_PZQqroRKsr)

## Prototype

[TrueKYC v1.1.0 Release](https://github.com/rupasreesayana/TrueKYC/releases/tag/v1.1.0)

## Repository

[GitHub Repository](https://github.com/rupasreesayana/TrueKYC)

## Project Status

Final hackathon prototype.
