# PerLauncher Ely.by integration notice

- Ely.by authentication endpoint: `https://authserver.ely.by`.
- Ely.by Authlib Injector API root: `https://account.ely.by/api/authlib-injector`.
- Ely.by documents `/auth/authenticate` and `/auth/refresh`; TOTP is sent as `password:token` when the user supplies a 2FA code.
- Authlib Injector is downloaded unmodified at runtime from the official download service, with BMCLAPI as a fallback mirror. The file is verified against SHA-256 before being used.
- Authlib Injector project: https://github.com/yushijinhun/authlib-injector
- Authlib Injector license and exception: https://github.com/yushijinhun/authlib-injector/blob/develop/COPYING.md
- Ely.by documentation: https://docs.ely.by/en/minecraft-auth.html and https://docs.ely.by/en/authlib-injector.html

The login form sends credentials directly from the device to Ely.by over HTTPS. PerLauncher does not persist the account password or TOTP code; the issued account token is saved in the same account storage mechanism already used by the launcher.
