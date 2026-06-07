ThisBuild / organization := "io.github.0x1docd00d"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.3"

val catsVersion       = "2.12.0"
val catsEffectVersion = "3.5.7"
val log4catsVersion   = "2.7.0"
val munitVersion      = "1.0.2"

lazy val commonSettings = Seq(
  scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked"),
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-core" % catsVersion,
    "org.typelevel" %% "cats-effect" % catsEffectVersion,
    "org.typelevel" %% "log4cats-core" % log4catsVersion,
    "org.typelevel" %% "log4cats-noop" % log4catsVersion,
    "org.scalameta" %% "munit" % munitVersion % Test
  ),
  testFrameworks += new TestFramework("munit.Framework")
)

def grailModule(name: String) =
  Project(id = name, base = file(s"modules/$name"))
    .settings(commonSettings*)

lazy val grailCore       = grailModule("grail-core")
lazy val grailStatic     = grailModule("grail-static")
lazy val grailJvm        = grailModule("grail-jvm")
lazy val grailLlvm       = grailModule("grail-llvm")
lazy val grailJadida     = grailModule("grail-jadida")
lazy val grailInstrument = grailModule("grail-instrument")
lazy val grailRunner     = grailModule("grail-runner")
lazy val grailMutator    = grailModule("grail-mutator")
lazy val grailDiff       = grailModule("grail-diff")
lazy val grailGraph      = grailModule("grail-graph")
lazy val grailLlm        = grailModule("grail-llm")
lazy val grailSmt        = grailModule("grail-smt")
lazy val grailApi        = grailModule("grail-api")
lazy val grailCli        = grailModule("grail-cli")

lazy val root = Project(id = "grail-dx", base = file("."))
  .aggregate(
    grailCore,
    grailStatic,
    grailJvm,
    grailLlvm,
    grailJadida,
    grailInstrument,
    grailRunner,
    grailMutator,
    grailDiff,
    grailGraph,
    grailLlm,
    grailSmt,
    grailApi,
    grailCli
  )
  .settings((commonSettings ++ Seq(
    name := "grail-dx",
    publish / skip := true
  ))*)
