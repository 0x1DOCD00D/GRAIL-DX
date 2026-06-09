package io.github.`0x1docd00d`
package grail.static

import java.nio.file.{Files, Path}
import java.time.Instant
import scala.meta.*
import scala.util.Try

import grail.core.*

/**
 * Entry point for Scalameta-backed structural parsing of Scala source files.
 *
 * `analyzeFile` is the only public surface: it reads a file, parses it as a
 * Scala 3 source tree, and produces a list of [[EvidenceGraphNode]] values
 * headed by exactly ONE `SourceFile` anchor node for the file itself, followed
 * by one node per class, object, method, and top-level function definition
 * found anywhere in the tree (including nested definitions).
 *
 * The method / function distinction is resolved by threading an `insideClass`
 * flag through the recursive descent:
 *   - `Defn.Def` whose ancestor chain contains a `Defn.Class` or `Defn.Object`
 *     → [[GraphNodeKind.Method]]
 *   - `Defn.Def` whose ancestor chain contains NO class or object definition
 *     → [[GraphNodeKind.Function]]
 *
 * The function is pure except for the single file-read IO.  Parse and IO
 * failures are returned as `Left` so callers never need to catch exceptions.
 *
 * Later stages in `grail-static` will enrich these nodes with call-graph edges,
 * data-flow facts, and SemanticDB symbol information.
 */
object StaticAnalyzer:

  /**
   * Reads `filePath`, parses it as a Scala 3 source tree, and returns the
   * extracted [[EvidenceGraphNode]] list.
   *
   * @param filePath absolute or relative path to the `.scala` source file
   * @return `Right(nodes)` on success; `Left(cause)` if the file cannot be
   *         read or Scalameta reports a tokenise / parse error
   */
  def analyzeFile(filePath: Path): Either[Throwable, List[EvidenceGraphNode]] =
    for
      content <- readFile(filePath)
      nodes   <- parseAndExtract(filePath, content)
    yield nodes

  // ── private pipeline ───────────────────────────────────────────────────────

  private def readFile(filePath: Path): Either[Throwable, String] =
    Try(Files.readString(filePath)).toEither

  /**
   * Parses `content` with the Scala 3 dialect and lifts any parse failure into
   * `Left`.  The Scalameta `ParseException` carries the failing position and a
   * human-readable message, both preserved in the `Left` payload.
   */
  private def parseAndExtract(
    filePath: Path,
    content:  String
  ): Either[Throwable, List[EvidenceGraphNode]] =
    given Dialect = dialects.Scala3
    val input = Input.VirtualFile(filePath.toString, content)
    Try(input.parse[Source].get).toEither
      .map(tree => extractNodes(filePath, tree))

  /**
   * Constructs the full node list: a single [[GraphNodeKind.SourceFile]] anchor
   * node is prepended, then every definition node discovered by
   * [[gatherDefinitions]] follows in tree-traversal order.
   */
  private def extractNodes(filePath: Path, tree: Source): List[EvidenceGraphNode] =
    val fileStr  = filePath.toString
    val now      = Instant.now()
    val fileNode = EvidenceGraphNode(
      id            = fileStr,
      applicationId = "",
      platform      = PlatformType.Jvm,
      kind          = GraphNodeKind.SourceFile,
      label         = filePath.getFileName.toString,
      properties    = Map(
        "filePath"       -> fileStr,
        "sourceLanguage" -> "Scala"
      ),
      evidenceIds   = Nil,
      embeddingText = Some(tree.pos.text),
      createdAt     = now
    )
    fileNode :: gatherDefinitions(tree.children, insideClass = false, fileStr, now)

  /**
   * Recursively descends the direct children of each node, threading the
   * `insideClass` flag downward through the entire subtree.
   *
   * The flag starts as `false` at the top level.  On entering any
   * [[Defn.Class]] or [[Defn.Object]], it is set to `true` and remains `true`
   * for every transitive descendant.  This single bit provides enough context
   * to distinguish a member [[GraphNodeKind.Method]] from a top-level
   * [[GraphNodeKind.Function]] without relying on parent-pointer lookups
   * (which Scalameta does not expose on parsed trees).
   *
   * All other tree nodes — package clauses, template bodies, block expressions,
   * parameter lists, etc. — are transparent: the function recurses into their
   * children while keeping `insideClass` unchanged.
   */
  private def gatherDefinitions(
    nodes:       List[Tree],
    insideClass: Boolean,
    fileStr:     String,
    now:         Instant
  ): List[EvidenceGraphNode] =
    nodes.flatMap: node =>
      node match

        case cls: Defn.Class =>
          toNode(
            id    = nodeId(fileStr, "class", cls.name.value, cls.pos.startLine),
            kind  = GraphNodeKind.Class,
            label = cls.name.value,
            file  = fileStr,
            line  = cls.pos.startLine,
            text  = cls.pos.text,
            now   = now
          ) :: gatherDefinitions(cls.children, insideClass = true, fileStr, now)

        case obj: Defn.Object =>
          toNode(
            id    = nodeId(fileStr, "object", obj.name.value, obj.pos.startLine),
            kind  = GraphNodeKind.Class,
            label = obj.name.value,
            file  = fileStr,
            line  = obj.pos.startLine,
            text  = obj.pos.text,
            now   = now
          ) :: gatherDefinitions(obj.children, insideClass = true, fileStr, now)

        case defn: Defn.Def =>
          val (kind, kindStr) =
            if insideClass then (GraphNodeKind.Method,   "method")
            else                (GraphNodeKind.Function, "function")
          toNode(
            id    = nodeId(fileStr, kindStr, defn.name.value, defn.pos.startLine),
            kind  = kind,
            label = defn.name.value,
            file  = fileStr,
            line  = defn.pos.startLine,
            text  = defn.pos.text,
            now   = now
          ) :: gatherDefinitions(defn.children, insideClass, fileStr, now)

        case other =>
          gatherDefinitions(other.children, insideClass, fileStr, now)

  // ── helpers ────────────────────────────────────────────────────────────────

  /** Stable, human-readable identifier: `<file>#<kind>:<name>:<line>`. */
  private def nodeId(file: String, kind: String, name: String, line: Int): String =
    s"$file#$kind:$name:$line"

  /**
   * Constructs an [[EvidenceGraphNode]] from parsed definition metadata.
   *
   * `applicationId` is left empty at this stage; it is bound later when the
   * node is registered against a concrete [[ApplicationSpec]].  `platform` is
   * defaulted to `Jvm` because `grail-static` currently targets Scala / JVM
   * source trees; LLVM targets are handled by `grail-llvm`.
   */
  private def toNode(
    id:    String,
    kind:  GraphNodeKind,
    label: String,
    file:  String,
    line:  Int,
    text:  String,
    now:   Instant
  ): EvidenceGraphNode =
    EvidenceGraphNode(
      id            = id,
      applicationId = "",
      platform      = PlatformType.Jvm,
      kind          = kind,
      label         = label,
      properties    = Map(
        "filePath"       -> file,
        "lineNumber"     -> line.toString,
        "sourceLanguage" -> "Scala"
      ),
      evidenceIds   = Nil,
      embeddingText = Some(text),
      createdAt     = now
    )
