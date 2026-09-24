# Fox Drop: Google Play listing

Paste these into Play Console. Everything here matches what the app actually does.

## Store listing (Grow users → Store presence → Main store listing)

**App name** (30 max)
```
Fox Drop: Shop & Event Alerts
```

**Short description** (80 max)
```
Item Shop, live event countdowns and wishlist alerts. Unofficial fan app.
```

**Full description** (4000 max)
```
Never miss a skin or a live event again. 🦊

Fox Drop keeps an eye on the game for you and taps you on the shoulder when something happens.

ITEM SHOP
• See today's whole Item Shop with prices, sale prices and what's leaving today
• Countdown to the next shop reset

WISHLIST ALERTS
• Heart any skin, emote or pickaxe, even ones that aren't in the shop yet
• Get an alert the day it shows up

LIVE EVENTS
• A big countdown to the next live event
• Reminders a day before, an hour before, 10 minutes before, and the moment it goes live
• Heard about an event? Add it yourself

UPDATES & SERVERS
• Alerts when a new update drops, when maintenance is coming, when servers go down, and when they're back up

NEWS & NEW COSMETICS
• In-game news and Epic notices
• Everything newly added to the game files

FOX CHAT (INVITE ONLY)
• Chat with your crew and share live-event tips
• Invite code from the admin; report or hide anyone

NO ADS, NO TRACKING
No email or phone number needed, and your wishlist stays on your phone. Fox Chat stores only the nickname and messages you choose to post.

Fox Drop is an unofficial fan app. It is not made, endorsed or sponsored by Epic Games. Fortnite is a trademark of Epic Games, Inc. Game data comes from fortnite-api.com and Epic's public status page.
```

**Graphics**
- App icon: `store/icon-512.png`
- Feature graphic: `store/feature-graphic-1024x500.png`
- Phone screenshots (2-8, you take these on the Fold with the cover screen): the Shop tab, the Events countdown, the Wishlist, the Servers tab, and a notification.

**Category:** Entertainment  ·  **Tags:** Games news/tools
**Contact email:** a personal address (Play requires one and shows it publicly)
**Website:** https://lateraldamage.github.io/fox-drop/
**Privacy policy:** https://lateraldamage.github.io/fox-drop/privacy.html

## App content (Policy → App content)

| Form | Answer |
|---|---|
| Privacy policy | https://lateraldamage.github.io/fox-drop/privacy.html |
| Ads | No, the app has no ads |
| App access | **Some functionality is restricted.** Instructions: "Fox Chat (the Chat tab) is invite-only. Open the Chat tab, type any nickname and the invite code, then tap Join the crew." Give the current code from Firestore config/invite (change it back afterwards if you like). Everything else needs no login. |
| Content rating (IARC) | Category: Reference, News, or Educational. No violence, no sexual content, no profanity, no drugs, no gambling. **Users can interact / exchange content: Yes** (Fox Chat, invite-only, moderated, with report and hide). No location sharing, no digital purchases. The rating may come out Teen because of chat; that matches Fortnite. |
| Target audience | **13-15, 16-17, 18+**. Do NOT tick under-13 boxes (that pulls in the Families policy). "Could it unintentionally appeal to children?": answer honestly; if it asks, the cartoon fox might, so explain it's a companion to a Teen-rated game. |
| News app | No (it shows game news, but it isn't a news publisher) |
| Data safety | **Collects data: Yes. Shares data: No** (Google Firebase is a service provider, which Play does not count as sharing). Encrypted in transit: Yes. Users can request deletion: Yes (via the admin or a GitHub issue; see privacy policy). Data types, all for **App functionality**, none for ads or analytics: **Personal info → Name** (the chat nickname; optional). **Messages → Other in-app messages** (chat, tips, comments; optional). **Device or other IDs** (the anonymous Firebase ID; required, created on first launch). Processed ephemerally: No for all three. |
| Government app | No |
| Financial features | None |
| Health | None |
| Exact alarm permission | Uses SCHEDULE_EXACT_ALARM (user-granted, not USE_EXACT_ALARM), so no declaration form is needed |
| Advertising ID | No |

## Release

- Upload: `app/build/outputs/bundle/play/app-play.aab` (built with `gradlew bundlePlay`)
- Every later upload needs a higher `versionCode` in `app/build.gradle.kts`.
- Use **Play App Signing** (the default). Google holds the real app-signing key; our key in `C:\Users\densonjr\foxdrop-keys` is only the upload key. If it's ever lost, Google can reset it, but back the folder up anyway.
