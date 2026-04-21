# CU View – Privacy Policy

*Last updated: 2026-04-17*

## What data the app collects

CU View stores the following data locally on your device:

- Your ClickUp OAuth access token, encrypted using Android's EncryptedSharedPreferences.
- A task cache (task names and IDs from the selected ClickUp view), encrypted using Android's EncryptedSharedPreferences.

No data is collected by the developer. No analytics, crash reporting, or tracking libraries are included.

## How the data is used

The access token is sent to the ClickUp API (`api.clickup.com`) to fetch tasks for the selected view. During the initial OAuth sign-in, the token also briefly transits through a Cloudflare Worker acting as a redirect endpoint — it is not logged or retained there.

## OAuth flow

During sign-in, an authorization code is exchanged for an access token via a Cloudflare Worker acting as a redirect endpoint. The worker exchanges the code with ClickUp and immediately forwards the token back to the app. The token is not logged or retained by the worker.

## Data sharing

No personal data is shared with third parties beyond the ClickUp API requests initiated by you and the Cloudflare Worker used solely as an OAuth redirect endpoint during sign-in.

## Data deletion

Removing the widget or clearing the app's data via Android Settings deletes all locally stored data.

## Contact

Questions? Open an issue at [github.com/raven-wing/cuview/issues](https://github.com/raven-wing/cuview/issues).
