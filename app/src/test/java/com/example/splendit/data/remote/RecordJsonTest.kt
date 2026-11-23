package com.example.splendit.data.remote

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordJsonTest {
    @Test
    fun parsesAnExpenseWithCustomShares() {
        val json = JSONObject(
            """{"collectionName":"expenses","id":"e1","group":"g1","creator":"u1",
            "title":"Pizza","amount_ore":45000,"payer":"u1","method":"even",
            "date":"2026-11-20 18:30:00.000Z","participants":["u1","u2","u3"],
            "shares":{"u1":15000,"u2":20000,"u3":10000},"created":"2026-11-20 18:31:02.500Z"}"""
        )

        val e = RecordJson.record(json) as ExpenseRecord

        assertEquals(45_000L, e.amountOre)
        assertEquals(listOf("u1", "u2", "u3"), e.participants)
        assertEquals(20_000L, e.shares!!.getValue("u2"))
        assertEquals(1_795_199_462_500L, e.created)
    }

    @Test
    fun evenSplitHasNoShares() {
        val json = JSONObject(
            """{"collectionName":"expenses","id":"e1","amount_ore":100,"shares":null,
            "participants":[]}"""
        )
        assertNull((RecordJson.record(json) as ExpenseRecord).shares)
    }

    @Test
    fun bodyRoundTripsThroughTheParser() {
        val payment = PaymentRecord("p1", "g1", "e1", "u2", "u1", 15_000, created = 0)
        val body = RecordJson.body(payment).put("collectionName", PAYMENTS)

        assertEquals(payment, RecordJson.record(body))
        assertTrue("server sets created", !RecordJson.body(payment).has("created"))
    }

    @Test
    fun unknownCollectionIsIgnored() {
        assertNull(RecordJson.record(JSONObject("""{"collectionName":"users","id":"u1"}""")))
    }

    @Test
    fun newIdsLookLikePocketBaseIds() {
        val ids = (1..100).map { RecordJson.newId() }
        assertTrue(ids.all { it.matches(Regex("^[a-z0-9]{15}$")) })
        assertEquals(100, ids.toSet().size)
    }
}
