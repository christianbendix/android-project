package com.example.splendit.data

import com.example.splendit.data.model.Group
import com.example.splendit.data.model.Jackpot
import com.example.splendit.data.model.ME
import com.example.splendit.data.model.Message
import com.example.splendit.data.model.PayMethod
import com.example.splendit.data.model.Person

/** Dummy data mirroring the Splitwheel v3 prototype. Users are mocked, as the brief allows. */
object SeedData {
    val people: Map<String, Person> = listOf(
        Person(ME, "Peter Hansen", "You", "PH", "+45 31 20 77 19"),
        Person("mads", "Mads Holm", "Mads", "MH", "+45 22 41 08 19"),
        Person("sofie", "Sofie Lund", "Sofie", "SL", "+45 28 90 13 44"),
        Person("emil", "Emil Berg", "Emil", "EB", "+45 40 12 55 07"),
        Person("freja", "Freja Dahl", "Freja", "FD", "+45 61 33 72 90"),
        Person("oliver", "Oliver Krogh", "Oliver", "OK", "+45 52 18 46 31"),
        Person("ida", "Ida Nørgaard", "Ida", "IN", "+45 27 64 09 58"),
        Person("lucas", "Lucas Bach", "Lucas", "LB", "+45 42 77 30 16"),
        Person("clara", "Clara Juul", "Clara", "CJ", "+45 30 59 81 22")
    ).associateBy { it.id }

    val friends = listOf("mads", "sofie", "emil", "freja", "oliver", "ida", "lucas", "clara")

    val groups: List<Group> = listOf(
        Group(
            id = "g1",
            name = "Roskilde 2026",
            initials = "R6",
            members = listOf(ME, "mads", "sofie", "emil"),
            createdBy = "mads",
            updated = 4,
            whenLabel = "14:02",
            unread = 2,
            jackpot = Jackpot(
                goal = 500.0,
                pool = 482.0,
                ratings = mapOf(ME to 3.5, "mads" to 4.5, "sofie" to 5.0, "emil" to 2.5)
            ),
            messages = listOf(
                Message.System(1, "Mads created the group · Jackpot pool on"),
                Message.Text(2, "sofie", "Tent is packed. Who is on groceries?"),
                Message.Text(3, "mads", "I will grab them — the wheel decides who pays."),
                Message.Expense(
                    id = 4,
                    from = "mads",
                    title = "Groceries — Netto",
                    total = 840.0,
                    payer = "mads",
                    method = PayMethod.WHEEL,
                    participants = listOf(ME, "mads", "sofie", "emil"),
                    paid = setOf("sofie"),
                    timestamp = 100
                ),
                Message.System(
                    5,
                    "Sofie sent 210 kr to Mads · 21 kr to the jackpot · first to pay ★"
                ),
                Message.Text(6, "emil", "Rigged wheel. Paying later.")
            )
        ),
        Group(
            id = "g2",
            name = "Kitchen K4",
            initials = "K4",
            members = listOf(ME, "oliver", "ida", "lucas"),
            createdBy = ME,
            updated = 3,
            whenLabel = "Yesterday",
            messages = listOf(
                Message.System(1, "You created the group"),
                Message.Expense(
                    id = 2,
                    from = ME,
                    title = "Cleaning supplies",
                    total = 320.0,
                    payer = ME,
                    method = PayMethod.CARD,
                    participants = listOf(ME, "oliver", "ida", "lucas"),
                    paid = setOf("oliver"),
                    timestamp = 90
                ),
                Message.Text(3, "ida", "Sending tonight!")
            )
        ),
        Group(
            id = "g3",
            name = "Lisbon in May",
            initials = "LM",
            members = listOf(ME, "sofie", "clara", "emil"),
            createdBy = ME,
            updated = 2,
            whenLabel = "Mon",
            unread = 1,
            jackpot = Jackpot(
                goal = 1000.0,
                pool = 340.0,
                ratings = mapOf(ME to 4.0, "sofie" to 4.5, "clara" to 3.5, "emil" to 3.0)
            ),
            messages = listOf(
                Message.System(1, "You created the group · Jackpot pool on"),
                Message.Expense(
                    id = 2,
                    from = ME,
                    title = "Apartment deposit",
                    total = 2400.0,
                    payer = ME,
                    method = PayMethod.EVEN,
                    participants = listOf(ME, "sofie", "clara", "emil"),
                    paid = setOf("emil"),
                    timestamp = 80
                ),
                Message.Image(3, "clara", null),
                Message.Text(4, "clara", "This is the view from the flat")
            )
        ),
        Group(
            id = "g4",
            name = "Friday padel",
            initials = "FP",
            members = listOf(ME, "mads", "oliver", "lucas"),
            createdBy = "oliver",
            updated = 1,
            whenLabel = "28 Oct",
            messages = listOf(
                Message.System(1, "Oliver created the group"),
                Message.Expense(
                    id = 2,
                    from = "oliver",
                    title = "Court booking",
                    total = 360.0,
                    payer = "oliver",
                    method = PayMethod.WHEEL,
                    participants = listOf(ME, "mads", "oliver", "lucas"),
                    paid = setOf(ME, "mads", "lucas"),
                    timestamp = 70
                ),
                Message.Text(3, "mads", "Same time next week?")
            )
        )
    )
}
