# Signing keys

## Debug: `debug.keystore` (committed)

All debug builds are signed with this key on every machine, so a test device
can update the app in place after you switch computers. It isn't a secret: it
only signs debug builds, which can't be published.

- Store/key password: `android`
- Alias: `androiddebugkey`
- SHA-256: `F3:B2:AD:C3:E7:D9:C3:4F:3D:B9:53:7B:E3:C2:62:31:D5:F5:DF:98:6D:48:A1:04:7B:44:C3:44:63:E0:3E:9F`

Don't replace it: a new key forces an uninstall on every test device.

## Release (never committed)

Create `keystore.properties` in the project root (it's git-ignored):

```properties
storeFile=keystore/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Keep the release keystore and its passwords out of the repo, and back them up:
losing them means you can't publish updates. Without `keystore.properties`,
release builds are unsigned.
