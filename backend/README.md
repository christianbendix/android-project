# Backend (PocketBase)

The app talks to a PocketBase server (`PB_BASE_URL` in `app/build.gradle.kts`). Everything the
server needs beyond a stock PocketBase lives here, so it can be rebuilt from the repo.

| Path | What it is |
| --- | --- |
| `pb_migrations/` | Schema and access rules: `phone` on `users`, plus `groups`, `expenses`, `payments`, `messages`. Amounts are whole øre. |
| `pb_hooks/lookup.pb.js` | `GET /api/splendit/lookup?q=` finds one user by exact phone or email, for adding members. |
| `scripts/seed_demo.py` | Demo users 2 and 3, five more people, and four groups with history. See [`DEMO.md`](../DEMO.md). |

Needs PocketBase **v0.23 or newer** (the JS migration and hook APIs changed in v0.23).

## Deploying

Copy both folders next to the `pocketbase` binary (or point at them) and restart:

```bash
./pocketbase serve --migrationsDir ./pb_migrations --hooksDir ./pb_hooks
```

Migrations run on start-up; the new collections then show in the admin UI. Run the seed
script after that.

### Behind nginx

Realtime is a long-lived server-sent-events stream on `/api/realtime`. With nginx's defaults
the stream is buffered and cut after 60 s. The app reconnects and reloads when that happens, so
nothing is lost, but notifications arrive late. Give that path its own block:

```nginx
location /api/realtime {
    proxy_pass http://127.0.0.1:8090;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 1h;
}
```

## Access rules in short

- A user sees themselves and the people they share a group with. Anyone else is found only by
  exact phone or email through the lookup route, which never lists users.
- Only members read a group and its expenses, payments and messages.
- Only the admin edits or deletes a group, and can't hand the admin role away.
- Expenses and messages are posted as yourself, by a member.
- A payment is from yourself, to the expense's payer, once per expense.

Not yet enforced on the server: that an expense's payer and participants are group members,
and that the shares add up to the amount. The app guarantees both.

## Checking the rules by hand

After seeding, as Sofie (`sofie@example.com`):

- `GET /api/collections/groups/records` lists Roskilde 2026, Kitchen K4 and Lisbon in May, not
  Friday padel.
- `GET /api/collections/users/records` lists only people in those groups.
- `POST /api/collections/payments/records` with `from` set to Mads's id is refused.
