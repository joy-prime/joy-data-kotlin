@file:Suppress("PublicApiImplicitType")

package me.joypri

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import kotlin.test.*

enum class Job {
    IC,
    MANAGER,
}

object FirstName : StringRole()
object MiddleName : StringRole()
object Age : IntRole()

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

object TheirJob : ClassRole<Job>(Job::class)

object EmployeeNumber : IntRole()
object HireDate : ClassRole<LocalDate>(LocalDate::class)

open class HrInfo(vararg parts: Part) : Mix(*parts) {
    val employeeNumber by EmployeeNumber
    val hireDate by HireDate
}

open class HrInfoR(vararg parts: Part) : Remix(*parts) {
    var employeeNumber by +EmployeeNumber
    var hireDate by +HireDate

    override fun toMix() = HrInfo(*mixParts())
}

object TheirHrInfo : MixRole<HrInfo, HrInfoR>(HrInfo::class, HrInfoR::class)

open class Employee(vararg parts: Part) : Person(*parts) {
    val job by TheirJob
    val hrInfo by TheirHrInfo
}

open class EmployeeR(vararg parts: Part) : PersonR(*parts) {
    var job by +TheirJob
    var hrInfo by !TheirHrInfo

    override fun toMix() = Employee(*mixParts())
}

object Reports : ListRole<Employee>(Employee::class)

open class Manager(vararg parts: Part) : Employee(*parts) {
    val reports by Reports
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JoyDataTest {
    private val fredHireDate: LocalDate = LocalDate.of(1990, 1, 25)
    private val fredEmployeeNumber = 10000
    private val fredAge = 35
    private val fred = Employee(
        FirstName of "Fred",
        Age of fredAge,
        TheirJob of Job.IC,
        TheirHrInfo of HrInfo(
            EmployeeNumber of fredEmployeeNumber,
            HireDate of fredHireDate
        )
    )

    private val sallyEmployeeNumber = 20000
    private val sallyHireDate: LocalDate = LocalDate.of(1995, 2, 20)
    private val sallyAge = 37
    private val sally = Manager(
        FirstName of "Sally",
        Age of sallyAge,
        TheirJob of Job.MANAGER,
        TheirHrInfo of HrInfo(
            EmployeeNumber of sallyEmployeeNumber,
            HireDate of sallyHireDate
        ),
        Reports of listOf(fred)
    )

    @Nested
    inner class RoleTest {
        @Test
        fun `has the right fully qualified name`() {
            assertEquals("me.joypri.FirstName", FirstName.qualifiedName)
        }
    }

    @Nested
    inner class RolePathTest {
        @Test
        fun plus() {
            val employeeHere = RolePath.empty<Employee>()
            val employeeListHere = RolePath.empty<List<Employee>>()
            val employeeSub1 = RolePath(employeeListHere, 1)

            // path to scalar concatenated with anything
            assertFailsWith(IllegalArgumentException::class) {
                Age.toPath() + Age.toPath()
            }
            // path to List<*> concatenated with path that starts with AtRole
            assertFailsWith(IllegalArgumentException::class) {
                Reports.toPath() + Age.toPath()
            }
            // path to MixParts concatenated with path that starts with AtIndex
            assertFailsWith(IllegalArgumentException::class) {
                TheirHrInfo.toPath() + employeeSub1
            }

            // LHS is empty
            assertEquals(Age.toPath(), employeeHere + Age)
            assertEquals(employeeSub1, employeeListHere + employeeSub1)

            // RHS is empty
            assertEquals(Reports.toPath(), Reports + employeeListHere)
            assertEquals(Reports[1], Reports[1] + employeeHere)

            // path to MixParts + path starting with AtRole
            assertEquals(Reports[1] + Age, Reports[1] + Age.toPath())

            // path to List<*> + path starting with AtIndex
            assertEquals(Reports[1], Reports + employeeSub1)
        }
    }

    @Nested
    inner class MixTest {

        @Test
        fun `constructs and gets`() {
            val fred = Mix(FirstName of "Fred", Age of 12)
            assertEquals("Fred", fred[FirstName])
            assertNull(fred[MiddleName])
            assertEquals(12, fred[Age])
        }

        @Test
        fun `equals and hashCode`() {
            val fredMix1 = Mix(FirstName of "Fred", Age of 12)
            val fredMix2 = Mix(FirstName of "Fred", Age of 12)
            val fredPerson1 = Person(FirstName of "Fred", Age of 12)
            val fredPerson2 = Person(FirstName of "Fred", Age of 12)
            val georgeMix = Mix(FirstName of "George", Age of 12)
            val georgePerson = Person(FirstName of "George", Age of 12)

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
            val fred = Person(FirstName of "Fred", Age of 12)
            assertEquals("Fred", fred.firstName)
            assertNull(fred.middleName)
            assertEquals(12, fred.age)

            val jane = Person(FirstName of "Jane", MiddleName of "Ann", Age of 14)
            assertEquals("Jane", jane.firstName)
            assertEquals("Ann", jane.middleName)
            assertEquals(14, jane.age)
        }

        @Test
        fun `Constructing Mix subclass with missing delegated property throws IllegalArgumentException`() {
            assertThrows(IllegalArgumentException::class.java) {
                Person(FirstName of "Fred")
            }
        }

        @Test
        fun `Mix_with basics`() {
            val fred = Person(FirstName of "Fred", Age of 11).with(Age of 12)
            assertEquals("Fred", fred.firstName)
            assertNull(fred.middleName)
            assertEquals(12, fred.age)

            val fredJohn = fred.with(MiddleName of "John")
            assertEquals("Fred", fredJohn.firstName)
            assertEquals("John", fredJohn.middleName)
            assertEquals(12, fredJohn.age)
        }

        @Test
        fun `Mix_with preserves runtime class`() {
            val fredMix: Mix = Person(FirstName of "Fred", Age of 11)
            assertEquals(Person::class, fredMix::class)

            val fredMixWith = fredMix.with(Age of 12)
            assertEquals(Person::class, fredMixWith::class)
        }

        @Test
        fun `Mix_mapAt Role`() {
            val fred = Person(
                FirstName of "Fred", Age of 11
            ).mapAt(Age) { age: Int -> age + 1 }
            assertEquals("Fred", fred.firstName)
            assertNull(fred.middleName)
            assertEquals(12, fred.age)
        }

        @Test
        fun `Mix _mapAt _with _get RolePath`() {
            assertEquals(fred as Employee?, fred[RolePath.empty()])
            assertEquals(fredAge, fred[Age.toPath()])
            assertEquals(fredEmployeeNumber, fred[TheirHrInfo + EmployeeNumber])

            val fred2 = fred.mapAt(TheirHrInfo + EmployeeNumber) { it + 1 }
            assertEquals("Fred", fred2.firstName)
            assertNull(fred2.middleName)
            assertEquals(fredHireDate, fred2.hrInfo.hireDate)
            assertEquals(fredEmployeeNumber + 1, fred2.hrInfo.employeeNumber)

            val fred3 = fred.with(
                TheirHrInfo + EmployeeNumber,
                fredEmployeeNumber + 1
            )
            assertEquals(fred2, fred3)

            val johnAge = 40
            val john = fred.with(FirstName of "John", Age of johnAge)
            assertEquals(johnAge, john.age)
            assertEquals("John", john.firstName)
            assertNull(john.middleName)
            assertEquals(fredHireDate, john.hrInfo.hireDate)

            val sallyWithFred2AndJohn = sally.mapAt(Reports) { listOf(fred2, john) }
            assertEquals(fred2, sallyWithFred2AndJohn.reports[0])

            val sallyLater = sallyWithFred2AndJohn.mapAt(Reports[1] + Age) { it + 1 }
            assertEquals(fred2, sallyLater.reports[0])
            assertEquals("John", sallyLater.reports[1].firstName)
            assertEquals(johnAge + 1, sallyLater.reports[1].age)

            // val invalidPath = EmployeeNumber + TheirHrInfo
            // val invalidPath = TheirHrInfo[0]
            assertNull(fred[EmployeeNumber.toPath()])
            assertFailsWith(IllegalArgumentException::class) {
                fred.mapAt(EmployeeNumber.toPath()) { it + 1 }
            }
            val starProjectedIntPath = Age.toPath() as RolePath<*>

            @Suppress("UNCHECKED_CAST")
            val pretendStringPath = starProjectedIntPath as RolePath<String>
            assertFailsWith(ClassCastException::class) {
                fred.mapAt(pretendStringPath) { "not an Int" }
            }

            assertFailsWith(IllegalArgumentException::class) {
                sally[Reports[1] + Age]
            }
            assertFailsWith(IllegalArgumentException::class) {
                sally.mapAt(Reports[1] + Age) { it + 1 }
            }
        }
        @Test
        fun `Mix_roleDeclarations on this class`() {
            val expectedPersonRoles = listOf(
                RoleDeclaration(Age, false),
                RoleDeclaration(FirstName, false),
                RoleDeclaration(MiddleName, true),
            )
            assertEquals(expectedPersonRoles, roleDeclarations(Person::class))
        }

        @Test
        fun `Mix_roleDeclarations include superclass`() {
            val expectedManagerRoles = listOf(
                RoleDeclaration(Age, false),
                RoleDeclaration(FirstName, false),
                RoleDeclaration(MiddleName, true),
                RoleDeclaration(Reports, false),
                RoleDeclaration(TheirHrInfo, false),
                RoleDeclaration(TheirJob, false)
            )
            assertEquals(expectedManagerRoles, roleDeclarations(Manager::class))
        }
    }

    @Nested
    inner class RemixTest {

        @Test
        fun `constructs, gets, and sets`() {
            val person = Remix(FirstName of "Fred")
            assertEquals("Fred", person[FirstName])
            assertNull(person[Age])
            person[Age] = 12
            assertEquals(12, person[Age])
        }

        @Test
        fun `equals and hashCode`() {
            val fredRemix1 = Remix(FirstName of "Fred", Age of 12)
            val fredRemix2 = Remix(FirstName of "Fred", Age of 12)
            val fredMix = Mix(FirstName of "Fred", Age of 12)

            val fredPerson = Person(FirstName of "Fred", Age of 12)
            val fredPersonR1 = PersonR(FirstName of "Fred", Age of 12)
            val fredPersonR2 = PersonR(FirstName of "Fred", Age of 12)

            val georgeRemix = Remix(FirstName of "George", Age of 12)
            val georgePersonR = PersonR(FirstName of "George", Age of 12)

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
            val person = PersonR(FirstName of "Fred")
            assertEquals("Fred", person.firstName)
            assertNull(person.age)
            person.age = 12
            assertEquals(12, person.age)
        }

        @Test
        fun `subclass automatically constructs Remix roles`() {
            val employee = EmployeeR(FirstName of "Fred")
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
                    // firstName = "forbidden by DSL rules" // should not compile
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
            val fredEmployeeNumber = 789
            val fredR = EmployeeR().apply {
                firstName = "Fred"
                age = 12
                job = Job.IC
                hrInfo.run {
                    employeeNumber = fredEmployeeNumber
                    hireDate = fredHireDate
                }
            }
            val fred = fredR.toMix()
            assertEquals("Fred", fred.firstName)
            assertEquals(12, fred.age)
            assertEquals(Job.IC, fred.job)
            assertEquals(fredEmployeeNumber, fred.hrInfo.employeeNumber)
            assertEquals(fredHireDate, fred.hrInfo.hireDate)
        }

        @Test
        fun `Remix_with basics`() {
            val doe = PersonR(Age of 11)
            assertNull(doe.firstName)

            val fred = doe.with(FirstName of "Fred", Age of 12)
            assertEquals("Fred", fred.firstName)
            assertEquals(12, fred.age)
        }

        @Test
        fun `Remix_with preserves runtime class`() {
            val fredRemix: Remix = PersonR(FirstName of "Fred", Age of 11)
            assertEquals(PersonR::class, fredRemix::class)

            val fredRemixWith = fredRemix.with(Age of 12)
            assertEquals(PersonR::class, fredRemixWith::class)
        }

        @Test
        fun `Remix_mapAt Role`() {
            val fred = PersonR(
                FirstName of "Fred", Age of 11
            ).mapAt(Age) { age: Int -> age + 1 }
            assertEquals("Fred", fred.firstName)
            assertEquals(12, fred.age)

            val jack = fred.mapAt(FirstName) {
                "Jack"
            }
            assertEquals("Jack", jack.firstName)
        }

        @Test
        fun `Remix_mapAt RolePath`() {
            val fredHireDate = LocalDate.of(2020, 1, 20)
            val fredEmployeeNumber = 789
            val fredR = EmployeeR().apply {
                firstName = "Fred"
                age = 12
                job = Job.IC
                hrInfo.run {
                    employeeNumber = fredEmployeeNumber
                    hireDate = fredHireDate
                }
            }
            val fred = fredR.toMix()
            assertEquals("Fred", fred.firstName)
            assertEquals(12, fred.age)
            assertEquals(Job.IC, fred.job)
            assertEquals(fredEmployeeNumber, fred.hrInfo.employeeNumber)
            assertEquals(fredHireDate, fred.hrInfo.hireDate)

            val fred2 = fred.mapAt(TheirHrInfo + EmployeeNumber) { it + 1 }
            assertEquals("Fred", fred2.firstName)
            assertEquals(fredHireDate, fred2.hrInfo.hireDate)
            assertEquals(fredEmployeeNumber + 1, fred2.hrInfo.employeeNumber)

            val olderFred = fred.with(Age of 40)
            assertEquals(40, olderFred.age)
            assertEquals("Fred", olderFred.firstName)
            assertEquals(fredHireDate, olderFred.hrInfo.hireDate)

            val jack = fred.mapAt(FirstName) { name: String ->
                require(name == "Fred")
                "Jack"
            }
            assertEquals("Jack", jack.firstName)
            assertTrue(jack.hrInfo === fred.hrInfo)
        }
    }
}