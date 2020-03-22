package me.joypri

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertEquals

object FirstName: Key<String>(String::class)

object JoyDataSpec : Spek({
    describe("Key") {
        it("has the right fully qualified name") {
            assertEquals("me.joypri.FirstName", FirstName.qualifiedName)
        }
        it("has its declared valueClass") {
            assertEquals(String::class, FirstName.valueClass)
        }
    }
})