# Releasing

Releases are two APKs on GitHub, NIHIL for Wear OS and its watch face, built and
signed by `.github/workflows/release.yml` when you push a tag, with `SHA256SUMS`
beside them. `nihil watch` on the computer (Preferences, Devices, *Install on a
watch*) installs the newest one: a stable release when there is one, else the
newest pre-release, and only once `SHA256SUMS` says the APKs are those.

Android only installs an update over an installed app signed with **the same
key**, so the key is made once and kept forever. It can be the launcher's own
(the four secrets below, with the same values) or one of the watch's; a watch
that has a debug build from `tools/watch install` takes a release only after
`adb uninstall com.nihilshell.wear`, which `nihil watch` offers.

## Once: the key

The launcher's `RELEASING.md` has how to make one with `keytool`. Keep the
`.jks` and its passwords somewhere safe, with a backup; `.gitignore` keeps
`*.jks` out of the repository. Then, here, the four repository secrets
(Settings → Secrets and variables → Actions, or with `gh`):

```sh
base64 -w0 nihil-release.jks | gh secret set NIHIL_KEYSTORE_BASE64
gh secret set NIHIL_KEYSTORE_PASSWORD
gh secret set NIHIL_KEY_ALIAS
gh secret set NIHIL_KEY_PASSWORD
```

## Every release

1. In `gradle.properties`, set `nihilVersion`: both `versionCode`s follow it.
2. Add the changes to `CHANGELOG.md` and commit.
3. Tag with the same version and push the tag:

   ```sh
   git tag v0.1.0
   git push origin v0.1.0
   ```

The workflow checks that the tag is `nihilVersion` and above the last tag, runs
the tests, builds and signs both APKs and publishes the release. A tag with a
dash, `v0.1.0-rc.1`, is a pre-release.
