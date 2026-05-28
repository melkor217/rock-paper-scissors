# Localization Guidelines

Use these rules when adding or updating Android string resources.

- The app always runs with English locale at runtime (`AppLocale` in `RpsApplication` and `MainActivity`). Keep locale files translated anyway so switching languages later is easy.
- Translate every user-facing string key in all locale `strings.xml` files.
- Never translate app name: `app_name` must stay exactly `RPS Online` in every locale.
- Keep translated text concise so labels/buttons/toasts fit small screens.
- Preserve placeholders exactly (`%1$s`, `%2$d`, etc.).
- Keep punctuation and casing aligned with the base English meaning.
- Do not translate short gameplay tokens that are intentionally compact: `W`, `L`, `D`, `vs`.
- If a locale needs a fallback, use English only temporarily and replace it in the same change whenever possible.

