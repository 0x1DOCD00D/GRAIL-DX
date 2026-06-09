package io.github.`0x1docd00d`
package grail.static

import java.nio.file.{Files, Path}
import munit.FunSuite

import grail.core.*

/**
 * Unit tests for [[StaticAnalyzer.analyzeFile]].
 *
 * Each test creates a temporary `.scala` file, writes a controlled source
 * snippet into it, invokes the analyzer, and asserts on the returned
 * [[EvidenceGraphNode]] list.  Temporary files are always removed in a
 * `finally` block so failures do not leave state on disk.
 *
 * Test groups:
 *   1. Error paths  – non-existent file, unparseable content
 *   2. SourceFile anchor – count, id, label, properties
 *   3. Class and object detection
 *   4. Method vs. Function discrimination
 *   5. Mixed-file scenarios
 *   6. Node metadata – properties, embeddingText, platform
 *   7. Node id format
 */
class StaticAnalyzerSuite extends FunSuite:

  // ── fixture ────────────────────────────────────────────────────────────────

  /** Writes `content` to a fresh temp file and passes the [[Path]] to `f`. */
  private def withTempScala(content: String)(f: Path => Unit): Unit =
    val path = Files.createTempFile("grail-test-", ".scala")
    try
      Files.writeString(path, content)
      f(path)
    finally
      Files.deleteIfExists(path)

  // ── 1. Error paths ─────────────────────────────────────────────────────────

  test("non-existent file path returns Left") {
    val result = StaticAnalyzer.analyzeFile(Path.of("/nonexistent/grail/Missing.scala"))
    assert(result.isLeft, "expected Left for a missing file")
  }

  test("file with unparseable content returns Left") {
    withTempScala("@@@ this is not valid Scala @@@") { path =>
      val result = StaticAnalyzer.analyzeFile(path)
      assert(result.isLeft, "expected Left for a parse failure")
    }
  }

  // ── 2. SourceFile anchor node ──────────────────────────────────────────────

  test("empty file produces exactly one SourceFile node") {
    withTempScala("") { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      assertEquals(nodes.size, 1)
      assertEquals(nodes.head.kind, GraphNodeKind.SourceFile)
    }
  }

  test("SourceFile node id equals filePath.toString") {
    withTempScala("") { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      assertEquals(nodes.head.id, path.toString)
    }
  }

  test("SourceFile node label equals the bare filename without directory") {
    withTempScala("") { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      assertEquals(nodes.head.label, path.getFileName.toString)
      assert(
        !nodes.head.label.contains(java.io.File.separator),
        "label must not contain a path separator"
      )
    }
  }

  test("SourceFile node is always the first element of the result list") {
    withTempScala("class Foo") { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      assertEquals(nodes.head.kind, GraphNodeKind.SourceFile)
    }
  }

  test("exactly one SourceFile node even in a file with many definitions") {
    withTempScala(
      """|class A
         |class B
         |def topFn(): Unit = ()
         |object Obj
         |""".stripMargin
    ) { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      val fileNodes = nodes.filter(_.kind == GraphNodeKind.SourceFile)
      assertEquals(fileNodes.size, 1)
    }
  }

  test("SourceFile node has no lineNumber property") {
    withTempScala("") { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      assert(
        !nodes.head.properties.contains("lineNumber"),
        "SourceFile anchor should not carry a lineNumber"
      )
    }
  }

  test("SourceFile node embeddingText holds the full source content") {
    val source = "class Foo\nclass Bar\n"
    withTempScala(source) { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      assertEquals(nodes.head.embeddingText, Some(source))
    }
  }

  // ── 3. Class and object detection ─────────────────────────────────────────

  test("single class produces two nodes: SourceFile and Class") {
    withTempScala("class Foo") { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      assertEquals(nodes.size, 2)
      val cls = nodes.tail.head
      assertEquals(cls.kind, GraphNodeKind.Class)
      assertEquals(cls.label, "Foo")
    }
  }

  test("object definition is mapped to GraphNodeKind.Class") {
    withTempScala("object MyObj") { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      val defn = nodes.tail.head
      assertEquals(defn.kind, GraphNodeKind.Class)
      assertEquals(defn.label, "MyObj")
    }
  }

  test("multiple top-level classes all produce separate Class nodes") {
    withTempScala(
      """|class Alpha
         |class Beta
         |class Gamma
         |""".stripMargin
    ) { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      val classes = nodes.filter(_.kind == GraphNodeKind.Class)
      assertEquals(classes.size, 3)
      assertEquals(classes.map(_.label).toSet, Set("Alpha", "Beta", "Gamma"))
    }
  }

  // ── 4. Method vs. Function discrimination ─────────────────────────────────

  test("def inside a class body maps to GraphNodeKind.Method") {
    withTempScala(
      """|class Calc:
         |  def add(a: Int, b: Int): Int = a + b
         |""".stripMargin
    ) { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      val methods = nodes.filter(_.kind == GraphNodeKind.Method)
      assertEquals(methods.size, 1)
      assertEquals(methods.head.label, "add")
    }
  }

  test("def inside an object body maps to GraphNodeKind.Method") {
    withTempScala(
      """|object Runner:
         |  def run(): Unit = ()
         |""".stripMargin
    ) { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      val methods = nodes.filter(_.kind == GraphNodeKind.Method)
      assertEquals(methods.size, 1)
      assertEquals(methods.head.label, "run")
    }
  }

  test("top-level def maps to GraphNodeKind.Function") {
    withTempScala(
      """|def greet(name: String): String = s"Hello, $name"
         |""".stripMargin
    ) { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      val functions = nodes.filter(_.kind == GraphNodeKind.Function)
      assertEquals(functions.size, 1)
      assertEquals(functions.head.label, "greet")
    }
  }

  test("top-level def is never tagged as Method") {
    withTempScala("def topFn(): Unit = ()") { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      assert(
        nodes.forall(_.kind != GraphNodeKind.Method),
        "a top-level def must not be a Method"
      )
    }
  }

  test("class method is never tagged as Function") {
    withTempScala(
      """|class Foo:
         |  def bar(): Unit = ()
         |""".stripMargin
    ) { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      assert(
        nodes.forall(_.kind != GraphNodeKind.Function),
        "a method inside a class must not be a Function"
      )
    }
  }

  test("multiple methods inside one class are all tagged Method") {
    withTempScala(
      """|class MathOps:
         |  def add(a: Int, b: Int): Int = a + b
         |  def sub(a: Int, b: Int): Int = a - b
         |  def mul(a: Int, b: Int): Int = a * b
         |""".stripMargin
    ) { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      val methods = nodes.filter(_.kind == GraphNodeKind.Method)
      assertEquals(methods.size, 3)
      assertEquals(methods.map(_.label).toSet, Set("add", "sub", "mul"))
    }
  }

  // ── 5. Mixed-file scenarios ────────────────────────────────────────────────

  test("class with method and standalone top-level function both appear") {
    withTempScala(
      """|class Calc:
         |  def add(a: Int, b: Int): Int = a + b
         |
         |def helper(): Unit = ()
         |""".stripMargin
    ) { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      val kinds = nodes.map(_.kind)
      assert(kinds.contains(GraphNodeKind.SourceFile), "SourceFile missing")
      assert(kinds.contains(GraphNodeKind.Class),      "Class missing")
      assert(kinds.contains(GraphNodeKind.Method),     "Method missing")
      assert(kinds.contains(GraphNodeKind.Function),   "Function missing")
    }
  }

  test("mixed file: labels are correctly assigned to Method and Function") {
    withTempScala(
      """|class Calc:
         |  def multiply(a: Int, b: Int): Int = a * b
         |
         |def helper(): Unit = ()
         |""".stripMargin
    ) { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      val method   = nodes.find(_.kind == GraphNodeKind.Method).get
      val function = nodes.find(_.kind == GraphNodeKind.Function).get
      assertEquals(method.label,   "multiply")
      assertEquals(function.label, "helper")
    }
  }

  test("class and companion object both appear as Class nodes") {
    withTempScala(
      """|class Config(val host: String)
         |object Config:
         |  def default: Config = Config("localhost")
         |""".stripMargin
    ) { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      val classes = nodes.filter(_.kind == GraphNodeKind.Class)
      assertEquals(classes.size, 2)
      assertEquals(classes.map(_.label).toSet, Set("Config"))
    }
  }

  // ── 6. Node metadata ──────────────────────────────────────────────────────

  test("all nodes have embeddingText defined") {
    withTempScala(
      """|class Foo:
         |  def bar(): Unit = ()
         |""".stripMargin
    ) { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      assert(nodes.forall(_.embeddingText.isDefined), "every node must have embeddingText")
    }
  }

  test("definition nodes carry filePath in properties") {
    withTempScala("class Foo") { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      val cls = nodes.find(_.kind == GraphNodeKind.Class).get
      assertEquals(cls.properties.get("filePath"), Some(path.toString))
    }
  }

  test("definition nodes carry lineNumber in properties") {
    withTempScala("class Foo") { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      val cls = nodes.find(_.kind == GraphNodeKind.Class).get
      assert(cls.properties.contains("lineNumber"), "lineNumber must be in properties")
    }
  }

  test("definition nodes carry sourceLanguage=Scala in properties") {
    withTempScala("class Foo") { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      val cls = nodes.find(_.kind == GraphNodeKind.Class).get
      assertEquals(cls.properties.get("sourceLanguage"), Some("Scala"))
    }
  }

  test("all nodes have platform Jvm") {
    withTempScala(
      """|class Foo:
         |  def bar(): Unit = ()
         |
         |def topFn(): Unit = ()
         |""".stripMargin
    ) { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      assert(nodes.forall(_.platform == PlatformType.Jvm), "all nodes must be Jvm platform")
    }
  }

  test("all nodes have applicationId as empty string (unbound at parse time)") {
    withTempScala("class Foo") { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      assert(nodes.forall(_.applicationId == ""), "applicationId must be empty until bound")
    }
  }

  test("all nodes have empty evidenceIds at parse time") {
    withTempScala("class Foo") { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      assert(nodes.forall(_.evidenceIds.isEmpty), "evidenceIds must be Nil at parse time")
    }
  }

  // ── 7. Node id format ─────────────────────────────────────────────────────

  test("Class node id has format <file>#class:<name>:<line>") {
    withTempScala("class Foo") { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      val cls = nodes.find(_.kind == GraphNodeKind.Class).get
      assert(cls.id.startsWith(path.toString),  "id must start with the file path")
      assert(cls.id.contains("#class:Foo:"),     "id must encode kind and name")
    }
  }

  test("Method node id has format <file>#method:<name>:<line>") {
    withTempScala(
      """|class Foo:
         |  def bar(): Unit = ()
         |""".stripMargin
    ) { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      val method = nodes.find(_.kind == GraphNodeKind.Method).get
      assert(method.id.contains("#method:bar:"), "id must encode 'method' as the kind segment")
    }
  }

  test("Function node id has format <file>#function:<name>:<line>") {
    withTempScala("def greet(): Unit = ()") { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      val fn = nodes.find(_.kind == GraphNodeKind.Function).get
      assert(fn.id.contains("#function:greet:"), "id must encode 'function' as the kind segment")
    }
  }

  test("Object node id has format <file>#object:<name>:<line>") {
    withTempScala("object Ops") { path =>
      val Right(nodes) = StaticAnalyzer.analyzeFile(path): @unchecked
      val obj = nodes.find(_.kind == GraphNodeKind.Class).get
      assert(obj.id.contains("#object:Ops:"), "id must encode 'object' as the kind segment")
    }
  }
