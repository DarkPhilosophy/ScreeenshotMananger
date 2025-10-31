# Ko - Screenshot Manager

A modern Android application to automatically manage screenshots.

## Features

- **Screenshot Monitoring**: Automatically detects new screenshots.
- **Screenshot Management**: View all your screenshots in one place.
- **Organize**: Filter screenshots into "Marked", "Kept", and "All" lists.
- **Actions**: Keep the important screenshots and delete the rest.
- **Background Service**: A service to monitor screenshots even when the app is not open.
- **Scheduled Deletion**: Uses `WorkManager` to schedule screenshot deletions.
- **Settings**: A dedicated settings screen for customization.

## Permissions

The app requires the following permissions to function correctly:
- **Storage Access**: To read screenshots.
- **Notifications**: To inform about new screenshots.
- **Draw Over Other Apps**: For quick actions when a screenshot is taken.
