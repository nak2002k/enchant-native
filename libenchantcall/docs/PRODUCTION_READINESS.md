# Production Readiness — libenchantcall

## Current Status: Phase 1 (Foundation) — Not Started

This document tracks all dev-only shortcuts, temporary workarounds, and items that must be addressed before production release.

---

## Phase 1: Foundation

### Build System
- [ ] **Use prebuilt WebRTC for initial development** — Building WebRTC from source takes hours and requires depot_tools. For Phase 1, use prebuilt WebRTC AAR for Android.
  - **Risk:** Prebuilt may not have latest security patches
  - **Priority:** Low for Phase 1, High before production
  - **Action:** Build WebRTC from source for production builds

### Testing
- [ ] **Mock WebRTC for unit tests** — Real WebRTC requires native libraries and platform setup. Unit tests should use mock PeerConnection.
  - **Risk:** Mocks may not catch real WebRTC bugs
  - **Priority:** Medium
  - **Action:** Add integration tests with real WebRTC before production

### Security
- [ ] **No certificate pinning in Phase 1** — Signaling uses WebSocket without certificate pinning initially.
  - **Risk:** MITM attack on signaling channel
  - **Priority:** High — must be done before production
  - **Action:** Implement certificate pinning for WebSocket connections

- [ ] **No key rotation in Phase 1** — Triple Ratchet media encryption is not implemented in Phase 1.
  - **Risk:** Media encrypted only with DTLS/SRTP (transport-level), no forward secrecy
  - **Priority:** Critical — this is the main differentiator
  - **Action:** Complete Phase 3 (Media Encryption) before production

- [ ] **No audio watermarking** — Call recording detection is planned but not implemented.
  - **Risk:** Participants can record calls without detection
  - **Priority:** Low — nice to have, not critical for launch

---

## Phase 2: Signaling & Quality

### Network
- [ ] **Fixed STUN/TURN servers** — Using hardcoded STUN/TURN servers instead of fetching from backend.
  - **Risk:** Servers may go down, no fallback
  - **Priority:** Medium
  - **Action:** Implement dynamic STUN/TURN server fetching from backend

- [ ] **No ICE candidate validation** — Not validating ICE candidates before adding to PeerConnection.
  - **Risk:** Malicious ICE candidates could cause crashes or connect to wrong endpoints
  - **Priority:** High
  - **Action:** Validate ICE candidate format and IP ranges before adding

### Quality
- [ ] **No adaptive bitrate** — Bitrate is fixed, not adapted to network conditions.
  - **Risk:** Poor call quality on bad networks
  - **Priority:** Medium
  - **Action:** Implement adaptive bitrate based on RTCP feedback

---

## Phase 3: Media Encryption

### Encryption
- [ ] **Triple Ratchet not yet implemented** — This is the core feature.
  - **Risk:** No E2EE for media, only transport-level encryption
  - **Priority:** Critical
  - **Action:** Implement CallRatchet class with symmetric + DH + PQ KEM ratchets

- [ ] **No PQ KEM support** — Post-quantum key encapsulation is planned but not implemented.
  - **Risk:** Future quantum computers could decrypt recorded calls
  - **Priority:** Medium for launch, High for long-term
  - **Action:** Integrate Kyber KEM from libenchantcrypto

### SRTP Transform
- [ ] **Custom SRTP transform not yet implemented** — Using WebRTC's default DTLS/SRTP.
  - **Risk:** Media keys derived from DTLS handshake, not from Triple Ratchet
  - **Priority:** Critical
  - **Action:** Implement FrameEncryptorInterface and FrameDecryptorInterface

---

## Phase 4: Group Calls

### SFU
- [ ] **No SFU implementation** — Group calls require SFU infrastructure.
  - **Risk:** No group call support
  - **Priority:** Medium for initial launch, High for feature parity
  - **Action:** Implement SFU client protocol

### Group Crypto
- [ ] **No epoch-based key derivation** — Group call encryption not implemented.
  - **Risk:** No post-join/post-leave secrecy for group calls
  - **Priority:** High for group calls
  - **Action:** Implement group crypto with epoch rotation

---

## Phase 5: Polish & Production

### Performance
- [ ] **No performance benchmarks** — Need to measure CPU, memory, latency.
  - **Risk:** Unknown performance characteristics
  - **Priority:** High before production
  - **Action:** Run benchmarks on all platforms

- [ ] **No stress tests** — Need to test with 100+ concurrent calls.
  - **Risk:** Unknown behavior under load
  - **Priority:** High before production
  - **Action:** Implement stress test suite

### Security Audit
- [ ] **No independent security audit** — Code has not been audited by third party.
  - **Risk:** Unknown security vulnerabilities
  - **Priority:** Critical before production
  - **Action:** Commission independent security audit

### Compliance
- [ ] **No accessibility testing** — Call UI needs accessibility testing.
  - **Risk:** Non-compliant with accessibility standards
  - **Priority:** Medium
  - **Action:** Test with TalkBack, VoiceOver

---

## Dev-Only Shortcuts Summary

| Shortcut | Risk | Priority | Phase |
|----------|------|----------|-------|
| Prebuilt WebRTC | Low | Low | 1 |
| Mock WebRTC in tests | Medium | Medium | 1 |
| No certificate pinning | High | High | 1 |
| No key rotation | Critical | Critical | 3 |
| Fixed STUN/TURN servers | Medium | Medium | 2 |
| No ICE validation | High | High | 2 |
| No adaptive bitrate | Medium | Medium | 2 |
| No Triple Ratchet | Critical | Critical | 3 |
| No PQ KEM | Medium | Medium | 3 |
| No custom SRTP | Critical | Critical | 3 |
| No SFU | Medium | Medium | 4 |
| No group crypto | High | High | 4 |
| No benchmarks | High | High | 5 |
| No stress tests | High | High | 5 |
| No security audit | Critical | Critical | 5 |
