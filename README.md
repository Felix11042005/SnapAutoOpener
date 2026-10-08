# SnapAutoOpener

Android proof-of-concept for local Snapchat UI automation.

## Private Space / Vertraulicher Bereich

SnapAutoOpener must be installed inside the same Android profile as Snapchat.

For Google's Private Space:
1. Unlock Private Space.
2. Install SnapAutoOpener inside Private Space.
3. Make sure Snapchat is also installed inside Private Space.
4. Open SnapAutoOpener from Private Space.
5. Enable notification access and accessibility for this copy of SnapAutoOpener.
6. Keep Private Space unlocked while automation is running.

Android intentionally isolates Private Space as a separate user profile. When the space is locked, apps inside it are stopped and cannot perform background work.

No Snapchat credentials or private Snapchat APIs are used.
