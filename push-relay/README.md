# إشعارات مجانية بدون بطاقة (Cloudflare Worker + FCM)

## 1) ملف الـ service account من Firebase
Firebase Console → ⚙️ Project settings → **Service accounts** → **Generate new private key**.
ينزل ملف JSON. افتحه وانسخ محتواه **كامل**. (هذا سر، لا ترفعه بأي مكان عام ولا تحطه بالتطبيق.)

## 2) حساب Cloudflare (مجاني، بدون بطاقة)
سجّل بـ https://dash.cloudflare.com/sign-up

## 3) أنشئ الـ Worker من الموقع (بدون برمجة)
1. **Workers & Pages → Create → Create Worker** (سمّه `chatapp-push`) → **Deploy**.
2. اضغط **Edit code**، امسح كل شي والصق محتوى `worker.js` → **Deploy**.
3. **Settings → Variables and Secrets → Add**:
   - النوع **Secret**، الاسم `SERVICE_ACCOUNT`، القيمة: محتوى ملف الـ JSON كامل → **Deploy**.
4. انسخ رابط الـ Worker (يشبه `https://chatapp-push.xxxx.workers.dev`).

## 4) التطبيق
افتح `app/src/main/java/com/chatapp/messenger/Push.kt` وحط الرابط:

    const val PUSH_URL = "https://chatapp-push.xxxx.workers.dev"

ثم ابنِ التطبيق ووزّعه.

## 5) تجربة
اطفي التطبيق عند الطرف الثاني (سحب من الشاشات الأخيرة) وابعثله رسالة: لازم يوصله إشعار.
إذا ما وصل: افتح رابط الـ Worker بالمتصفح (لازم يطلع `{"ok":true,...}`)، وشوف Logs من لوحة Cloudflare.

## ملاحظات
- الحد المجاني 100,000 طلب باليوم (طلب واحد لكل رسالة).
- الـ Worker يتأكد إن الرسالة موجودة فعلاً ومرسلها هو صاحب الطلب، فما أحد يگدر يرسل إشعارات مزوّرة.
- بعض الأجهزة (أوبو/ريلمي/شاومي...) لازم من إعدادات التطبيق: البطارية «بدون قيود» + «التشغيل التلقائي».
- إشعارات المجموعات شغالة: الـ Worker يرسل لكل أعضاء المجموعة (ماعدا المرسل) بدفعات من 10 أعضاء، والتطبيق يكرر الطلب لين يخلصون
  (حد Cloudflare المجاني 50 طلب خارجي لكل استدعاء). المجموعة الكبيرة تستهلك أكثر من طلب لكل رسالة من حصة الـ 100,000 اليومية.
  المجموعات أكبر من 300 عضو: الإشعار لأول 300 عضو فقط (غيّر `GROUP_MAX_MEMBERS` بـ worker.js).
- بعد تحديث `worker.js` لازم تنشره من جديد بـ Cloudflare (Edit code → Deploy).
