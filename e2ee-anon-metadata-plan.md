# Integrating Metadata‑Private Transport and Anonymous Groups into a libsignal‑style System

> Assumption: your current system is **API‑compatible with libsignal** – same files, algorithms, and session logic (X3DH/PQXDH handshake, Double Ratchet, Sesame‑style session management; MLS for groups).

This doc is a **design playbook for smaller agents / services** that will extend the system with:

1. A **metadata‑private transport layer** (mixnet / PingPong / XRD‑style) _under_ the existing protocol.
2. An **anonymous group mode** (AART‑style) _on top of_ your MLS group implementation.

The goal is to change **how ciphertext moves and how groups reveal sender identity**, without touching the core libsignal/MLS crypto logic.

---

## 0. Baseline: What we assume exists

The agents should assume the following components behave exactly like the public specs:

- **X3DH / PQXDH handshake** for 1:1 sessions.
- **Double Ratchet** (possibly extended with PQ ratchet / ML‑KEM Braid) for message keys.
- **Sesame‑style session manager** for multi‑device / async session mapping.
- **MLS implementation** for group messaging (RFC 9420 / 9750 semantics).

Reference specs:
- Signal documentation (X3DH, PQXDH, Double Ratchet, Sesame, ML‑KEM Braid).[web:4][web:8][web:45][web:50]
- MLS Protocol and Architecture (RFC 9420, RFC 9750).[web:44][web:49][web:51]
- ART / AART and anonymous group messaging papers.[web:19][web:25][web:34][web:38][web:47]

Agents **must not modify**:

- Handshake message formats.
- Double Ratchet state machine and KDF chains.
- MLS tree math or key schedule.

All changes are **new layers** around these.

---

## 1. New Transport Layer: Metadata‑Private Messaging

### 1.1 High‑level idea

Current libsignal‑style transport:

- Client → server: `EncryptedMessage` with sender ID, receiver ID, and ciphertext.
- Server queues per user/device.

New transport:

- Client → **mixnet / PingPong‑like layer** → mailbox.
- Transport sees only **opaque fixed‑size packets** with **mailbox IDs**, not user IDs.
- Your existing libsignal/MLS ciphertexts are the **payload** inside these packets.

We approximate designs like **XRD**, **Stadium**, and **PingPong**:
- XRD/Stadium: batched mixnets with aggregate hybrid shuffle.[web:18][web:32][web:36][web:39][web:42]
- PingPong: notify‑then‑fetch design using enclaves and oblivious data structures.[web:31][web:33][web:35][web:37][web:40]


### 1.2 Core objects

Agents should introduce the following abstractions (language‑agnostic):

```text
MailboxId = 128‑bit random identifier (not user id)
TransportPacket = {
    mailbox_id: MailboxId,
    epoch: uint64,
    payload: fixed_size_bytes,
}

AppCiphertext = existing libsignal/MLS ciphertext bytes
```

Rules:
- `payload` must be a fixed length (pad or fragment `AppCiphertext`).
- `MailboxId` MUST NOT encode the user ID or device ID.


### 1.3 Client‑side send pipeline

For every outgoing message, instead of calling `send_to(user_id, ciphertext)` directly over a plain queue:

1. **Select Mailbox**
   - Map logical destination (user or group) to one or more `MailboxId`s.
   - This mapping is stored only on the client and in a trusted component on the server (mixnet or enclave), never in clear on the main DB.

2. **Wrap ciphertext**

   ```text
   TransportPacket {
     mailbox_id = destination_mailbox_id,
     epoch      = current_epoch_or_round,
     payload    = pad(AppCiphertext, FIXED_PACKET_SIZE),
   }
   ```

3. **Submit to entry node**
   - Client sends `TransportPacket` to an **entry node** (first hop of the mixnet) at a **fixed rate** (include dummy packets when idle).

4. **Do not attach sender id**
   - The packet must not contain sender or receiver identifiers outside of the encrypted `AppCiphertext`.


### 1.4 Server‑side mixnet / PingPong layer

Agents implementing infrastructure should:

#### 1.4.1 Mixnet option (XRD‑style)

- Deploy N mix servers in sequence.
- For each time window (round):
  - Collect all `TransportPacket`s from clients.
  - Each server:
    - Decrypts its onion layer (if used).
    - Batches, **shuffles**, and forwards packets to the next hop.
  - Last hop writes packets into mailboxes keyed by `mailbox_id`.

Goal: external observer cannot correlate input packets with output mailboxes except via volume bounds. This follows the XRD/Stadium approach.[web:18][web:32][web:36][web:39][web:42]

#### 1.4.2 PingPong‑style option

- **PING**: notification bit‑vector indicating which mailboxes have new messages.
  - Stored and updated in a secure enclave, using oblivious operations so the host cannot see access patterns.[web:31][web:33][web:35][web:37][web:40]
- **PONG**: oblivious key–value store mapping `MailboxId → encrypted blobs`.
- Clients:
  - Periodically fetch PING and PONG data via a uniform interface.
  - Filter locally for their own mailboxes.

This hides which mailbox you query and when.


### 1.5 Client‑side receive pipeline

1. Client periodically retrieves **all candidate packets** for mailboxes it owns (via mixnet or PingPong).
2. For each packet:
   - Strip `TransportPacket` header.
   - Feed `payload` to existing libsignal/MLS receive path:
     - 1:1: `signal_session.decrypt(payload)`.
     - Group: `mls_group.handle_application_message(payload)`.

The upper layers do not know about mixnets; they just see delayed, out‑of‑order ciphertext.


### 1.6 Integration constraints for agents

- **Do not bypass transport** – no direct sender/receiver queues.
- Always send at roughly **constant cadence** (add dummy packets).
- Never log or expose `MailboxId` → user mappings outside trusted scope.
- Keep messages fixed‑size at the transport layer.


---

## 2. Anonymous Group Mode over MLS

We want a group type where:

- Membership is managed by MLS as usual.
- FS and PCS are preserved.
- **Sender identity is hidden**, at least to other members (IGA) and ideally to outsiders (EGA), as in AART.[web:34][web:38]

### 2.1 New group type

Agents should treat groups as:

```text
GroupType = {
  NORMAL_MLS,       // standard MLS semantics, authenticated senders
  ANONYMOUS_MLS,    // AART‑style semantics, anonymous senders
}

GroupState = {
  mls_state: MlsGroupState,
  type: GroupType,
  anon_key: optional<Bytes>, // derived group AEAD key
}
```

Creation rules:

- `NORMAL_MLS`: identical to your current MLS group creation.
- `ANONYMOUS_MLS`:
  - Create an MLS group as usual.
  - After first epoch is established, derive `anon_key` from MLS exporter:

    ```text
    anon_key = MLS_ExportSecret("anon-group-key", key_len)
    ```

  - Store `anon_key` inside `GroupState` (in secure memory / keystore).


### 2.2 Anonymous send path

For a group with `type = ANONYMOUS_MLS`:

1. **Message composition**

   ```text
   plaintext = serialize({
     group_id,
     epoch,
     app_payload,     // user message
   })
   ```

2. **Anonymous encryption**

   ```text
   ciphertext = AEAD_Encrypt(
       key = anon_key,
       nonce = random_or_derived,
       plaintext,
       associated_data = group_id || epoch
   )
   ```

3. **Wrap into MLS application message**

   - Use MLS to deliver `ciphertext` to all group members **without** tying it to your leaf credential as sender identity.
   - Concretely, use MLS for epoch and member management, but treat the application layer as:
     - `ApplicationData = ciphertext`.
     - Skip per‑sender app‑layer signatures.

4. **Transport**

   - Send this MLS application message through the metadata‑private transport described in section 1.

Members who receive the message:

1. Use MLS as usual to check the message belongs to the current epoch and is from some group member.
2. Extract `ciphertext` from the MLS application message.
3. Decrypt with `anon_key` using the same associated data.
4. Display as “Message from anonymous member of group X”.

**Note:** this provides internal group anonymity (IGA) at the crypto level; traffic analysis and timing can still leak info unless transport is hidden (hence the mixnet layer).


### 2.3 Deriving `anon_key` securely

Agents must:

- Use the standardized MLS exporter with a distinct label, e.g. `"anon-group-key"` to avoid key reuse.[web:44][web:48][web:51]
- Refresh `anon_key` when:
  - Membership changes (add/remove member).
  - MLS epoch advances.

Implementation sketch:

```text
on_epoch_change(group):
  if group.type == ANONYMOUS_MLS:
      group.anon_key = MLS_ExportSecret("anon-group-key", key_len)
```

This ties anonymity keys to the same FS/PCS guarantees as MLS.


### 2.4 Optional: stronger anonymity via group / ring signatures

If agents need a proof that "the sender is a valid group member" without revealing which one:

- Introduce a group or ring signature scheme over group members’ signing keys.
- For each anonymous message:
  - Sender produces `sigma = GroupSign(msg)`.
  - Receivers verify `GroupVerify(msg, sigma)`.
- This aligns closer to the AART formalization for IGA/EGA.[web:34][web:38][web:47]

This is **optional** and more complex; initial version can rely on MLS’s own membership auth.


---

## 3. Where to plug this into an existing libsignal‑style codebase

For agents that know the libsignal layout, the integration points are conceptually:

### 3.1 1:1 messaging

- Replace or wrap:
  - `sendMessage(to, plaintext)` →
    - Use existing libsignal session to encrypt to `AppCiphertext`.
    - Call `transport_send(mailbox, AppCiphertext)` (section 1 logic).

- Receive side:
  - `transport_receive()` → returns `TransportPacket`.
  - Strip header, feed `payload` to `decryptMessage()` as before.

No changes inside the ratchet code.


### 3.2 Group messaging (MLS)

- For **NORMAL_MLS** groups:
  - No change, except that finished MLS ciphertext goes through `transport_send`.

- For **ANONYMOUS_MLS** groups:
  - On send:
    - Intercept just before building application data:
      - Instead of `ApplicationData = app_plaintext`, set `ApplicationData = anon_ciphertext` as described in 2.2.
  - On receive:
    - After MLS verifies the message and extracts `ApplicationData`, attempt AEAD decrypt with `anon_key`.

All MLS tree operations remain untouched.


---

## 4. Agent responsibilities & invariants

To keep the system secure, your smaller agents must enforce these invariants:

### 4.1 Transport agents

- All messages pass through the metadata‑private layer.
- No logs or metrics expose direct user‑to‑user edges.
- All packets are fixed‑size; padding is mandatory.
- Clients send at roughly constant cadence, with dummy packets.


### 4.2 Group/session agents

- Do not alter core libsignal / MLS crypto.
- For anonymous groups:
  - Always derive `anon_key` from MLS exporter after each epoch change.
  - Never include sender identity in application‑layer plaintext.
  - Decryption errors are treated as generic failures, not as identity leaks.


### 4.3 Security review agents

- Periodically audit that:
  - No code path bypasses the transport layer.
  - No correlation IDs (like user id or device id) are added to `TransportPacket`s.
  - Logs/metrics are sanitized.
- Monitor research on:
  - Improvements to MLS security analysis and zero‑knowledge group updates.[web:48]
  - New AART / membership‑privacy for ART / MLS results.[web:34][web:38][web:47]
  - New results on metadata‑private messaging (XRD, PingPong, follow‑ups).[web:18][web:31][web:35][web:39][web:42]


---

## 5. Migration strategy

Agents should handle migration of existing users and groups as follows:

1. **Introduce metadata‑private transport first**
   - Keep old direct queues temporarily.
   - Gradually route more conversations via the mixnet / PingPong layer.
   - Once stable, disable direct transport.

2. **Add ANONYMOUS_MLS group type**
   - Existing MLS groups remain NORMAL_MLS.
   - Allow users to create new anonymous groups.
   - Optionally, provide a migration path: clone existing group into a new ANONYMOUS_MLS group and re‑invite members.

3. **Harden config**
   - Default new large groups to ANONYMOUS_MLS + metadata‑private transport if your threat model demands it.


---

## 6. What smaller agents should NEVER do

- Never change the Double Ratchet, X3DH/PQXDH, or MLS core logic without explicit instructions.
- Never attach real user IDs to `TransportPacket`.
- Never remove padding or dummy traffic mechanisms.
- Never log plaintext content or raw `AppCiphertext` outside secure storage.

The whole point of this design is that **we get strictly more privacy** (metadata hiding, anonymous groups) while keeping all of libsignal/MLS’s proven content security properties intact.
