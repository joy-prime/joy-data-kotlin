package me.joypri

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

object FirstName: Key<String>()
object Age: Key<Int>()

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JoyDataTest {

    @Test
    fun `Key has the right fully qualified name`() {
        assertThat(FirstName.qualifiedName).isEqualTo("me.joypri.FirstName")
    }

    @Test
    fun `JoyData constructs and gets`() {
        val data = JoyData(FirstName to "Fred", Age to 12)
        assertThat(data[FirstName]).isEqualTo("Fred")
        assertThat(data[Age]).isEqualTo(12)
    }
}