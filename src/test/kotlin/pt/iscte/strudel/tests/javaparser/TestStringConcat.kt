package pt.iscte.strudel.tests.javaparser

import org.junit.jupiter.api.Test
import pt.iscte.strudel.javaparser.Java2Strudel
import pt.iscte.strudel.javaparser.extensions.string
import pt.iscte.strudel.vm.IVirtualMachine
import kotlin.math.sqrt
import kotlin.test.assertEquals

class TestStringConcat {

    @Test
    fun testStringString() {
        val src = """
            public class StringConcat {
                public static String concatStringString(String x, String y) {
                    return x + y;
                }
            }
        """.trimIndent()

        val module = Java2Strudel().load(src)
        val vm = IVirtualMachine.create()
        val concat = module.getProcedure("concatStringString")

        val result = vm.execute(concat, string("hello "), string("world"))
        assertEquals("hello world", result?.value)
    }

    @Test
    fun testStringPrimitive() {
        val src = """
            public class StringConcat {
                public static String concatStringPrimitive(String x, int y) {
                    return x + y;
                }
            }
        """.trimIndent()

        val module = Java2Strudel().load(src)
        val vm = IVirtualMachine.create()
        val concat = module.getProcedure("concatStringPrimitive")

        val result = vm.execute(concat, string("hello "), vm.getValue(2))
        assertEquals("hello 2", result?.value)
    }

    @Test
    fun testStringAny() {
        val src = """
            import java.lang.Math; 
            
            public class StringConcat {
                public static String concatLeft(String x) {
                    return x + Math.sqrt(2.0);
                }
            }
        """.trimIndent()

        val module = Java2Strudel().load(src)
        val vm = IVirtualMachine.create()
        val concat = module.getProcedure("concatLeft")

        val result = vm.execute(concat, string("hello "))
        assertEquals("hello ${sqrt(2.0)}", result?.value)
    }

    @Test
    fun testAnyString() {
        val src = """
            import java.lang.Math; 
            
            public class StringConcat {
                public static String concatRight(String x) {
                    return Math.sqrt(2.0) + x;
                }
            }
        """.trimIndent()

        val module = Java2Strudel().load(src)
        val vm = IVirtualMachine.create()
        val concat = module.getProcedure("concatRight")

        val result = vm.execute(concat, string(" hello"))
        assertEquals("${sqrt(2.0)} hello", result?.value)
    }
}