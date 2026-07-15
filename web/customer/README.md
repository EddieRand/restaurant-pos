# Customer QR Ordering

This is the source app for the customer QR ordering page served by the Ktor
server at `/qr/`.

## Commands

- `npm run dev`: run the customer app locally on port `5175`.
- `npm run build`: build the static assets into
  `server/src/main/resources/customer`.

The build script reuses the Vite binary installed for `web/admin` so this
iteration does not require a separate dependency install.
