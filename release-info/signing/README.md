# Android Signing Setup

1. Generate a release keystore locally.
2. Copy `keystore.properties.example` to `keystore.properties`.
3. Fill the values with your real signing credentials.
4. Never commit `keystore.properties` or keystore binaries.

For CI, store equivalent credentials in GitHub Actions secrets.
