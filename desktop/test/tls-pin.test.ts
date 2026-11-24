import assert from "node:assert/strict";
import { createHash, createSign, generateKeyPairSync } from "node:crypto";
import test from "node:test";
import { certificateSPKIPin, isValidPin, verifyPinnedCertificate } from "../src/tls-pin.js";

function derLength(len: number): Buffer {
  if (len < 0x80) return Buffer.from([len]);
  const bytes: number[] = [];
  let n = len;
  while (n > 0) {
    bytes.unshift(n & 0xff);
    n >>= 8;
  }
  return Buffer.from([0x80 | bytes.length, ...bytes]);
}

function derTag(tag: number, content: Buffer): Buffer {
  return Buffer.concat([Buffer.from([tag]), derLength(content.length), content]);
}

function derSequence(...items: Buffer[]): Buffer {
  return derTag(0x30, Buffer.concat(items));
}

function derBitString(content: Buffer): Buffer {
  return derTag(0x03, Buffer.concat([Buffer.from([0x00]), content]));
}

function derOID(oidBytes: number[]): Buffer {
  return derTag(0x06, Buffer.from(oidBytes));
}

function makeSelfSignedECert(): { pem: string; pin: string } {
  const { publicKey, privateKey } = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const spki = publicKey.export({ type: "spki", format: "der" });
  const pin = createHash("sha256").update(spki).digest("base64url");

  // ecdsa-with-SHA256 OID: 1.2.840.10045.4.3.2
  const sigAlg = derSequence(derOID([0x2a, 0x86, 0x48, 0xce, 0x3d, 0x04, 0x03, 0x02]));
  // CN=hub.persona.invalid
  const cn = derSequence(
    derTag(
      0x31,
      derSequence(
        derOID([0x55, 0x04, 0x03]),
        derTag(0x0c, Buffer.from("hub.persona.invalid", "utf8")),
      ),
    ),
  );
  const validity = derSequence(
    derTag(0x17, Buffer.from("250101000000Z", "ascii")),
    derTag(0x17, Buffer.from("350101000000Z", "ascii")),
  );
  const tbs = derSequence(
    derTag(0xa0, derTag(0x02, Buffer.from([0x02]))), // v3
    derTag(0x02, Buffer.from([0x01])), // serial 1
    sigAlg,
    cn,
    validity,
    cn,
    spki,
  );
  const signature = createSign("SHA256").update(tbs).sign(privateKey);
  const certDer = derSequence(tbs, sigAlg, derBitString(signature));
  const b64 = certDer.toString("base64").match(/.{1,64}/g)!.join("\n");
  return {
    pem: `-----BEGIN CERTIFICATE-----\n${b64}\n-----END CERTIFICATE-----\n`,
    pin,
  };
}

test("SPKI fingerprint validation accepts 43-char base64url hashes and rejects malformed values", () => {
  const { pem, pin } = makeSelfSignedECert();
  assert.equal(isValidPin(pin), true);
  assert.equal(isValidPin("short"), false);
  assert.equal(isValidPin(`${pin}=`), false);
  assert.equal(certificateSPKIPin(pem), pin);
  assert.equal(certificateSPKIPin("not a cert"), undefined);
});

test("pinned verification accepts matching SPKI, rejects mismatch, and falls back for unpinned or other hosts", () => {
  const { pem: certA, pin: pinA } = makeSelfSignedECert();
  const { pem: certB, pin: pinB } = makeSelfSignedECert();
  assert.notEqual(pinA, pinB);

  assert.equal(
    verifyPinnedCertificate(
      { hostname: "192.0.2.10", certificateData: certA, verificationResult: "CERT_AUTHORITY_INVALID" },
      { hostname: "192.0.2.10", fp: pinA },
    ),
    0,
  );
  assert.equal(
    verifyPinnedCertificate(
      { hostname: "2001:db8::1", certificateData: certA, verificationResult: "CERT_AUTHORITY_INVALID" },
      { hostname: "[2001:db8::1]", fp: pinA },
    ),
    0,
  );
  assert.equal(
    verifyPinnedCertificate(
      { hostname: "192.0.2.10", certificateData: certB, verificationResult: "CERT_AUTHORITY_INVALID" },
      { hostname: "192.0.2.10", fp: pinA },
    ),
    -2,
  );
  assert.equal(
    verifyPinnedCertificate(
      { hostname: "other.example.com", certificateData: certA, verificationResult: "OK" },
      { hostname: "192.0.2.10", fp: pinA },
    ),
    -3,
  );
  assert.equal(
    verifyPinnedCertificate(
      { hostname: "192.0.2.10", certificateData: certA, verificationResult: "CERT_AUTHORITY_INVALID" },
      undefined,
    ),
    -3,
  );
});
