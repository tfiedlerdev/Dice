# Play Store metadata - upload without copy-pasting

Everything in `fastlane/metadata/android/` (title, descriptions, screenshots, icon,
feature graphic, changelog) is in the exact layout the `fastlane supply` tool expects.
Once set up, `bundle exec fastlane supply` pushes all of it to Play Console in one
command - no manual copy-pasting into the web UI.

## One-time setup (you have to do this part - it needs your own Google account)

1. **Install fastlane** (needs Ruby, which macOS already has):
   ```
   cd /Users/tobi/Workspaces/AndroidStudioProjects/Dice
   bundle install
   ```

2. **Create a Google Play service account key** - this is the credential that lets
   the command line publish on your behalf, instead of you logging into the website:
   - In [Play Console](https://play.google.com/console), go to
     **Setup → API access**.
   - Click **Choose a project** (or create one) to link a Google Cloud project, if
     you haven't already.
   - Click **Create new service account** - this opens Google Cloud Console's
     service account page (same account, different console).
   - Create the service account, then open it → **Keys** → **Add key** → **Create
     new key** → type **JSON** → Create. This downloads a `.json` file - that's
     your credential.
   - Back in Play Console's API access page, find the new service account and
     **Grant access**. Give it at least the **"Edit store listing"** permission
     (under the app's permissions) - it doesn't need release/production-rollout
     permissions just to update text, screenshots, and graphics.
   - Save the downloaded JSON file as:
     ```
     /Users/tobi/Workspaces/AndroidStudioProjects/Dice/fastlane/play-store-credentials.json
     ```
     (already in `.gitignore` - never commit this file, it's equivalent to a password.)

3. **Your app must already exist in Play Console** with at least one draft release
   uploaded (fastlane supply updates an existing app's listing; it can't create the
   app itself on Play for you the very first time). If you haven't created the app
   listing yet, do that once manually, upload your first `.aab`, then come back here.

## Uploading the store listing

From the repo root:

```
bundle exec fastlane supply --skip_upload_apk --skip_upload_aab
```

The `--skip_upload_apk --skip_upload_aab` flags upload **only** the metadata/
screenshots/graphics in this folder, leaving your actual app binary upload (and its
release rollout) to whatever process you already use (Android Studio's "Generate
Signed Bundle", a CI job, etc.) - keeps the two concerns separate.

To preview what would be uploaded without actually sending anything:
```
bundle exec fastlane supply --skip_upload_apk --skip_upload_aab --validate_only true
```

## What's in here

```
fastlane/metadata/android/
  en-US/   de-DE/   es-ES/   fr-FR/   it-IT/   ja-JP/   pt-BR/
    title.txt                 - app name (same across locales: "Dice Sim 3D")
    short_description.txt     - the one-liner shown under the name (≤80 chars)
    full_description.txt      - the full store listing text (≤4000 chars)
    changelogs/1.txt          - "what's new" for versionCode 1 (bump the filename
                                 to match versionCode for future releases)
    images/
      icon.png                - 512x512 hi-res listing icon (Play rounds it itself)
      featureGraphic.png      - 1024x500 banner
      phoneScreenshots/       - 1080x1920 marketing screenshots in that locale
```

Regenerating the screenshots/icon/graphic themselves (if you change the app's UI or
want a different look) is a separate step - see the comment at the top of
`app/src/androidTest/java/tfdev/dice/ScreenshotSetupTest.kt` for the recipe used to
produce the raw screenshots this metadata's images were composited from.

## Still manual in Play Console (fastlane/the API can't do these for you)

- Content rating questionnaire
- Data Safety form (must stay consistent with the privacy policy)
- Target audience / ads declaration
- Pricing & countries
- The actual production release rollout (uploading/promoting the signed `.aab`)
