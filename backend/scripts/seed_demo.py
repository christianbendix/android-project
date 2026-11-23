#!/usr/bin/env python3
"""Seed PocketBase with the demo users, groups, expenses and payments.

Creates demo users 2 (Sofie) and 3 (Mads) with known phone numbers, five more people, and four
groups with some history, so the profiles look lived-in. User 1 is not seeded: they sign up
live in the demo.

Safe to run again: every record has a fixed id and existing ones are skipped.

    export PB_URL=https://pb-demo.cbendix.dk
    export PB_ADMIN_EMAIL=...          # a PocketBase superuser
    export PB_ADMIN_PASSWORD=...
    export DEMO_PASSWORD=...           # password for every seeded user (8+ characters)
    python3 backend/scripts/seed_demo.py

    # Before a rehearsal: delete every group a demo user is in (with their expenses,
    # payments and messages), delete the account user 1 made last time, then seed again.
    python3 backend/scripts/seed_demo.py --reset --delete-user peter@example.com

Standard library only, so it runs anywhere Python 3.8+ does.
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request


def pid(name):
    """A fixed 15-character PocketBase id, e.g. 'sofie' -> 'sofie0000000000'."""
    return (name + "0" * 15)[:15]


# (key, name, phone, email). Users 2 and 3 are the ones logged in on emulators 2 and 3.
USERS = [
    ("sofie", "Sofie Lund", "+4520000002", "sofie@example.com"),
    ("mads", "Mads Holm", "+4520000003", "mads@example.com"),
    ("emil", "Emil Berg", "+4520000004", "emil@example.com"),
    ("freja", "Freja Dahl", "+4520000005", "freja@example.com"),
    ("oliver", "Oliver Krogh", "+4520000006", "oliver@example.com"),
    ("ida", "Ida Nørgaard", "+4520000007", "ida@example.com"),
    ("clara", "Clara Juul", "+4520000008", "clara@example.com"),
]

# Each group's history is created in order, which is the order the app shows it in.
# ("expense", key, creator, title, kroner, payer, method, date, participants, shares or None)
# ("payment", key, expense key, from)  -- always to the expense's payer, for their share
# ("message", key, from, text)
GROUPS = [
    {
        "key": "groskilde",
        "name": "Roskilde 2026",
        "description": "Festival week. Tent, food and the bus there.",
        "admin": "mads",
        "members": ["mads", "sofie", "emil", "freja"],
        "history": [
            ("message", "mroskilde1", "sofie", "Tent is packed. Who is on groceries?"),
            ("message", "mroskilde2", "mads", "I'll grab them. The wheel decides who pays."),
            ("expense", "eroskilde1", "mads", "Groceries — Netto", 840, "mads", "wheel",
             "2026-06-27", ["mads", "sofie", "emil", "freja"], None),
            ("payment", "proskilde1", "eroskilde1", "sofie"),
            ("payment", "proskilde2", "eroskilde1", "freja"),
            ("expense", "eroskilde2", "emil", "Bus tickets", 1160, "emil", "even",
             "2026-06-28", ["mads", "sofie", "emil", "freja"], None),
            ("payment", "proskilde3", "eroskilde2", "mads"),
            ("message", "mroskilde3", "emil", "Rigged wheel. Paying later."),
        ],
    },
    {
        "key": "gkitchen",
        "name": "Kitchen K4",
        "description": "Shared kitchen on the 4th floor.",
        "admin": "sofie",
        "members": ["sofie", "oliver", "ida", "clara"],
        "history": [
            ("expense", "ekitchen1", "sofie", "Cleaning supplies", 320, "sofie", "even",
             "2026-10-02", ["sofie", "oliver", "ida", "clara"], None),
            ("payment", "pkitchen1", "ekitchen1", "oliver"),
            ("expense", "ekitchen2", "ida", "Coffee machine", 1499, "ida", "even",
             "2026-10-19", ["sofie", "oliver", "ida", "clara"],
             {"sofie": 500, "oliver": 333, "ida": 333, "clara": 333}),
            ("payment", "pkitchen2", "ekitchen2", "clara"),
            ("message", "mkitchen1", "ida", "Sending tonight!"),
        ],
    },
    {
        "key": "glisbon",
        "name": "Lisbon in May",
        "description": "Long weekend, 14–18 May.",
        "admin": "sofie",
        "members": ["sofie", "mads", "clara"],
        "history": [
            ("expense", "elisbon1", "sofie", "Apartment deposit", 2400, "sofie", "even",
             "2026-04-03", ["sofie", "mads", "clara"], None),
            ("payment", "plisbon1", "elisbon1", "clara"),
            ("message", "mlisbon1", "clara", "Booked the flat. The view is unreal."),
            ("expense", "elisbon2", "mads", "Airport taxi", 360, "mads", "card",
             "2026-05-14", ["sofie", "mads", "clara"], None),
            ("payment", "plisbon2", "elisbon2", "sofie"),
        ],
    },
    {
        "key": "gpadel",
        "name": "Friday padel",
        "description": "Court 3, every Friday at 17.",
        "admin": "oliver",
        "members": ["oliver", "mads", "emil"],
        "history": [
            ("expense", "epadel1", "oliver", "Court booking", 360, "oliver", "card",
             "2026-11-13", ["oliver", "mads", "emil"], None),
            ("payment", "ppadel1", "epadel1", "mads"),
            ("payment", "ppadel2", "epadel1", "emil"),
            ("message", "mpadel1", "mads", "Same time next week?"),
        ],
    },
]


class PocketBase:
    def __init__(self, url):
        self.url = url.rstrip("/")
        self.token = None

    def request(self, method, path, body=None, query=None):
        url = self.url + path
        if query:
            url += "?" + urllib.parse.urlencode(query)
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("Content-Type", "application/json")
        if self.token:
            req.add_header("Authorization", self.token)
        try:
            with urllib.request.urlopen(req, timeout=20) as res:
                text = res.read().decode()
                return res.status, json.loads(text) if text else None
        except urllib.error.HTTPError as e:
            text = e.read().decode()
            try:
                return e.code, json.loads(text)
            except ValueError:
                return e.code, {"message": text}

    def login_superuser(self, email, password):
        status, body = self.request(
            "POST",
            "/api/collections/_superusers/auth-with-password",
            {"identity": email, "password": password},
        )
        if status != 200:
            sys.exit(f"Superuser login failed ({status}): {body}")
        self.token = body["token"]

    def exists(self, collection, record_id):
        status, _ = self.request("GET", f"/api/collections/{collection}/records/{record_id}")
        return status == 200

    def create(self, collection, record):
        """Creates the record unless one with its id is already there. Returns True if new."""
        if self.exists(collection, record["id"]):
            return False
        status, body = self.request("POST", f"/api/collections/{collection}/records", record)
        if status != 200:
            sys.exit(f"Creating {collection}/{record['id']} failed ({status}): {body}")
        return True

    def list_ids(self, collection, filter_):
        status, body = self.request(
            "GET",
            f"/api/collections/{collection}/records",
            query={"filter": filter_, "perPage": 500, "fields": "id"},
        )
        if status != 200:
            sys.exit(f"Listing {collection} failed ({status}): {body}")
        return [item["id"] for item in body["items"]]

    def delete(self, collection, record_id):
        status, body = self.request("DELETE", f"/api/collections/{collection}/records/{record_id}")
        if status not in (204, 404):
            sys.exit(f"Deleting {collection}/{record_id} failed ({status}): {body}")


def reset(pb, delete_users):
    demo_ids = [pid(key) for key, *_ in USERS]
    filter_ = " || ".join(f"members.id ?= '{uid}'" for uid in demo_ids)
    groups = pb.list_ids("groups", filter_)
    for group_id in groups:
        # Expenses, payments and messages go with the group (cascade delete).
        pb.delete("groups", group_id)
    print(f"Deleted {len(groups)} group(s) with demo members.")
    for email in delete_users:
        for user_id in pb.list_ids("users", f"email = '{email}'"):
            pb.delete("users", user_id)
            print(f"Deleted user {email}.")


def seed(pb, password):
    users = {key: pid(key) for key, *_ in USERS}
    for key, name, phone, email in USERS:
        new = pb.create("users", {
            "id": users[key],
            "email": email,
            "emailVisibility": False,
            "verified": True,
            "password": password,
            "passwordConfirm": password,
            "name": name,
            "phone": phone,
        })
        print(f"{'Created' if new else 'Kept   '} user {name} ({phone}, {email})")

    for group in GROUPS:
        group_id = pid(group["key"])
        new = pb.create("groups", {
            "id": group_id,
            "name": group["name"],
            "description": group["description"],
            "admin": users[group["admin"]],
            "members": [users[m] for m in group["members"]],
        })
        print(f"{'Created' if new else 'Kept   '} group {group['name']}")

        expenses = {}
        for item in group["history"]:
            kind = item[0]
            if kind == "expense":
                _, key, creator, title, kroner, payer, method, date, people, shares = item
                amount = round(kroner * 100)
                share_ore = {p: round(amount / len(people)) for p in people}
                if shares:
                    share_ore = {p: round(shares[p] * 100) for p in people}
                expenses[key] = (users[payer], share_ore)
                pb.create("expenses", {
                    "id": pid(key),
                    "group": group_id,
                    "creator": users[creator],
                    "title": title,
                    "amount_ore": amount,
                    "payer": users[payer],
                    "method": method,
                    "date": f"{date} 12:00:00.000Z",
                    "participants": [users[p] for p in people],
                    "shares": {users[p]: v for p, v in share_ore.items()} if shares else None,
                })
            elif kind == "payment":
                _, key, expense_key, from_ = item
                payer, share_ore = expenses[expense_key]
                pb.create("payments", {
                    "id": pid(key),
                    "group": group_id,
                    "expense": pid(expense_key),
                    "from": users[from_],
                    "to": payer,
                    "amount_ore": share_ore[from_],
                })
            elif kind == "message":
                _, key, from_, text = item
                pb.create("messages", {
                    "id": pid(key),
                    "group": group_id,
                    "from": users[from_],
                    "text": text,
                })


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--reset", action="store_true",
                        help="delete every group a demo user is in before seeding")
    parser.add_argument("--delete-user", action="append", default=[], metavar="EMAIL",
                        help="also delete this account (e.g. user 1 from the last rehearsal)")
    args = parser.parse_args()

    env = {}
    for name in ("PB_URL", "PB_ADMIN_EMAIL", "PB_ADMIN_PASSWORD", "DEMO_PASSWORD"):
        env[name] = os.environ.get(name, "")
        if not env[name]:
            sys.exit(f"Set {name} first (see the top of this file).")
    if len(env["DEMO_PASSWORD"]) < 8:
        sys.exit("DEMO_PASSWORD must be at least 8 characters (the app requires it).")

    pb = PocketBase(env["PB_URL"])
    pb.login_superuser(env["PB_ADMIN_EMAIL"], env["PB_ADMIN_PASSWORD"])
    if args.reset or args.delete_user:
        reset(pb, args.delete_user)
    seed(pb, env["DEMO_PASSWORD"])
    print("\nDone. Log in on emulator 2 as sofie@example.com and on emulator 3 as "
          "mads@example.com, with DEMO_PASSWORD.")


if __name__ == "__main__":
    main()
