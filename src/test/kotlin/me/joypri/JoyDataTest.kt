package me.joypri

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import kotlin.test.assertEquals
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
    }
}