@file:Suppress("PublicApiImplicitType")

package me.joypri

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

enum class Job {
    CEO
}

object FirstName : Role<String>()
object MiddleName : Role<String>()
object Age : Role<Int>()

open class Person(vararg parts: Part) : Mix(*parts) {
    val firstName by FirstName
    val middleName by +MiddleName
    val age by Age
}

open class PersonR(vararg parts: Part) : Remix(*parts) {
    var firstName by +FirstName
    var age by +Age

    override fun toMix() = Person(*mixParts())
}

object TheirJob : Role<Job>()

object EmployeeNumber : Role<Int>()
object HireDate : Role<LocalDate>()

open class HrInfo(vararg parts: Part) : Mix(*parts) {
    val employeeNumber by EmployeeNumber
    val hireDate by HireDate
}

open class HrInfoR(vararg parts: Part) : Remix(*parts) {
    var employeeNumber by +EmployeeNumber
    var hireDate by +HireDate

    override fun toMix() = HrInfo(*mixParts())
}

typealias HrInfoRole = MixRole<HrInfo, HrInfoR>

object TheirHrInfo : HrInfoRole()

open class Employee(vararg parts: Part) : Person(*parts) {
    val job by TheirJob
    val hrInfo by TheirHrInfo
}

open class EmployeeR(vararg parts: Part) : PersonR(*parts) {
    var job by +TheirJob
    var hrInfo by !TheirHrInfo

    override fun toMix() = Employee(*mixParts())
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JoyDataTest {
    @Nested
    inner class RoleTest {
        @Test
        fun `has the right fully qualified name`() {
            assertEquals("me.joypri.FirstName", FirstName.qualifiedName)
        }
    }

    @Nested
    inner class MixTest {

        @Test
        fun `constructs and gets`() {
            val fred = Mix(FirstName to "Fred", Age to 12)
            assertEquals("Fred", fred[FirstName])
            assertNull(fred[MiddleName])
            assertEquals(12, fred[Age])
        }

        @Test
        fun `equals and hashCode`() {
            val fredMix1 = Mix(FirstName to "Fred", Age to 12)
            val fredMix2 = Mix(FirstName to "Fred", Age to 12)
            val fredPerson1 = Person(FirstName to "Fred", Age to 12)
            val fredPerson2 = Person(FirstName to "Fred", Age to 12)
            val georgeMix = Mix(FirstName to "George", Age to 12)
            val georgePerson = Person(FirstName to "George", Age to 12)

            assertEquals(fredMix1, fredMix2)
            assertEquals(fredMix1.hashCode(), fredMix2.hashCode())

            assertEquals(fredPerson1, fredPerson2)
            assertEquals(fredPerson1.hashCode(), fredPerson2.hashCode())

            assertNotEquals(fredMix1, georgeMix)
            assertNotEquals(fredMix1, fredPerson1)
            assertNotEquals(fredMix1 as Any?, null)

            assertNotEquals(fredPerson1, fredMix1)
            assertNotEquals(fredPerson1, georgePerson)
        }

        @Test
        fun `subclass can delegate properties to roles`() {
            val fred = Person(FirstName to "Fred", Age to 12)
            assertEquals("Fred", fred.firstName)
            assertNull(fred.middleName)
            assertEquals(12, fred.age)

            val jane = Person(FirstName to "Jane", MiddleName to "Ann", Age to 14)
            assertEquals("Jane", jane.firstName)
            assertEquals("Ann", jane.middleName)
            assertEquals(14, jane.age)
        }

        @Test
        fun `Constructing Mix subclass with missing delegated property throws IllegalArgumentException`() {
            assertThrows(IllegalArgumentException::class.java) {
                Person(FirstName to "Fred")
            }
        }

        @Test
        fun `Mix_with basics`() {
            val fred = Person(FirstName to "Fred", Age to 11).with(Age to 12)
            assertEquals("Fred", fred.firstName)
            assertNull(fred.middleName)
            assertEquals(12, fred.age)

            val fredJohn = fred.with(MiddleName to "John")
            assertEquals("Fred", fredJohn.firstName)
            assertEquals("John", fredJohn.middleName)
            assertEquals(12, fredJohn.age)
        }

        @Test
        fun `Mix_with preserves runtime class`() {
            val fredMix: Mix = Person(FirstName to "Fred", Age to 11)
            assertEquals(Person::class, fredMix::class)

            val fredMixWith = fredMix.with(Age to 12)
            assertEquals(Person::class, fredMixWith::class)
        }

        @Test
        fun `Mix_mapAt basics`() {
            val fred = Person(
                FirstName to "Fred", Age to 11
            ).mapAt(Age) { (it ?: 0) + 1 }
            assertEquals("Fred", fred.firstName)
            assertNull(fred.middleName)
            assertEquals(12, fred.age)

            val fredJohn = fred.mapAt(MiddleName) {
                require(it == null)
                "John"
            }
            assertEquals("Fred", fredJohn.firstName)
            assertEquals("John", fredJohn.middleName)
            assertEquals(12, fredJohn.age)

            val fred2 = fredJohn.mapAt(MiddleName) {
                require(it == "John")
                null
            }
            assertEquals("Fred", fred2.firstName)
            assertNull(fred2.middleName)
            assertEquals(12, fred2.age)
        }
    }

    @Nested
    inner class RemixTest {

        @Test
        fun `constructs, gets, and sets`() {
            val person = Remix(FirstName to "Fred")
            assertEquals("Fred", person[FirstName])
            assertNull(person[Age])
            person[Age] = 12
            assertEquals(12, person[Age])
        }

        @Test
        fun `equals and hashCode`() {
            val fredRemix1 = Remix(FirstName to "Fred", Age to 12)
            val fredRemix2 = Remix(FirstName to "Fred", Age to 12)
            val fredMix = Mix(FirstName to "Fred", Age to 12)

            val fredPerson = Person(FirstName to "Fred", Age to 12)
            val fredPersonR1 = PersonR(FirstName to "Fred", Age to 12)
            val fredPersonR2 = PersonR(FirstName to "Fred", Age to 12)

            val georgeRemix = Remix(FirstName to "George", Age to 12)
            val georgePersonR = PersonR(FirstName to "George", Age to 12)

            assertEquals(fredRemix1, fredRemix2)
            assertEquals(fredRemix1.hashCode(), fredRemix2.hashCode())

            assertEquals(fredPersonR1, fredPersonR2)
            assertEquals(fredPersonR1.hashCode(), fredPersonR2.hashCode())

            assertNotEquals(fredRemix1 as Any, fredMix as Any)
            assertNotEquals(fredRemix1 as Any, fredPerson as Any)
            assertNotEquals(fredRemix1 as Any?, null)

            assertNotEquals(fredMix as Any, fredRemix1 as Any)
            assertNotEquals(fredPerson as Any, fredRemix1 as Any)

            assertNotEquals(fredPersonR1, fredRemix1)
            assertNotEquals(fredPersonR1, georgePersonR)
            assertNotEquals(fredPersonR1 as Any, fredMix as Any)

            assertNotEquals(fredRemix1, fredPersonR1)
            assertNotEquals(fredMix as Any, fredPersonR1 as Any)
            assertNotEquals(fredRemix1, georgeRemix)
        }

        @Test
        fun `subclass can delegate properties to roles`() {
            val person = PersonR(FirstName to "Fred")
            assertEquals("Fred", person.firstName)
            assertNull(person.age)
            person.age = 12
            assertEquals(12, person.age)
        }

        @Test
        fun `subclass automatically constructs Remix roles`() {
            val employee = EmployeeR(FirstName to "Fred")
            assertNotNull(employee.hrInfo)
            assertEquals(HrInfoR(), employee.hrInfo)
        }

        @Test
        fun `DSL syntax`() {
            val fredHireDate = LocalDate.of(2020, 1, 20)
            val employee = EmployeeR().apply {
                firstName = "Fred"
                age = 12
                hrInfo.run {
                    employeeNumber = 789
                    hireDate = fredHireDate
                }
            }
            assertEquals("Fred", employee.firstName)
            assertEquals(12, employee.age)
            assertNull(employee.job)
            assertEquals(789, employee.hrInfo.employeeNumber)
            assertEquals(fredHireDate, employee.hrInfo.hireDate)
        }

        @Test
        fun `Build Mix from Remix`() {
            val fredHireDate = LocalDate.of(2020, 1, 20)
            val employeeR = EmployeeR().apply {
                firstName = "Fred"
                age = 12
                job = Job.CEO
                hrInfo.run {
                    employeeNumber = 789
                    hireDate = fredHireDate
                }
            }
            val employee = employeeR.toMix()
            assertEquals("Fred", employee.firstName)
            assertEquals(12, employee.age)
            assertEquals(Job.CEO, employee.job)
            assertEquals(789, employee.hrInfo.employeeNumber)
            assertEquals(fredHireDate, employee.hrInfo.hireDate)
        }

        @Test
        fun `Remix_with basics`() {
            val doe = PersonR(Age to 11)
            assertNull(doe.firstName)

            val fred = doe.with(FirstName to "Fred", Age to 12)
            assertEquals("Fred", fred.firstName)
            assertEquals(12, fred.age)
        }

        @Test
        fun `Remix_with preserves runtime class`() {
            val fredRemix: Remix = PersonR(FirstName to "Fred", Age to 11)
            assertEquals(PersonR::class, fredRemix::class)

            val fredRemixWith = fredRemix.with(Age to 12)
            assertEquals(PersonR::class, fredRemixWith::class)
        }

        @Test
        fun `Remix_mapAt basics`() {
            val fred = PersonR(
                FirstName to "Fred", Age to 11
            ).mapAt(Age) { (it ?: 0) + 1 }
            assertEquals("Fred", fred.firstName)
            assertEquals(12, fred.age)

            val doe = fred.mapAt(FirstName) {
                require(it == "Fred")
                null
            }

            val fred2 = doe.mapAt(FirstName) {
                require(it == null)
                "Fred"
            }
            assertEquals("Fred", fred2.firstName)
            assertEquals(12, fred2.age)
        }
    }
}