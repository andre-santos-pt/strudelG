package pt.iscte.strudel.tests.javaparser

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import pt.iscte.strudel.javaparser.Java2Strudel
import pt.iscte.strudel.javaparser.StrudelUnsupportedException
import pt.iscte.strudel.model.impl.ArrayElementAssignment
import pt.iscte.strudel.model.impl.RecordFieldAssignment
import pt.iscte.strudel.vm.IVirtualMachine
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TestUnaryExpressionReplace {
  @Test
  fun test() {
    val src = """
            public class ArrayList {
                int[] array = {1,2,3};
                int next = 0;
                
                void add(int n) {
                  array[next++] = n;
                }
                
                void removeLast() {
                  array[--next] = 0;
                }
                
                void addNext(int n) {
                  array[next++] = ++n;
                }
            }
        """.trimIndent()
    val module = Java2Strudel().load(src)
    val body = module.get("add").block
    assertIs<ArrayElementAssignment>(body.children[0])
    assertIs<RecordFieldAssignment>(body.children[1])
  }

  @Test
  fun testIf() {
    val src = """
            public class ArrayList {
                int[] array = {1,2,3};
                int next = 0;
                
                void add(int n) {
                  if(array[next++] == 0)
                    array[next] = n;
                }
            }
        """.trimIndent()
    assertThrows<StrudelUnsupportedException> {
      Java2Strudel().load(src)
    }
  }


  @Test
  fun testUnsupport() {
    val src = """
            public class ArrayList {
                int[] array = {1,2,3};
                int next = 0;
                
                void addStrange() {
                  array[next++] = next;
                }
            }
        """.trimIndent()
    assertThrows<StrudelUnsupportedException> {
      Java2Strudel().load(src)
    }
  }
}