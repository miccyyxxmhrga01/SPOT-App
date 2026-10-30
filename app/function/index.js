"use strict";

const crypto = require("node:crypto");
const nodemailer = require("nodemailer");

const {initializeApp} = require("firebase-admin/app");
const {getAuth} = require("firebase-admin/auth");
const {
FieldValue,
Timestamp,
getFirestore,
} = require("firebase-admin/firestore");

const {defineSecret} = require("firebase-functions/params");
const {setGlobalOptions} = require("firebase-functions/v2");
const {HttpsError, onCall} = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");

initializeApp();

const REGION = "asia-southeast1";
const CODE_COLLECTION = "_emailVerificationCodes";
const CODE_LIFETIME_MS = 10 * 60 * 1000;
const RESEND_WAIT_MS = 60 * 1000;
const MAX_ATTEMPTS = 5;

setGlobalOptions({
region: REGION,
maxInstances: 10,
timeoutSeconds: 60,
memory: "256MiB",
});

const gmailUser = defineSecret("GMAIL_USER");
const gmailAppPassword = defineSecret("GMAIL_APP_PASSWORD");
const otpHashSecret = defineSecret("OTP_HASH_SECRET");

function verificationHash(uid, email, code) {
return crypto
.createHmac("sha256", otpHashSecret.value())
.update(`${uid}:${email}:${code}`)
.digest("hex");
}

function hashesMatch(storedHash, candidateHash) {
if (
typeof storedHash !== "string" ||
typeof candidateHash !== "string" ||
storedHash.length !== candidateHash.length
) {
return false;
}

return crypto.timingSafeEqual(
Buffer.from(storedHash, "hex"),
Buffer.from(candidateHash, "hex"),
);
}

async function authenticatedUser(request, allowVerified = false) {
if (!request.auth) {
throw new HttpsError(
"unauthenticated",
"You must be signed in before verifying an email.",
);
}

const user = await getAuth().getUser(request.auth.uid);
const email = (user.email || "").trim().toLowerCase();

if (!email) {
throw new HttpsError(
"failed-precondition",
"The signed-in account does not have an email address.",
);
}

const requestedEmail = String(request.data?.email || "")
.trim()
.toLowerCase();

if (requestedEmail && requestedEmail !== email) {
throw new HttpsError(
"permission-denied",
"The provided email does not match the signed-in account.",
);
}

if (!allowVerified && user.emailVerified) {
throw new HttpsError(
"failed-precondition",
"This email is already verified. Please log in.",
);
}

return {
uid: user.uid,
email,
alreadyVerified: user.emailVerified,
};
}

function callableHandler(handler) {
return async (request) => {
try {
return await handler(request);
} catch (error) {
if (error instanceof HttpsError) {
throw error;
}

logger.error("Email verification function failed", {
uid: request.auth?.uid || null,
error: error instanceof Error ? error.message : String(error),
});

throw new HttpsError(
"internal",
"The verification service is temporarily unavailable. Try again.",
);
}
};
}

exports.sendVerificationCode = onCall(
{
secrets: [gmailUser, gmailAppPassword, otpHashSecret],
},
callableHandler(async (request) => {
const {uid, email} = await authenticatedUser(request);
const db = getFirestore();
const codeReference = db.collection(CODE_COLLECTION).doc(uid);
const existingCode = await codeReference.get();
const nowMillis = Date.now();

if (existingCode.exists) {
const lastSentAt = existingCode.get("lastSentAt");

if (lastSentAt instanceof Timestamp) {
const elapsed = nowMillis - lastSentAt.toMillis();

if (elapsed < RESEND_WAIT_MS) {
const seconds = Math.ceil((RESEND_WAIT_MS - elapsed) / 1000);

throw new HttpsError(
"resource-exhausted",
`Please wait ${seconds} seconds before requesting another code.`,
);
}
}
}

const code = crypto.randomInt(100000, 1000000).toString();
const codeHash = verificationHash(uid, email, code);

await codeReference.set({
uid,
email,
codeHash,
attempts: 0,
verified: false,
createdAt: FieldValue.serverTimestamp(),
lastSentAt: Timestamp.fromMillis(nowMillis),
expiresAt: Timestamp.fromMillis(nowMillis + CODE_LIFETIME_MS),
});

const transporter = nodemailer.createTransport({
host: "smtp.gmail.com",
port: 465,
secure: true,
auth: {
user: gmailUser.value(),
pass: gmailAppPassword.value(),
},
});

try {
await transporter.sendMail({
from: `"SPOT" <${gmailUser.value()}>`,
to: email,
subject: "Your SPOT verification code",
text: [
"Your SPOT verification code is:",
"",
code,
"",
"This code expires in 10 minutes.",
"If you did not create this account, ignore this email.",
].join("\n"),
html: `
<div style="font-family:Arial,sans-serif;background:#f7f7f7;padding:32px">
<div style="max-width:480px;margin:auto;background:white;border-radius:18px;padding:28px;border:1px solid #ececec">
<div style="font-size:24px;font-weight:800;color:#171717">SPOT</div>
<p style="color:#555;line-height:1.6">Use this code to verify your email address:</p>
<div style="font-size:34px;font-weight:800;letter-spacing:10px;color:#b58e22;background:#fff7db;border-radius:14px;padding:18px;text-align:center">${code}</div>
<p style="color:#777;font-size:13px;line-height:1.6">This code expires in 10 minutes. If you did not create this account, you can ignore this email.</p>
</div>
</div>
`,
});
} catch (error) {

await codeReference.delete().catch(() => undefined);
throw error;
}

return {
success: true,
expiresInSeconds: CODE_LIFETIME_MS / 1000,
};
}),
);

exports.verifyVerificationCode = onCall(
{
secrets: [otpHashSecret],
},
callableHandler(async (request) => {
const code = String(request.data?.code || "").trim();

if (!/^\d{6}$/.test(code)) {
throw new HttpsError(
"invalid-argument",
"Enter the six-digit verification code.",
);
}

const {uid, email, alreadyVerified} =
await authenticatedUser(request, true);

if (alreadyVerified) {
return {verified: true};
}

const db = getFirestore();
const codeReference = db.collection(CODE_COLLECTION).doc(uid);

const result = await db.runTransaction(async (transaction) => {
const snapshot = await transaction.get(codeReference);

if (!snapshot.exists) {
return {state: "missing"};
}

const data = snapshot.data();
const expiresAt = data.expiresAt;
const attempts = Number(data.attempts || 0);

if (!(expiresAt instanceof Timestamp) || expiresAt.toMillis() < Date.now()) {
transaction.delete(codeReference);
return {state: "expired"};
}

if (attempts >= MAX_ATTEMPTS) {
transaction.delete(codeReference);
return {state: "locked"};
}

const candidateHash = verificationHash(uid, email, code);

if (!hashesMatch(data.codeHash, candidateHash)) {
transaction.update(codeReference, {
attempts: attempts + 1,
lastAttemptAt: FieldValue.serverTimestamp(),
});

return {
state: "invalid",
remainingAttempts: MAX_ATTEMPTS - attempts - 1,
};
}

transaction.update(codeReference, {
verified: true,
verifiedAt: FieldValue.serverTimestamp(),
});

return {state: "verified"};
});

if (result.state === "missing") {
throw new HttpsError(
"failed-precondition",
"No verification code was found. Request a new code.",
);
}

if (result.state === "expired") {
throw new HttpsError(
"deadline-exceeded",
"That code has expired. Request a new code.",
);
}

if (result.state === "locked") {
throw new HttpsError(
"resource-exhausted",
"Too many incorrect attempts. Request a new code.",
);
}

if (result.state === "invalid") {
throw new HttpsError(
"invalid-argument",
`Incorrect code. ${result.remainingAttempts} attempt(s) remaining.`,
);
}


await getAuth().updateUser(uid, {emailVerified: true});

await db.collection("users").doc(uid).set(
{
emailVerified: true,
verifiedAt: FieldValue.serverTimestamp(),
},
{merge: true},
);

await codeReference.delete();

return {verified: true};
}),
);