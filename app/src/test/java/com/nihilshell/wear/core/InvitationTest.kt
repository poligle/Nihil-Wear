package com.nihilshell.wear.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InvitationTest {
    private val pin = "8-eMVPli0fpzjeUBe-b_bmRtjDMwo1bNYXaVTyPSbVc"
    private val qr = "nihil-deck://pair?v=1&name=nihil&hosts=192.168.1.20,100.64.0.7&port=64445&code=7JTX39F4&pin=$pin"

    @Test
    fun readsTheComputersQr() {
        val invitation = Invitation.parse(qr)
        assertNotNull(invitation)
        invitation!!
        assertEquals("nihil", invitation.name)
        assertEquals(listOf("192.168.1.20", "100.64.0.7"), invitation.hosts)
        assertEquals(64445, invitation.port)
        assertEquals("7JTX39F4", invitation.code)
        assertEquals(pin, invitation.pin)
    }

    @Test
    fun readsWhatTheDeckEncodes() {
        // Python's urlencode, as deck.py writes it: a space as "+", the commas left alone.
        val invitation = Invitation.parse("  nihil-deck://pair?v=1&name=my+laptop&hosts=10.0.0.2&port=64445&code=ABCDEFGH&pin=$pin\n")
        assertEquals("my laptop", invitation?.name)
    }

    @Test
    fun nameFallsBackToTheFirstAddress() {
        assertEquals("10.0.0.2", Invitation.parse("nihil-deck://pair?v=1&hosts=10.0.0.2&port=1&code=ABCDEFGH&pin=$pin")?.name)
    }

    @Test
    fun refusesWhatIsntAnInvitation() {
        assertNull(Invitation.parse("https://nihilshell.com"))
        assertNull(Invitation.parse(qr.replace("v=1", "v=2")))
        assertNull(Invitation.parse(qr.replace("code=7JTX39F4", "code=7JTX39F0")))
        assertNull(Invitation.parse(qr.replace("code=7JTX39F4", "code=7JTX39")))
        assertNull(Invitation.parse(qr.replace(pin, pin.dropLast(1))))
        assertNull(Invitation.parse(qr.replace("port=64445", "port=70000")))
        assertNull(Invitation.parse(qr.replace("hosts=192.168.1.20,100.64.0.7", "hosts=")))
        assertNull(Invitation.parse(qr.replace("192.168.1.20", "nihil.local")))
        assertNull(Invitation.parse(qr.replace("192.168.1.20", "192.168.1.256")))
    }

    @Test
    fun readsAddressesWithoutLookingThemUp() {
        assertArrayEquals(byteArrayOf(192.toByte(), 168.toByte(), 1, 20), Ipv4.parse("192.168.1.20"))
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), Ipv4.parse("0.0.0.0"))
        assertNull(Ipv4.parse("1.2.3"))
        assertNull(Ipv4.parse("1.2.3.4.5"))
        assertNull(Ipv4.parse("1.2.3.x"))
        assertNull(Ipv4.parse("1.2.3.1000"))
        assertNull(Ipv4.parse("1..3.4"))
    }
}
