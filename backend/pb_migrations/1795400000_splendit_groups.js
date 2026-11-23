/// <reference path="../pb_data/types.d.ts" />

// Splendit schema (PocketBase v0.23+ migration API).
//
// - users get a unique phone, stored with the country code (+4512345678).
// - groups, expenses, payments and messages hold the shared data. Amounts are whole øre.
//
// Access rules: only members can read a group and everything in it, only the admin can edit
// or delete the group, members only post as themselves, and a payment can only be made from
// yourself to the person who paid the bill.
migrate((app) => {
  const users = app.findCollectionByNameOrId("users")
  users.fields.add(new TextField({
    name: "phone",
    pattern: "^\\+[1-9][0-9]{6,14}$",
  }))
  users.addIndex("idx_users_phone", true, "phone", "phone != ''")
  // You can see yourself and the people you share a group with. Finding someone new goes
  // through /api/splendit/lookup (pb_hooks/lookup.pb.js), which needs an exact phone or email.
  const sharesAGroup =
    "id = @request.auth.id || " +
    "(@collection.groups.members.id ?= id && @collection.groups.members.id ?= @request.auth.id)"
  users.listRule = sharesAGroup
  users.viewRule = sharesAGroup
  app.save(users)

  const autodates = [
    { type: "autodate", name: "created", onCreate: true, onUpdate: false },
    { type: "autodate", name: "updated", onCreate: true, onUpdate: true },
  ]

  const groups = new Collection({
    type: "base",
    name: "groups",
    listRule: "members.id ?= @request.auth.id",
    viewRule: "members.id ?= @request.auth.id",
    createRule:
      "@request.auth.id != '' && admin = @request.auth.id && members.id ?= @request.auth.id",
    // The admin can't hand the group to someone else or leave it without an admin.
    updateRule:
      "admin = @request.auth.id && " +
      "(@request.body.admin:isset = false || @request.body.admin = @request.auth.id)",
    deleteRule: "admin = @request.auth.id",
    fields: [
      { type: "text", name: "name", required: true, max: 100 },
      { type: "text", name: "description", max: 500 },
      { type: "relation", name: "admin", required: true, maxSelect: 1, collectionId: users.id },
      { type: "relation", name: "members", required: true, maxSelect: 100, collectionId: users.id },
      ...autodates,
    ],
  })
  app.save(groups)

  const member = "group.members.id ?= @request.auth.id"

  const expenses = new Collection({
    type: "base",
    name: "expenses",
    listRule: member,
    viewRule: member,
    createRule: `creator = @request.auth.id && ${member}`,
    // Editing an expense (issue #24) is not built yet.
    updateRule: null,
    deleteRule: "creator = @request.auth.id",
    fields: [
      {
        type: "relation", name: "group", required: true, maxSelect: 1,
        collectionId: groups.id, cascadeDelete: true,
      },
      { type: "relation", name: "creator", required: true, maxSelect: 1, collectionId: users.id },
      { type: "text", name: "title", required: true, max: 200 },
      { type: "number", name: "amount_ore", required: true, onlyInt: true, min: 1 },
      { type: "relation", name: "payer", required: true, maxSelect: 1, collectionId: users.id },
      {
        type: "select", name: "method", required: true, maxSelect: 1,
        values: ["even", "wheel", "card"],
      },
      { type: "date", name: "date" },
      {
        type: "relation", name: "participants", required: true, maxSelect: 100,
        collectionId: users.id,
      },
      // Øre per participant, e.g. {"<user id>": 15000}. Empty means an equal split.
      { type: "json", name: "shares", maxSize: 20000 },
      ...autodates,
    ],
  })
  app.save(expenses)

  const payments = new Collection({
    type: "base",
    name: "payments",
    listRule: member,
    viewRule: member,
    createRule:
      `from = @request.auth.id && ${member} && expense.group = group && to = expense.payer`,
    updateRule: null,
    deleteRule: null,
    fields: [
      {
        type: "relation", name: "group", required: true, maxSelect: 1,
        collectionId: groups.id, cascadeDelete: true,
      },
      {
        type: "relation", name: "expense", required: true, maxSelect: 1,
        collectionId: expenses.id, cascadeDelete: true,
      },
      { type: "relation", name: "from", required: true, maxSelect: 1, collectionId: users.id },
      { type: "relation", name: "to", required: true, maxSelect: 1, collectionId: users.id },
      { type: "number", name: "amount_ore", required: true, onlyInt: true, min: 1 },
      ...autodates,
    ],
    // Each member settles a given expense once.
    indexes: [
      "CREATE UNIQUE INDEX `idx_payments_expense_from` ON `payments` (`expense`, `from`)",
    ],
  })
  app.save(payments)

  const messages = new Collection({
    type: "base",
    name: "messages",
    listRule: member,
    viewRule: member,
    createRule: `from = @request.auth.id && ${member}`,
    updateRule: null,
    deleteRule: "from = @request.auth.id",
    fields: [
      {
        type: "relation", name: "group", required: true, maxSelect: 1,
        collectionId: groups.id, cascadeDelete: true,
      },
      { type: "relation", name: "from", required: true, maxSelect: 1, collectionId: users.id },
      { type: "text", name: "text", required: true, max: 2000 },
      ...autodates,
    ],
  })
  app.save(messages)
}, (app) => {
  for (const name of ["messages", "payments", "expenses", "groups"]) {
    app.delete(app.findCollectionByNameOrId(name))
  }
  const users = app.findCollectionByNameOrId("users")
  users.removeIndex("idx_users_phone")
  users.fields.removeByName("phone")
  // PocketBase's defaults for the users collection.
  users.listRule = "id = @request.auth.id"
  users.viewRule = "id = @request.auth.id"
  app.save(users)
})
