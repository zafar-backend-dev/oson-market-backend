# Oson Market Backend

Java 17+ · Spring Boot 3.5 · Maven · PostgreSQL 16+

`domain` ichida auth uchun ishlatiladigan 7 ta JPA entity bor. HTTP endpointlar `auth` modulida joylashgan. API integratsiya prompti [prompts/auth.txt](prompts/auth.txt) faylida.

## Ishga tushirish

1. PostgreSQL’da `src/main/resources/application.yaml` faylidagi bazani yarating yoki mavjud bazaga ulaning. Jadvallari hali yo‘q yangi bazada to‘liq cheklovlar, indekslar va boshlang‘ich to‘lov usullari kerak bo‘lsa, avval `database/oson_market_v2_schema.sql` faylini shu bazaga bajaring.
2. `application.yaml` ichida PostgreSQL, Google OAuth, JWT va Brevo sozlamalarini tekshiring. Brevo jo‘natuvchi email manzili tasdiqlangan bo‘lishi kerak. Google OAuth mijozining ruxsat etilgan qaytish manzillariga `http://localhost:8080/login/oauth2/code/google` manzilini kiriting.
3. IntelliJ IDEA’da `pom.xml` faylini Maven loyihasi sifatida ochib, `uz.osonmarket.OsonMarketApplication` klassini ishga tushiring. Terminaldan `mvn spring-boot:run` buyrug‘i ham ishlaydi.

Dastur `application.yaml` ichida berilgan `ddl-auto: update` bilan auth uchun kerakli qo‘shimcha ustunlarni yaratadi. SQLdagi email saqlovchi foydalanuvchi jadvaliga `username`, `google_subject` va `credential_version` qo‘shilgan. Tasdiqlash kodi jadvali kod yuborilgan vaqt va ro‘yxatdan o‘tish uchun vaqtincha saqlanadigan BCrypt xeshini ham saqlaydi.

## Auth API

API javoblari va Brevo xatlari uchun `Accept-Language: uz` yoki `Accept-Language: ru` sarlavhasini yuboring. Standart til o‘zbekcha. JSON so‘rovlari uchun `Content-Type: application/json` ishlating.

| Metod va manzil | JSON maydonlari | Natija |
| --- | --- | --- |
| `POST /api/auth/sign-up` | `firstName`, `lastName`, `email`, `username`, `password` | `202` va `challengeId`. Emailga kod yuboriladi. |
| `POST /api/auth/sign-up/verify` | `challengeId`, `code` | `201` va JWT. Akkaunt va profil yaratiladi. |
| `POST /api/auth/sign-in` | `login`, `password` | `202` va `challengeId`. `login` email yoki username bo‘ladi. |
| `POST /api/auth/sign-in/verify` | `challengeId`, `code` | `200` va JWT. |
| `POST /api/auth/forgot-password` | `email` | `202` va `challengeId`. Mavjud faol akkauntga tiklash kodi yuboriladi. |
| `POST /api/auth/reset-password` | `challengeId`, `code`, `newPassword` | `200`. Parol yangilanadi va eski tokenlar bekor qilinadi. |
| `GET /api/auth/me` | `Authorization: Bearer <accessToken>` | Profil, rol va tanlangan til. |
| GET /oauth2/authorization/google | Brauzerda ochiladi | Google orqali kirishni boshlaydi. |
| POST /api/auth/google/exchange | loginCode | Frontend callback kodini JWTga almashtiradi. |

Tasdiqlash kodini JSONda satr sifatida yuboring. Masalan, `"code": "004321"`. Google orqali birinchi kirishda profil avtomatik yaratiladi. Keyingi kirishlar Google akkauntining doimiy identifikatori orqali o‘sha profilni topadi. Google oqimida Brevo kodi yuborilmaydi.

Email orqali kirish va ro‘yxatdan o‘tishda qayta kod yuborish uchun birinchi bosqich endpointini yana chaqiring. Kutish vaqti, kod muddati va urinishlar soni `application.yaml` faylida boshqariladi.

## Swagger

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- Sign-in yoki sign-up kodini tasdiqlagach, Swagger UI dagi Authorize maydoniga accessToken qiymatini Bearer prefiksisiz kiriting. Keyin GET /api/auth/me ni sinang.
- Google OAuth oqimini brauzerda http://localhost:8080/oauth2/authorization/google orqali boshlang.

Tilni tanlash uchun Accept-Language sarlavhasida bitta qiymat yuboring (uz yoki ru). Brauzer yuboradigan tillar ro‘yxati standart o‘zbek tilidan foydalanadi. Swagger UI dagi Accept-Language tanlovining standart qiymati uz. Tanlangan til sign-in javobi va email kod xatiga bir xil qo‘llanadi.
`/api/profile` endpointlari va klient integratsiyasi uchun [promps/profile.txt](promps/profile.txt) fayliga qarang.

Google callback va POST /api/auth/google/exchange shartnomasi prompts/google-authorızxatıon.txt faylida.
