// ============================================================================
// push-relay: يرسل إشعارات FCM بدل Cloud Functions (مجاني على Cloudflare Workers، بدون بطاقة)
//
// التطبيق، بعد ما ترسل رسالتك، يستدعي هذا الـ Worker ويرسل توكن تسجيل الدخول (Firebase ID token).
// الـ Worker: 1) يتأكد من التوكن  2) يتأكد إن الرسالة موجودة فعلاً بقاعدة البيانات ومرسلها هو صاحب التوكن
//             3) يجلب توكنات أجهزة المستلم  4) يرسل الإشعار عبر FCM.
//
// المتغيّرات (Settings > Variables):
//   SERVICE_ACCOUNT  (Secret)  محتوى ملف الـ service account JSON كامل
//   RTDB_URL         (اختياري) رابط Realtime Database، الافتراضي https://<project>-default-rtdb.firebaseio.com
// ============================================================================

const SCOPES = {
  rtdb: 'https://www.googleapis.com/auth/firebase.database https://www.googleapis.com/auth/userinfo.email',
  fcm: 'https://www.googleapis.com/auth/firebase.messaging',
  fs: 'https://www.googleapis.com/auth/datastore'
};

const ID_RE = /^[A-Za-z0-9_-]{1,128}$/;
const seen = new Map(); // منع تكرار الإشعار لنفس الرسالة (ضمن نفس النسخة من الـ Worker)

export default {
  async fetch(req, env) {
    if (req.method === 'OPTIONS') return new Response(null, { status: 204 });
    if (req.method === 'GET') {
      const m = new URL(req.url).pathname.match(/^\/u\/([A-Za-z0-9_-]{1,128})\/?$/);
      if (m) return profilePage(m[1]);
    }
    if (req.method !== 'POST') return json({ ok: true, service: 'push-relay' });
    try {
      return await handle(req, env);
    } catch (e) {
      return json({ ok: false, error: String(e && e.message || e) }, 500);
    }
  }
};

async function handle(req, env) {
  const sa = JSON.parse(env.SERVICE_ACCOUNT);
  const project = sa.project_id;
  const rtdbUrl = (env.RTDB_URL || `https://${project}-default-rtdb.firebaseio.com`).replace(/\/$/, '');

  // 1) هوية المرسل
  const idToken = (req.headers.get('Authorization') || '').replace(/^Bearer\s+/i, '');
  const sender = await verifyIdToken(idToken, project);
  if (!sender) return json({ ok: false, error: 'unauthorized' }, 401);

  const body = await req.json().catch(() => ({}));
  if (body.action === 'call' || body.action === 'call_cancel') {
    return handleCall(body, sender, sa, project, rtdbUrl);
  }
  const to = String(body.to || '');
  const msgId = String(body.msgId || '');
  if (!ID_RE.test(to) || !ID_RE.test(msgId) || to === sender) return json({ ok: false, error: 'bad request' }, 400);

  const dedupe = sender + '/' + msgId;
  if (seen.has(dedupe)) return json({ ok: true, skipped: 'duplicate' });
  seen.set(dedupe, Date.now());
  if (seen.size > 2000) for (const k of [...seen.keys()].slice(0, 1000)) seen.delete(k);

  // 2) الرسالة موجودة فعلاً ومرسلها هو صاحب التوكن
  const cid = [sender, to].sort().join('_');
  const rtTok = await accessToken(sa, SCOPES.rtdb);
  const msg = await rtdbGet(rtdbUrl, `chats/${cid}/messages/${msgId}`, rtTok);
  if (!msg || msg.from !== sender || msg.to !== to || typeof msg.text !== 'string') {
    return json({ ok: false, error: 'message not found' }, 403);
  }
  if (Math.abs(Date.now() - Number(msg.createdAt || 0)) > 3 * 60 * 1000) {
    return json({ ok: true, skipped: 'old' });
  }

  // 3) إذا المستلم حاظر المرسل: ما نرسل
  const blocked = await rtdbGet(rtdbUrl, `blocks/${to}/${sender}`, rtTok);
  if (blocked === true) return json({ ok: true, skipped: 'blocked' });

  // 4) توكنات أجهزة المستلم + اسم المرسل
  const fsTok = await accessToken(sa, SCOPES.fs);
  const fsBase = `https://firestore.googleapis.com/v1/projects/${project}/databases/(default)/documents`;
  const tokens = await listTokens(fsBase, to, fsTok);
  if (tokens.length === 0) return json({ ok: true, skipped: 'no tokens' });

  let name = '';
  try {
    const r = await fetch(`${fsBase}/profiles/${sender}`, { headers: { Authorization: 'Bearer ' + fsTok } });
    if (r.ok) name = ((await r.json()).fields?.name?.stringValue) || '';
  } catch (_) {}

  // 5) الإرسال (data فقط، التطبيق هو الي يبني الإشعار بصورة المرسل)
  const sent = await deliver(sa, project, fsBase, fsTok, to, tokens, {
    type: 'msg', from: sender, to, fromName: name,
    text: msg.text.slice(0, 200), chatId: cid
  }, '120s');
  return json({ ok: true, sent });
}

// ---------------------------------------------------------------- المكالمات الصوتية

async function handleCall(body, sender, sa, project, rtdbUrl) {
  const action = body.action;
  const to = String(body.to || '');
  const callId = String(body.callId || '');
  if (!ID_RE.test(to) || !ID_RE.test(callId) || to === sender) return json({ ok: false, error: 'bad request' }, 400);

  const dedupe = action + '/' + sender + '/' + callId;
  if (seen.has(dedupe)) return json({ ok: true, skipped: 'duplicate' });
  seen.set(dedupe, Date.now());

  const rtTok = await accessToken(sa, SCOPES.rtdb);
  const call = await rtdbGet(rtdbUrl, `calls/${callId}`, rtTok);
  // لازم المكالمة موجودة والطالب هو المتصل فعلاً وللشخص نفسه
  if (!call || call.from !== sender || call.to !== to) return json({ ok: false, error: 'call not found' }, 403);

  if (action === 'call') {
    if (call.state !== 'ringing') return json({ ok: true, skipped: 'not ringing' });
    if (Math.abs(Date.now() - Number(call.createdAt || 0)) > 60 * 1000) return json({ ok: true, skipped: 'old' });
    const blocked = await rtdbGet(rtdbUrl, `blocks/${to}/${sender}`, rtTok);
    if (blocked === true) return json({ ok: true, skipped: 'blocked' });
  } else if (call.state === 'ringing' || call.state === 'active') {
    // الإلغاء فقط إذا المكالمة سكّرت فعلاً
    return json({ ok: true, skipped: 'still open' });
  }

  const fsTok = await accessToken(sa, SCOPES.fs);
  const fsBase = `https://firestore.googleapis.com/v1/projects/${project}/databases/(default)/documents`;
  const tokens = await listTokens(fsBase, to, fsTok);
  if (tokens.length === 0) return json({ ok: true, skipped: 'no tokens' });

  let data;
  if (action === 'call') {
    let name = '';
    try {
      const r = await fetch(`${fsBase}/profiles/${sender}`, { headers: { Authorization: 'Bearer ' + fsTok } });
      if (r.ok) name = ((await r.json()).fields?.name?.stringValue) || '';
    } catch (_) {}
    data = { type: 'call', callId, from: sender, to, fromName: name };
  } else {
    data = { type: 'call_cancel', callId, from: sender, to };
  }
  // ttl قصير: المكالمة القديمة ما لها فايدة
  const sent = await deliver(sa, project, fsBase, fsTok, to, tokens, data, '30s');
  return json({ ok: true, sent });
}

// ---------------------------------------------------------------- إرسال FCM

async function deliver(sa, project, fsBase, fsTok, to, tokens, data, ttl) {
  const fcmTok = await accessToken(sa, SCOPES.fcm);
  let sent = 0;
  await Promise.all(tokens.map(async (t) => {
    const r = await fetch(`https://fcm.googleapis.com/v1/projects/${project}/messages:send`, {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + fcmTok, 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: { token: t, data, android: { priority: 'HIGH', ttl } } })
    });
    if (r.ok) { sent++; return; }
    const err = await r.json().catch(() => ({}));
    // توكن ميت: ننظّفه
    if (r.status === 404 || JSON.stringify(err).includes('UNREGISTERED')) {
      await fetch(`${fsBase}/tokens/${to}/devices/${encodeURIComponent(t)}`, {
        method: 'DELETE', headers: { Authorization: 'Bearer ' + fsTok }
      }).catch(() => {});
    }
  }));
  return sent;
}

// ---------------------------------------------------------------- Firestore / RTDB

async function listTokens(fsBase, uid, tok) {
  const r = await fetch(`${fsBase}/tokens/${uid}/devices?pageSize=20`, { headers: { Authorization: 'Bearer ' + tok } });
  if (!r.ok) return [];
  const j = await r.json();
  return (j.documents || []).map((d) => decodeURIComponent(d.name.split('/').pop()));
}

async function rtdbGet(base, path, tok) {
  const r = await fetch(`${base}/${path}.json?access_token=${encodeURIComponent(tok)}`);
  if (!r.ok) return null;
  return await r.json();
}

// ---------------------------------------------------------------- Google OAuth (service account)

const tokenCache = new Map();

async function accessToken(sa, scope) {
  const c = tokenCache.get(scope);
  if (c && c.exp > Date.now() + 60000) return c.token;
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const claim = b64url(JSON.stringify({
    iss: sa.client_email, scope, aud: 'https://oauth2.googleapis.com/token', iat: now, exp: now + 3600
  }));
  const key = await crypto.subtle.importKey(
    'pkcs8', pemToBuf(sa.private_key), { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']
  );
  const sig = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(header + '.' + claim));
  const jwt = header + '.' + claim + '.' + b64url(sig);
  const r = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: 'grant_type=' + encodeURIComponent('urn:ietf:params:oauth:grant-type:jwt-bearer') + '&assertion=' + jwt
  });
  if (!r.ok) throw new Error('oauth failed: ' + (await r.text()));
  const j = await r.json();
  tokenCache.set(scope, { token: j.access_token, exp: Date.now() + (j.expires_in || 3600) * 1000 });
  return j.access_token;
}

// ---------------------------------------------------------------- التحقق من Firebase ID token

let jwks = null, jwksAt = 0;

async function verifyIdToken(token, project) {
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  let header, payload;
  try {
    header = JSON.parse(new TextDecoder().decode(b64urlToBuf(parts[0])));
    payload = JSON.parse(new TextDecoder().decode(b64urlToBuf(parts[1])));
  } catch (_) { return null; }
  if (header.alg !== 'RS256' || !header.kid) return null;

  const now = Math.floor(Date.now() / 1000);
  if (payload.aud !== project || payload.iss !== 'https://securetoken.google.com/' + project) return null;
  if (!payload.sub || typeof payload.sub !== 'string') return null;
  if (payload.exp < now || payload.iat > now + 300) return null;

  if (!jwks || Date.now() - jwksAt > 3600 * 1000 || !jwks.keys.some((k) => k.kid === header.kid)) {
    const r = await fetch('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com');
    if (!r.ok) return null;
    jwks = await r.json();
    jwksAt = Date.now();
  }
  const jwk = jwks.keys.find((k) => k.kid === header.kid);
  if (!jwk) return null;
  const key = await crypto.subtle.importKey(
    'jwk', jwk, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify']
  );
  const ok = await crypto.subtle.verify(
    'RSASSA-PKCS1-v1_5', key, b64urlToBuf(parts[2]), new TextEncoder().encode(parts[0] + '.' + parts[1])
  );
  return ok ? payload.sub : null;
}

// ---------------------------------------------------------------- أدوات

function json(o, status = 200) {
  return new Response(JSON.stringify(o), { status, headers: { 'Content-Type': 'application/json' } });
}
function b64url(x) {
  const bytes = typeof x === 'string' ? new TextEncoder().encode(x) : new Uint8Array(x);
  let s = '';
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
function b64urlToBuf(s) {
  s = s.replace(/-/g, '+').replace(/_/g, '/');
  while (s.length % 4) s += '=';
  const bin = atob(s);
  const buf = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) buf[i] = bin.charCodeAt(i);
  return buf;
}
function pemToBuf(pem) {
  const b64 = pem.replace(/-----[^-]+-----/g, '').replace(/\s+/g, '');
  const bin = atob(b64);
  const buf = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) buf[i] = bin.charCodeAt(i);
  return buf.buffer;
}

// ----------------------------------------------------------------------------
// صفحة رابط الحساب (الباركود): GET /u/<uid>
// تفتح التطبيق مباشرة على حساب المستخدم، وإذا التطبيق مو منصّب تعرض رسالة.
// ----------------------------------------------------------------------------
const APP_PACKAGE = 'com.e4classb.android';

function profilePage(uid) {
  const deep = `chatapp://user/${uid}`;
  const intent = `intent://user/${uid}#Intent;scheme=chatapp;package=${APP_PACKAGE};end`;
  const html = `<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>فتح الحساب</title>
<style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#0f1424;color:#fff;font-family:system-ui,sans-serif;text-align:center}
.c{padding:28px;max-width:360px}a{display:inline-block;margin-top:18px;padding:14px 30px;border-radius:999px;background:#e91e8c;color:#fff;text-decoration:none;font-weight:700}
p{color:#b0b3b8;line-height:1.7}</style></head><body><div class="c">
<h2>جاري فتح الحساب بالتطبيق...</h2>
<p>إذا ما انفتح التطبيق تلقائياً اضغط الزر. وإذا ما عندك التطبيق نصّبه أول وبعدها امسح الباركود مرة ثانية.</p>
<a href="${intent}">فتح بالتطبيق</a></div>
<script>
var ua = navigator.userAgent || '';
if (/Android/i.test(ua)) { location.replace(${JSON.stringify(intent)}); }
else { setTimeout(function(){ location.href = ${JSON.stringify(deep)}; }, 100); }
</script></body></html>`;
  return new Response(html, { status: 200, headers: { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'public, max-age=3600' } });
}
