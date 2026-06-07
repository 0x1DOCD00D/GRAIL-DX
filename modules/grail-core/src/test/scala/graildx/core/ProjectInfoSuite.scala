package graildx.core

import cats.effect.unsafe.implicits.global
import munit.FunSuite

class ProjectInfoSuite extends FunSuite:
  test("project info uses cats effect and pure logging") {
    assertEquals(ProjectInfo.message.unsafeRunSync(), "grail-dx")
  }
