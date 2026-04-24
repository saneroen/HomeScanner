# HomeScanner Mobile (React Native + Expo + EAS)

This is a **separate** cross-platform rewrite scaffold located in [`mobile-rn/`](mobile-rn/), intentionally isolated from the existing Android Kotlin app in [`app/`](app/).

## Prerequisites

- Node.js managed via `nvm`
- Expo CLI (via `npx`)
- EAS account for cloud builds

## Setup

1. Use Node from `nvm`:

   ```bash
   export NVM_DIR="/Users/santoshmadugundi/.nvm"
   source "$NVM_DIR/nvm.sh"
   nvm use 22.17.0
   ```

2. Install dependencies:

   ```bash
   cd mobile-rn
   npm install
   ```

## Run locally

From [`mobile-rn/package.json`](mobile-rn/package.json):

- `npm run start` – start Expo dev server
- `npm run android` – run on Android emulator/device
- `npm run ios` – run on iOS simulator/device (macOS)
- `npm run web` – run web preview

## EAS configuration

Profiles are defined in [`mobile-rn/eas.json`](mobile-rn/eas.json):

- `development`
- `preview`
- `production`

App identifiers and owner are in [`mobile-rn/app.json`](mobile-rn/app.json). Update placeholders before first EAS build:

- `expo.owner`
- `expo.extra.eas.projectId`

## Build commands

From [`mobile-rn/package.json`](mobile-rn/package.json):

- Android:
  - `npm run eas:build:android:dev`
  - `npm run eas:build:android:preview`
  - `npm run eas:build:android:prod`
- iOS:
  - `npm run eas:build:ios:dev`
  - `npm run eas:build:ios:preview`
  - `npm run eas:build:ios:prod`

Submit commands:

- `npm run eas:submit:android`
- `npm run eas:submit:ios`

## Initial architecture folders

Scaffolded structure:

- [`mobile-rn/src/core/`](mobile-rn/src/core/)
- [`mobile-rn/src/data/`](mobile-rn/src/data/)
- [`mobile-rn/src/features/`](mobile-rn/src/features/)
- [`mobile-rn/src/integrations/`](mobile-rn/src/integrations/)
- [`mobile-rn/src/ui/`](mobile-rn/src/ui/)
- [`mobile-rn/app/`](mobile-rn/app/)

These are placeholders for the rewrite plan documented in [`plans/react-native-eas-rewrite-plan.md`](plans/react-native-eas-rewrite-plan.md).
