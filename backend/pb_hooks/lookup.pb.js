/// <reference path="../pb_data/types.d.ts" />

// GET /api/splendit/lookup?q=<phone or email>
//
// Finds one registered user by exact phone (with country code, e.g. +4512345678) or email, so
// a group admin can add them. The users collection itself is only readable by people who
// share a group, so this is the one way to find someone new, and it never lists anybody.
routerAdd("GET", "/api/splendit/lookup", (e) => {
  const q = (e.request.url.query().get("q") || "").trim()
  if (!q || q.length > 254) {
    throw new BadRequestError("Give a phone number or an email address as q.")
  }

  let user
  try {
    user = $app.findFirstRecordByFilter(
      "users",
      "phone = {:q} || email:lower = {:email}",
      { q: q, email: q.toLowerCase() },
    )
  } catch (err) {
    throw new NotFoundError("Nobody with that phone number or email.")
  }

  return e.json(200, {
    id: user.id,
    name: user.getString("name"),
    phone: user.getString("phone"),
  })
}, $apis.requireAuth())
