package com.medpull.kiosk.utils

import com.medpull.kiosk.data.models.FieldType
import com.medpull.kiosk.data.models.FormField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressParserTest {

    private fun field(
        id: String,
        type: FieldType = FieldType.TEXT,
        value: String? = null,
        options: List<String> = emptyList()
    ) = FormField(id = id, formId = "f", fieldName = id, fieldType = type, value = value, options = options)

    // ─── parse: comma form ────────────────────────────────────────────────────

    @Test
    fun `full comma address parses into all parts`() {
        val p = AddressParser.parse("123 main st, houston, tx 77001")!!
        assertEquals("123 Main St", p.street)
        assertEquals("Houston", p.city)
        assertEquals("TX", p.state)
        assertEquals("77001", p.zip)
    }

    @Test
    fun `two part comma address with full state name`() {
        val p = AddressParser.parse("123 Main St, Houston Texas 77001")!!
        assertEquals("123 Main St", p.street)
        assertEquals("Houston", p.city)
        assertEquals("TX", p.state)
        assertEquals("77001", p.zip)
    }

    @Test
    fun `street and city only is a partial parse`() {
        val p = AddressParser.parse("123 Main St, Houston")!!
        assertEquals("Houston", p.city)
        assertNull(p.state)
        assertNull(p.zip)
    }

    @Test
    fun `multi word city and state survive`() {
        val p = AddressParser.parse("456 Oak Ave, New York, New York 10001")!!
        assertEquals("New York", p.city)
        assertEquals("NY", p.state)
        assertEquals("10001", p.zip)
    }

    // ─── parse: spoken (comma-less) form ─────────────────────────────────────

    @Test
    fun `spoken address without commas parses`() {
        val p = AddressParser.parse("123 main street houston texas 77001")!!
        assertEquals("123 Main Street", p.street)
        assertEquals("Houston", p.city)
        assertEquals("TX", p.state)
        assertEquals("77001", p.zip)
    }

    @Test
    fun `spoken address with unit keeps unit in street`() {
        val p = AddressParser.parse("1420 seaside blvd apt 3b brownwood tx 76801")!!
        assertEquals("1420 Seaside Blvd Apt 3B", p.street)
        assertEquals("Brownwood", p.city)
        assertEquals("TX", p.state)
        assertEquals("76801", p.zip)
    }

    // ─── parse: non-addresses stay out ───────────────────────────────────────

    @Test
    fun `plain street answer is not a multi-part parse`() {
        assertNull(AddressParser.parse("123 Main St"))
    }

    @Test
    fun `conversational answer with comma is rejected`() {
        assertNull(AddressParser.parse("no, I don't have one"))
    }

    @Test
    fun `short answers are rejected`() {
        assertNull(AddressParser.parse("none"))
    }

    // ─── street-field detection ──────────────────────────────────────────────

    @Test
    fun `street fields across schema styles are detected`() {
        assertTrue(AddressParser.isStreetField(field("address_street")))
        assertTrue(AddressParser.isStreetField(field("mailing_address_street")))
        assertTrue(AddressParser.isStreetField(field("mailing_address")))
        assertTrue(AddressParser.isStreetField(field("parent1_mailing_address")))
    }

    @Test
    fun `non street fields are not detected`() {
        assertTrue(!AddressParser.isStreetField(field("email_address")))
        assertTrue(!AddressParser.isStreetField(field("address_city")))
        assertTrue(!AddressParser.isStreetField(field("mailing_zip")))
        assertTrue(!AddressParser.isStreetField(field("first_name")))
        assertTrue(!AddressParser.isStreetField(field("physical_same_as_mailing", FieldType.RADIO, options = listOf("Yes", "No"))))
    }

    // ─── related-field resolution ────────────────────────────────────────────

    @Test
    fun `sliding fee style siblings resolve`() {
        val street = field("address_street")
        val all = listOf(street, field("address_city"), field("address_state"), field("address_zip"))
        val r = AddressParser.findRelated(street, all)
        assertEquals("address_city", r.cityField?.id)
        assertEquals("address_state", r.stateField?.id)
        assertEquals("address_zip", r.zipField?.id)
        assertNull(r.combinedField)
    }

    @Test
    fun `coastal style prefixed siblings resolve`() {
        val street = field("mailing_address_street")
        val all = listOf(street, field("mailing_city"), field("mailing_state"), field("mailing_zip"))
        val r = AddressParser.findRelated(street, all)
        assertEquals("mailing_city", r.cityField?.id)
        assertEquals("mailing_state", r.stateField?.id)
        assertEquals("mailing_zip", r.zipField?.id)
    }

    @Test
    fun `patient registration bare siblings resolve`() {
        val street = field("mailing_address")
        val all = listOf(street, field("city"), field("state"), field("zip"))
        val r = AddressParser.findRelated(street, all)
        assertEquals("city", r.cityField?.id)
        assertEquals("state", r.stateField?.id)
        assertEquals("zip", r.zipField?.id)
    }

    @Test
    fun `combined city state zip sibling wins and suppresses singles`() {
        val street = field("parent1_mailing_address")
        val all = listOf(street, field("parent1_city_state_zip"), field("city", value = "Houston"))
        val r = AddressParser.findRelated(street, all)
        assertEquals("parent1_city_state_zip", r.combinedField?.id)
        assertNull(r.cityField)
        assertNull(r.zipField)
    }

    @Test
    fun `already filled siblings are not refilled`() {
        val street = field("address_street")
        val all = listOf(street, field("address_city", value = "Houston"), field("address_zip"))
        val r = AddressParser.findRelated(street, all)
        assertNull(r.cityField)
        assertEquals("address_zip", r.zipField?.id)
    }

    // ─── fills + display ─────────────────────────────────────────────────────

    @Test
    fun `fills map parsed parts onto sibling fields`() {
        val street = field("address_street")
        val all = listOf(street, field("address_city"), field("address_state"), field("address_zip"))
        val parsed = AddressParser.parse("123 Main St, Houston, TX 77001")!!
        val fills = AddressParser.fillsFor(parsed, AddressParser.findRelated(street, all))
        assertEquals(3, fills.size)
        assertEquals("Houston", fills.first { it.fieldId == "address_city" }.value)
        assertEquals("TX", fills.first { it.fieldId == "address_state" }.value)
        assertEquals("77001", fills.first { it.fieldId == "address_zip" }.value)
    }

    @Test
    fun `combined sibling gets one merged value`() {
        val street = field("parent1_mailing_address")
        val all = listOf(street, field("parent1_city_state_zip"))
        val parsed = AddressParser.parse("99 Pine Rd, Austin, TX 78701")!!
        val fills = AddressParser.fillsFor(parsed, AddressParser.findRelated(street, all))
        assertEquals(1, fills.size)
        assertEquals("Austin, TX 78701", fills[0].value)
    }

    @Test
    fun `display text is the full one-line address`() {
        val parsed = AddressParser.parse("123 main st, houston, tx 77001")!!
        assertEquals("123 Main St, Houston, TX 77001", AddressParser.displayText(parsed))
        assertNotNull(parsed)
    }
}
