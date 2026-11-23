# Demo: three phones, one group

Three emulators sit side by side. Emulator 1 signs up live, makes a group with the two others,
adds a bill, and the other two get notified and pay. No real money moves: paying marks your
share as settled (FR-04-B).

## Before the demo

1. **Server.** Deploy `backend/` to the PocketBase server (see [`backend/README.md`](backend/README.md)).
2. **Seed.** Create users 2 and 3 and their history:

   ```bash
   export PB_URL=https://pb-demo.cbendix.dk
   export PB_ADMIN_EMAIL=...  PB_ADMIN_PASSWORD=...  DEMO_PASSWORD=...
   python3 backend/scripts/seed_demo.py
   ```

   | | Name | Phone | Email |
   | --- | --- | --- | --- |
   | User 2 | Sofie Lund | +45 20 00 00 02 | sofie@example.com |
   | User 3 | Mads Holm | +45 20 00 00 03 | mads@example.com |

   Both use `DEMO_PASSWORD`. Sofie and Mads already share *Lisbon in May*, and each is in two
   more groups with other people, so their home screens and balances look lived-in.

3. **Emulators.** Three AVDs on **Android 13 or newer** (API 33+, for the notification
   permission prompt). Install the debug build on each:

   ```bash
   ./gradlew assembleDebug
   for s in $(adb devices | awk 'NR>1 && $2=="device" {print $1}'); do
     adb -s "$s" install -r app/build/outputs/apk/debug/app-debug.apk
   done
   ```

4. Log in as Sofie on emulator 2 and Mads on emulator 3. Allow notifications when asked.
   Leave the app open or in the background; notifications come over the realtime connection,
   so the app process must be alive (no Firebase push yet).

### Rehearsing again

Reset the demo groups and delete the account emulator 1 made, then seed again:

```bash
python3 backend/scripts/seed_demo.py --reset --delete-user <user 1's email>
```

`--reset` deletes every group Sofie or Mads is in (including the seeded ones, which are then
recreated) with all their expenses, payments and messages.

## The run-through

| # | Where | Do | You should see |
| --- | --- | --- | --- |
| 1 | Emulator 1 | **Create account**: name, email, phone (🇩🇰 +45 is the default; 8 digits), password. | Signed straight in; empty home screen. |
| 2 | Emulator 1 | **+** → name *"Friday dinner"*, a description. *Phone or email* tab: add `20000002` and `20000003`. **Create group**. | Sofie and Mads appear as found and selected; the group opens with "You created the group". |
| 3 | Emulators 2, 3 | — | Notification "*‹you› added you to the group*". Tap it: the app opens in *Friday dinner*. |
| 4 | Emulator 1 | **Expense** → *Dinner*, 450, everyone in, **I paid — split evenly** → **Send requests** → **Post & send requests**. | The bill appears for everyone at once. |
| 5 | Emulators 2, 3 | — | Notification "*‹you› added Dinner. Your share is 150 kr.*" The group says "You owe ‹you› 150 kr". |
| 6 | Emulators 2, 3 | **Pay 150 kr** on the bill. | Emulator 1 is notified "*Sofie paid you 150 kr*", then Mads; the feed shows both payments and the balance drops to 0 ("Everyone is settled up"). |

### If something goes wrong

- **No notification**: check the permission was allowed (Settings → Apps → Splitwheel →
  Notifications) and the app wasn't swiped away. The data still syncs when the app is opened.
- **"Nobody with that phone number"** in step 2: the seed script didn't run against this server,
  or the number was typed with a different country code.
- **Too many attempts** at sign-up: PocketBase rate-limits per IP, and all three emulators share
  the host's IP. Wait a minute.

## What is real and what isn't yet

- Real: accounts, groups, members, expenses, payments and chat messages are stored in
  PocketBase, only members can read them, and changes reach the other phones in realtime.
- A write that fails because of the network shows at once and is retried in the background
  until it goes through, without duplicates. It is held in memory only: a write made offline is
  lost if the app is killed before it is sent (the persistent outbox is issue #10).
- Not on the server yet: the jackpot (off by default when creating a group), photos in the chat
  (the photo button is hidden), and the wheel/high-card draw, which runs on the phone that posts
  the bill and is stored as the result (FR-06-C asks for a draw on the server).
