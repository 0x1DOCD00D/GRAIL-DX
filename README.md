# GRAIL-DX
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Scala 3](https://img.shields.io/badge/Scala-3.6+-red.svg)](https://www.scala-lang.org/)

**G**raph **R**easoning And **A**bductive **I**nference with **L**LMs for **D**ifferential **D**iagnosis

---
### **Author:** [Dr. Mark Grechanik](http://www.cs.uic.edu/~drmark/) • 📧 [Email 0x1DOCD00D](mailto:0x1DOCD00D@drmark.tech)


GRAIL-DX is a Scala 3 framework for automatically localizing functional faults in deployed software applications. A stakeholder enters symptoms of a failure, input values, configuration values, and deployment context. GRAIL-DX returns likely source code or configuration locations, explains how the suspected fault can propagate to the observed failure, and proposes repairs or differential diagnosis experiments. GRAIL-DX is a subproject of Abductive Reasoning on Graphs for Unified Symptom Differential Diagnosis, [ARGUS-DX](https://drmark.tech/research/llm-fault-diagnosis) that studies how _large language models (LLMs)_ can be augmented with application specific evidence through retrieval augmented generation, graph reasoning, and differential diagnosis to pinpoint faults in complex systems. Within this broader program, GRAIL-DX focuses specifically on software fault localization, using static analysis, dynamic traces, injected faults, configuration artifacts, and GraphRAG to help LLMs reason from observed symptoms back to likely source code, bytecode, LLVM IR, or cloud configuration faults.

GRAIL-DX targets Java, Scala, and LLVM based applications. For JVM applications, GRAIL-DX analyzes source code, bytecode, tests, runtime traces, and configuration artifacts. For LLVM based applications, GRAIL-DX analyzes source code, LLVM IR, native execution traces, compiler level instrumentation, and configuration artifacts. The goal is to support applications whose executable behavior can be connected back to analyzable program structure.

This implementation replaces the original probabilistic logical network core with an LLM guided GraphRAG architecture. The LLM does not invent static or dynamic facts. It uses external tools to obtain code structure, control flow, data flow, execution traces, mutation evidence, configuration dependencies, and symbolic feasibility results. The LLM acts as a ReAct diagnosis agent that asks for evidence, reasons over retrieved graph slices, ranks explanations, and requests more experiments when the evidence is ambiguous.

GRAIL-DX can work with cloud based LLMs and local LLMs. Cloud based models such as Claude, GPT, or Gemini can be used when stronger reasoning, tool use, and larger context windows are needed. Local models served through Ollama or comparable runtimes can be used when privacy, cost control, offline execution, or institutional policy prevents sending code and traces to a cloud provider.

## Table of contents

- [Project vision](#project-vision)
- [Architectural principle](#architectural-principle)
- [Target applications](#target-applications)
- [LLM deployment modes](#llm-deployment-modes)
- [Repository layout](#repository-layout)
- [Step one. Define the application specification](#step-one-define-the-application-specification)
- [Step two. Ingest source code, documentation, and configuration artifacts](#step-two-ingest-source-code-documentation-and-configuration-artifacts)
- [Step three. Build the static analysis pipeline](#step-three-build-the-static-analysis-pipeline)
- [Step four. Select fault injection locations](#step-four-select-fault-injection-locations)
- [Step five. Generate mutants and configuration perturbations](#step-five-generate-mutants-and-configuration-perturbations)
- [Step six. Instrument the application](#step-six-instrument-the-application)
- [Step seven. Run original and mutant executions](#step-seven-run-original-and-mutant-executions)
- [Step eight. Compute differential traces](#step-eight-compute-differential-traces)
- [Step nine. Build the evidence graph and vector index](#step-nine-build-the-evidence-graph-and-vector-index)
- [Step 10. Expose analysis tools to the LLM](#step-10-expose-analysis-tools-to-the-llm)
- [Step 10A. Invoke external tools through MCP subagents](#step-10a-invoke-external-tools-through-mcp-subagents)
- [Step 11. Implement the ReAct codebase analysis agent](#step-11-implement-the-react-codebase-analysis-agent)
- [Step 12. Implement the abductive diagnosis agent](#step-12-implement-the-abductive-diagnosis-agent)
- [Step 13. Add symbolic path checking](#step-13-add-symbolic-path-checking)
- [Step 14. Implement the DDX experiment planner](#step-14-implement-the-ddx-experiment-planner)
- [Step 15. Implement repair synthesis and validation](#step-15-implement-repair-synthesis-and-validation)
- [Step 16. Build the API and command line interface](#step-16-build-the-api-and-command-line-interface)
- [Step 17. Close the feedback loop](#step-17-close-the-feedback-loop)
- [Step 18. Evaluate GRAIL-DX](#step-18-evaluate-grail-dx)
- [Framework choices](#framework-choices)
- [Bytecode and instrumentation references](#bytecode-and-instrumentation-references)
- [End to end data flow](#end-to-end-data-flow)

## Project vision

GRAIL-DX is built around one practical goal. A developer or operator should be able to describe a deployed failure and receive a ranked diagnosis that points to specific code or configuration locations, explains the route from suspected fault to observed symptom, and proposes the next action.

The implementation treats diagnosis as grounded abductive reasoning. The system starts with a symptom and works backward through source structure, runtime traces, configuration dependencies, injected fault evidence, and feasible propagation paths. The LLM is useful because it can synthesize these evidence sources into a diagnosis, but the LLM is not treated as the source of truth.

## Architectural principle

The implementation follows a strict division of labor.

Static analyzers build the map of the application. Dynamic analyzers record how execution moves through that map. Fault injection creates counterfactual evidence. The evidence graph stores the memory of source structure, runtime behavior, documentation, and failures. The theorem prover rejects infeasible explanations. The LLM performs grounded abductive diagnosis through ReAct.

This division is necessary because a large language model should not be trusted to reconstruct whole program control flow or data flow by reading code alone. It can summarize, compare, explain, and hypothesize, but the authoritative facts must come from tools. GRAIL-DX therefore exposes static analysis, dynamic analysis, graph retrieval, trace comparison, symbolic checking, and experiment execution as callable tools.

## Target applications

GRAIL-DX targets Java, Scala, and LLVM based applications.

For Java and Scala applications, GRAIL-DX works with source code, JVM bytecode, build files, tests, dependency metadata, logs, traces, metrics, and configuration artifacts. Static analysis can operate over Scala 3 typed trees, SemanticDB metadata, JVM bytecode, and interprocedural call and data flow models. Dynamic analysis can use JVM instrumentation, Java agents, OpenTelemetry, bytecode probes, and test execution traces.

For Java applications, GRAIL-DX can also use JADIDA as a home grown instrumentation project for computing runtime dataflow. JADIDA should be treated as a first class GRAIL-DX instrumentation backend for Java applications because it can provide application specific dynamic dataflow evidence that generic telemetry tools usually do not expose.

For LLVM based applications, GRAIL-DX works with source code, LLVM IR, compiler generated metadata, native binaries, test executions, runtime traces, and configuration artifacts. Static analysis can operate over LLVM IR, control flow graphs, call graphs, data flow facts, debug symbols, and compiler generated source mappings. Dynamic analysis can use compiler passes, sanitizers, profiling hooks, trace instrumentation, and native test harnesses.

The design goal is not to force all languages into one analysis mechanism. The goal is to normalize their evidence into the same GRAIL-DX graph model. JVM and LLVM applications may use different analyzers and instrumentation tools, but they should produce compatible graph nodes, graph edges, trace records, mutation records, and symptom signatures.

## LLM deployment modes

GRAIL-DX supports cloud based and local LLM deployment.

Cloud based LLMs should be used when diagnosis requires strong reasoning, larger context windows, better tool use, or higher quality repair synthesis. This mode is appropriate when source code and traces can be sent to a managed provider under the relevant security and privacy rules.

Local LLMs should be used when code privacy, offline execution, cost control, regulatory constraints, or institutional policies prevent cloud inference. Local deployment can use Ollama or comparable runtimes. In this mode, GRAIL-DX should keep the same ReAct tool protocol, but may use smaller evidence packets, more aggressive graph retrieval, and more deterministic ranking outside the model.

The LLM provider should be hidden behind an interface. The rest of GRAIL-DX should not care whether a diagnosis step is performed by Claude, GPT, Gemini, Ollama, or another model.

A minimal LLM interface is shown below.

```scala
trait LlmClient:
  def complete(request: LlmRequest): cats.effect.IO[LlmResponse]
  def completeWithTools(request: ToolUseRequest): cats.effect.IO[ToolUseResponse]
```

This abstraction allows GRAIL-DX to switch between cloud and local inference without rewriting the diagnosis pipeline.

## Repository layout

The implementation should be organized as a multi module Scala 3 project managed by sbt. A compact module layout is shown below.

```text
grail-dx/
  build.sbt
  project/
  modules/
    grail-core/
    grail-static/
    grail-jvm/
    grail-llvm/
    grail-jadida/
    grail-instrument/
    grail-runner/
    grail-mutator/
    grail-diff/
    grail-graph/
    grail-llm/
    grail-smt/
    grail-api/
    grail-cli/
```

The `grail-core` module defines shared domain objects such as application specifications, source locations, graph nodes, graph edges, symptoms, hypotheses, traces, mutants, and diagnosis reports. The `grail-static` module defines common static analysis abstractions. The `grail-jvm` module implements Java, Scala, and JVM bytecode analysis. The `grail-llvm` module implements LLVM IR and native application analysis. The `grail-jadida` module integrates JADIDA as a Java instrumentation backend for collecting runtime dataflow evidence. The `grail-instrument` module inserts probes into selected code locations. The `grail-runner` module executes original and mutated versions of the application under sampled inputs and configurations. The `grail-mutator` module creates traceable injected faults.

The `grail-diff` module aligns original and mutant executions and computes runtime differences. The `grail-graph` module builds and queries the evidence graph. The `grail-llm` module implements ReAct agents and LLM tool calling. The `grail-smt` module checks path feasibility and simple repair constraints. The `grail-api` and `grail-cli` modules expose the system to users and other tools.

This structure keeps JVM analysis, LLVM analysis, JADIDA integration, LLM orchestration, and graph construction separate. That separation matters because bytecode tools, compiler IR tools, dataflow instrumentation, solvers, and LLM providers will evolve independently.

## Step one. Define the application specification

GRAIL-DX begins with an application specification. The required inputs are the repository path, language family, build commands, test commands, entry points, input parameter domains, configuration domains, observable outputs, deployment descriptors, infrastructure artifacts, and symptom vocabulary. The output is an `ApplicationSpec` that becomes the contract for all later stages.

A minimal `ApplicationSpec` can be represented as shown below.

```scala
enum ApplicationPlatform:
  case Jvm
  case Llvm
  case Mixed

enum InstrumentationBackend:
  case ByteBuddy
  case Asm
  case Javassist
  case Cglib
  case Jadida
  case LlvmPass
  case OpenTelemetry

final case class ApplicationSpec(
  applicationId: String,
  platform: ApplicationPlatform,
  instrumentationBackends: List[InstrumentationBackend],
  repositoryRoot: java.nio.file.Path,
  buildCommand: List[String],
  testCommand: List[String],
  entryPoints: List[EntryPointSpec],
  inputDomains: List[InputDomainSpec],
  configurationDomains: List[ConfigurationDomainSpec],
  observableOutputs: List[OutputSpec],
  deploymentArtifacts: List[java.nio.file.Path],
  symptomVocabulary: List[SymptomKind]
)
```

This step is necessary because GRAIL-DX cannot analyze a system until it knows what to build, what to run, what to mutate, what platform specific analyzers to use, what instrumentation backend to use, and what counts as a failure. The application specification feeds source ingestion, static analysis, test execution, mutation planning, instrumentation, and diagnosis.

## Step two. Ingest source code, documentation, and configuration artifacts

The ingestion stage collects all artifacts that may contain diagnostic evidence. The inputs include source files, build files, README files, troubleshooting guides, runbooks, API documentation, inline comments, infrastructure as code files, service manifests, CI files, and historical incident reports. The output is an evidence corpus with normalized records for files, document chunks, code snippets, comments, configuration keys, service names, known symptoms, and known remediations.

The LLM can assist in this step as a constrained extractor. It may read documentation and propose candidate graph facts under a fixed schema, but it must not directly update the graph as if its interpretation were ground truth. A troubleshooting guide can produce a candidate relation such as `error code suggests cause` or `configuration option affects behavior`, but that relation should be labeled as documentation derived evidence until linked to code, configuration, traces, or human confirmation.

A compact evidence record is shown below.

```scala
final case class EvidenceItem(
  id: String,
  applicationId: String,
  kind: EvidenceKind,
  sourcePath: java.nio.file.Path,
  lineStart: Option[Int],
  lineEnd: Option[Int],
  text: String,
  provenance: Provenance,
  confidence: EvidenceConfidence
)
```

The evidence corpus feeds static analysis, GraphRAG indexing, documentation based priors, and later explanation generation.

## Step three. Build the static analysis pipeline

The static analysis stage extracts source level, bytecode level, and IR level structure.

For Scala 3 code, the implementation should use Scala 3 TASTy inspection and Scalameta SemanticDB to recover typed trees, symbols, source positions, and semantic information. For Java and JVM bytecode, the implementation should use OPAL and SootUp to recover bytecode structure, call graphs, and interprocedural data flow facts. For Java applications instrumented with JADIDA, the static analyzer should also identify variables, method boundaries, field accesses, object flows, and value producing expressions that should be observed by JADIDA during execution. For LLVM based applications, the implementation should use LLVM IR analysis, Clang or compiler front end metadata when available, debug symbol mappings, and LLVM pass infrastructure to recover control flow, data flow, call graph structure, and source mappings.

The inputs are source files, compiled classes, JVM bytecode, LLVM IR, native binaries, dependency classpaths, TASTy files, SemanticDB files, debug symbols, configuration files, and infrastructure artifacts. The outputs are abstract syntax trees, symbol tables, method or function summaries, call graphs, control flow graphs, data flow graphs, def use chains, exception flow summaries, configuration dependency graphs, and cloud resource dependency graphs.

The main static artifacts are summarized below.

| Artifact | Purpose | Feeds into |
|---|---|---|
| Typed syntax tree | Identifies expressions, declarations, and source locations | Mutation planning and instrumentation |
| Symbol table | Connects names to declarations and types | Data flow and repair synthesis |
| JVM bytecode model | Represents Java and Scala compiled behavior | JVM instrumentation and bytecode mutation |
| JADIDA dataflow targets | Identifies Java runtime dataflow probes | JADIDA instrumentation and dynamic dataflow collection |
| LLVM IR model | Represents compiler level native behavior | LLVM instrumentation and IR mutation |
| Call graph | Shows method or function reachability | Path finding and ReAct tools |
| Control flow graph | Shows execution order and branches | Path feasibility and instrumentation |
| Data flow graph | Shows value movement through the program | Fault to symptom navigation |
| Configuration graph | Shows how settings affect components | Cloud and IaC diagnosis |
| Comment map | Links documentation to code locations | Explanation and priors |

This step is required because GRAIL-DX must provide the LLM with trustworthy structural facts. The output feeds mutation location selection, instrumentation planning, graph construction, and LLM tools that answer questions about calls, data dependencies, control dependencies, source locations, bytecode locations, and JADIDA dataflow targets.

## Step four. Select fault injection locations

Fault injection should be selective. GRAIL-DX should not mutate every possible instruction, because broad mutation creates combinatorial explosion, redundant mutants, and fault interference. The inputs to this stage are the static graphs, output variables, known symptom types, configuration dependencies, documentation derived priors, code churn data, coverage history, and prior incidents.

The output is a fault injection plan with ranked mutation locations, mutation operators, expected affected outputs, execution cost estimates, and interference risk estimates. Candidate locations should include branch predicates, loop guards, return expressions, assignments to observable outputs, exception handlers, serialization boundaries, permission checks, resource binding logic, infrastructure fields, policy rules, port mappings, route definitions, and health check definitions.

For JVM applications, candidate locations can be source expressions, Java bytecode instructions, method entries, method exits, exception handlers, field reads, field writes, and branch instructions. For Java applications using JADIDA, the selection stage should also prefer locations where runtime dataflow evidence would distinguish competing hypotheses, such as assignments to output producing values, transformations of request data, object field updates, and values that cross method boundaries. For LLVM based applications, candidate locations can be LLVM instructions, basic block terminators, function calls, memory operations, comparison instructions, arithmetic operations, and configuration dependent branches.

A location should be selected when an injected fault at that location can cover many plausible real faults, separate competing hypotheses, or invalidate many wrong propagation paths. This stage reduces later abduction time because the GraphRAG and LLM layers face fewer plausible hypotheses.

The selection record is shown below.

```scala
final case class InjectionCandidate(
  id: String,
  location: SourceLocation,
  operator: MutationOperator,
  expectedAffectedOutputs: List[OutputSpec],
  diagnosticLeverage: Double,
  executionCost: Double,
  interferenceRisk: Double,
  preferredInstrumentation: List[InstrumentationBackend],
  rationale: String
)
```

## Step five. Generate mutants and configuration perturbations

The mutation stage creates controlled artificial faults. The inputs are the fault injection plan, source files, typed syntax trees, bytecode locations, LLVM IR locations, infrastructure artifacts, and configuration files. The outputs are `MutantSpec` records and mutated builds.

Each `MutantSpec` should record the mutant identifier, original source location, mutation operator, original expression or instruction, mutated expression or instruction, affected method or function, affected variable, affected configuration key, build identifier, and expected target outputs.

For Scala and Java applications, Stryker4s can be used as a starting point for Scala mutation concepts, but GRAIL-DX should eventually use a custom mutator because it needs trace aligned, graph aware, and configuration aware mutations rather than only killed or survived test results. For JVM bytecode level mutation, GRAIL-DX can use ASM, Byte Buddy, Javassist, cglib, or a combination of these tools depending on the target. For Java applications using JADIDA, each mutant should also record which dataflow probes should be enabled to observe the mutant’s propagation through runtime values. For LLVM based applications, GRAIL-DX can mutate LLVM IR or insert compiler pass based perturbations before native compilation.

A mutation record is shown below.

```scala
final case class MutantSpec(
  mutantId: String,
  applicationId: String,
  platform: ApplicationPlatform,
  location: SourceLocation,
  operator: MutationOperator,
  originalText: String,
  mutatedText: String,
  affectedMethodOrFunction: Option[String],
  affectedVariable: Option[String],
  affectedConfigurationKey: Option[String],
  instrumentationBackends: List[InstrumentationBackend],
  buildId: String
)
```

This stage creates the counterfactual evidence that makes GRAIL-DX different from ordinary telemetry based diagnosis. Its output feeds instrumentation and runtime execution.

## Step six. Instrument the application

The instrumentation stage inserts probes into the original and mutated applications. The inputs are static analysis results, source to bytecode maps, source to LLVM IR maps, selected mutation locations, selected expressions, selected variables, and the test harness metadata. The outputs are instrumented builds, `ProbeSpec` records, and a probe to source map.

The instrumentation should collect method or function entry and exit events, selected expression values, selected instruction values, branch decisions, loop counts, data dependency events, exceptions, timeouts, output values, and configuration observations.

For JVM applications, OpenTelemetry should be used for broad service telemetry, while Byte Buddy, ASM, Javassist, or cglib can be used for custom bytecode instrumentation. Byte Buddy is useful when GRAIL-DX needs higher level runtime class transformation or Java agent integration. ASM is useful when GRAIL-DX needs low level bytecode control. Javassist is useful for source like bytecode manipulation. cglib is useful for dynamic proxy and class generation patterns.

For Java applications, JADIDA should be the preferred home grown backend for computing dynamic dataflow when source level and runtime value propagation are central to the diagnosis. JADIDA should instrument Java applications to observe how values flow through assignments, method calls, returns, field reads, field writes, and selected object interactions. Its output should be normalized into GRAIL-DX trace events and graph edges, so the rest of GRAIL-DX can use JADIDA evidence without depending on JADIDA internals.

For LLVM based applications, instrumentation should be implemented with LLVM passes, compiler inserted probes, sanitizer hooks, profiling hooks, or native tracing wrappers. The implementation should preserve source mappings so that runtime events can be connected back to source locations and graph nodes.

A probe specification is shown below.

```scala
final case class ProbeSpec(
  probeId: String,
  applicationId: String,
  platform: ApplicationPlatform,
  backend: InstrumentationBackend,
  sourceLocation: SourceLocation,
  binaryLocation: Option[BinaryLocation],
  observedKind: ObservedKind,
  graphNodeId: String
)
```

A JADIDA dataflow event should be normalized as shown below.

```scala
final case class JadidaDataflowEvent(
  eventId: String,
  runId: String,
  sourceLocation: SourceLocation,
  fromValueId: Option[String],
  toValueId: String,
  operation: String,
  methodName: String,
  variableName: Option[String],
  fieldName: Option[String],
  timestampNanos: Long
)
```

This stage is required because GRAIL-DX needs runtime evidence that is aligned with source locations and graph nodes. JADIDA is especially important for Java applications where the diagnosis depends on concrete runtime dataflow rather than only method level traces or logs. The probe map feeds trace collection and differential analysis.

## Step seven. Run original and mutant executions

The runner executes the original application and each mutated application over sampled inputs and configurations. The inputs are instrumented builds, generated input vectors, configuration vectors, test commands, dependency containers, timeout policies, and observable output definitions. The outputs are `TraceRun` records.

A `TraceRun` should include the run identifier, mutant identifier if one exists, input vector, configuration vector, method or function events, branch decisions, loop counts, expression or instruction values, dynamic dataflow events, def use events, exceptions, timeouts, logs, metrics, traces, output values, and resource observations. ScalaTest should be used for Scala test execution, and Testcontainers should be used when realistic databases, message queues, browsers, or service dependencies are required. For Java applications instrumented with JADIDA, the runner should collect and persist JADIDA dataflow events as part of the same run. For LLVM based applications, the runner should support native executable invocation, command line test harnesses, generated drivers, and containerized execution.

A trace run summary is shown below.

```scala
final case class TraceRun(
  runId: String,
  applicationId: String,
  platform: ApplicationPlatform,
  mutantId: Option[String],
  inputVector: Map[String, String],
  configurationVector: Map[String, String],
  events: Vector[TraceEvent],
  dataflowEvents: Vector[JadidaDataflowEvent],
  outputs: Map[String, ObservedValue],
  symptoms: Vector[ObservedSymptom],
  durationMillis: Long
)
```

This stage feeds differential analysis. It also creates the dynamic evidence used later by the ReAct agent through trace retrieval and graph tools.

## Step eight. Compute differential traces

The differential analysis stage aligns each mutant run with the corresponding original run under the same inputs and configurations. The inputs are original `TraceRun` records, mutant `TraceRun` records, static control and data flow graphs, JADIDA dataflow events, probe maps, mutation records, and output definitions. The outputs are `DiffTrace`, `PropagationEvidence`, and `SymptomSignature` records.

A `DiffTrace` should record changed values, changed branches, changed control paths, changed dataflow paths, changed return values, new exceptions, timeouts, output sign changes, output magnitude changes, and configuration effects. A `PropagationEvidence` record should describe how an injected fault appears to propagate through control flow, data flow, call flow, exception flow, or configuration dependency. A `SymptomSignature` should summarize the externally visible symptom produced by the mutant.

For JVM applications, differential traces are aligned through source positions, bytecode positions, method signatures, probe identifiers, JADIDA value identifiers, and graph node identifiers. For Java applications with JADIDA enabled, GRAIL-DX should compare original and mutant dataflow events to identify where a value first diverges and how the divergence reaches an output or symptom. For LLVM based applications, traces are aligned through source mappings, IR instruction identifiers, function names, debug symbols, probe identifiers, and graph node identifiers.

This stage is required because execution alone is not diagnostic. A statement that merely executes is not necessarily suspicious. A statement whose changed value propagates to the symptom is much more important. JADIDA makes this distinction sharper for Java applications by showing runtime value propagation directly. The outputs feed graph construction and later diagnosis.

## Step nine. Build the evidence graph and vector index

The graph construction stage merges static and dynamic evidence into a single application specific diagnostic graph. The inputs are static analysis results, the evidence corpus, mutation records, trace runs, JADIDA dataflow events, differential traces, propagation evidence, symptom signatures, and historical incidents. The outputs are graph nodes, graph edges, edge weights, text chunks, embeddings, and retrieval indices.

Neo4j is a practical graph storage choice because it supports graph queries and vector search. The graph should contain nodes for applications, files, classes, methods, functions, statements, expressions, bytecode instructions, LLVM IR instructions, variables, runtime values, comments, configuration options, cloud resources, runs, trace events, dataflow events, mutants, symptoms, outputs, candidate diagnoses, and patches. It should contain edges for containment, calls, control dependence, static data flow, JADIDA dynamic data flow, configuration dependence, resource dependence, execution, mutation, propagation, symptom observation, symptom similarity, and repair evidence.

A simplified graph node is shown below.

```scala
final case class GraphNode(
  id: String,
  applicationId: String,
  platform: ApplicationPlatform,
  kind: GraphNodeKind,
  label: String,
  properties: Map[String, String],
  evidenceIds: List[String]
)
```

A simplified graph edge is shown below.

```scala
final case class GraphEdge(
  id: String,
  applicationId: String,
  sourceId: String,
  targetId: String,
  kind: GraphEdgeKind,
  weight: Double,
  evidenceIds: List[String]
)
```

The vector index should store embeddings for methods, functions, expressions, bytecode snippets, LLVM IR snippets, comments, trace snippets, JADIDA dataflow summaries, troubleshooting documents, incident reports, symptoms, and repair notes. This gives the LLM both semantic retrieval and graph structured retrieval.

## Step 10. Expose analysis tools to the LLM

The LLM should access GRAIL-DX through explicit tools. The inputs are the graph store, vector index, static analysis database, trace database, JADIDA dataflow database, mutation ledger, and symbolic checker. The output is a tool registry available to the ReAct agents.

The core tools are summarized below.

| Tool | Purpose |
|---|---|
| `getApplicationSummary` | Returns entry points, outputs, configs, platform, and graph status |
| `getStaticSlice` | Returns code and configuration slices |
| `getDynamicSlice` | Returns trace based slices |
| `getControlFlow` | Returns control flow around a location |
| `getDataFlow` | Returns static data dependencies around a value |
| `getJadidaDataflow` | Returns Java runtime dataflow evidence collected by JADIDA |
| `getBytecodeView` | Returns JVM bytecode evidence for Java or Scala applications |
| `getLlvmIrView` | Returns LLVM IR evidence for LLVM based applications |
| `retrieveSimilarSymptoms` | Finds prior symptoms resembling the query |
| `retrieveSimilarMutants` | Finds injected faults with similar outcomes |
| `compareOriginalAndMutantTrace` | Explains execution differences |
| `compareOriginalAndMutantDataflow` | Explains JADIDA dataflow differences between original and mutant runs |
| `findFaultToSymptomPaths` | Finds candidate navigation paths |
| `rankHypotheses` | Scores diagnosis candidates |
| `proposeExperiment` | Creates a DDX experiment |
| `runExperiment` | Executes a selected offline experiment |
| `getPatchCandidates` | Retrieves or generates repairs |
| `checkPathFeasibility` | Calls symbolic checking |

The tool request format is shown below.

```json
{
  "reason": "The agent needs Java runtime dataflow evidence for the strongest candidate location.",
  "action": "getJadidaDataflow",
  "arguments": {
    "runId": "RUN-1042",
    "sourceLocation": "OrderCalculator.java:87",
    "maxEvents": 100
  }
}
```

This step is required because the LLM must obtain evidence rather than fabricate it. The diagnosis agent will call these tools whenever it needs source structure, dynamic evidence, graph paths, symbolic results, mutation history, bytecode facts, LLVM IR facts, or JADIDA runtime dataflow facts.

## Step 10A. Invoke external tools through MCP subagents

GRAIL-DX should use the Model Context Protocol, abbreviated as MCP, as the standard mechanism for invoking external tools from ReAct subagents. MCP gives GRAIL-DX a disciplined way to connect LLM based agents to analyzers, databases, repositories, trace stores, graph services, symbolic checkers, build systems, and experiment runners without hard wiring every integration into the main diagnosis loop.

The purpose of MCP in GRAIL-DX is not to replace the internal Scala 3 tool registry. The purpose is to expose internal and external capabilities through a uniform protocol so that subagents can discover tools, request resources, execute actions, and receive structured results. This keeps the ReAct loop grounded in actual evidence instead of asking the LLM to infer missing facts.

The GRAIL-DX host should run the main diagnosis workflow. It should create ReAct subagents for specialized tasks such as codebase analysis, static slicing, dynamic trace comparison, JADIDA dataflow inspection, LLVM IR inspection, DDX planning, repair validation, and report generation. Each subagent should interact with one or more MCP servers through an MCP client. The MCP servers should expose narrowly scoped capabilities with explicit schemas, permissions, and audit logs.

The conceptual topology is shown below.

```text
User
  |
  v
GRAIL-DX API and CLI
  |
  v
GRAIL-DX host
  |
  +-- ReAct diagnosis subagent
  |     |
  |     +-- MCP client
  |           |
  |           +-- graph MCP server
  |           +-- static analysis MCP server
  |           +-- trace MCP server
  |           +-- JADIDA MCP server
  |           +-- LLVM MCP server
  |           +-- SMT MCP server
  |           +-- experiment runner MCP server
  |           +-- repository MCP server
  |
  +-- ReAct DDX subagent
  |
  +-- ReAct repair subagent
```

The main MCP capabilities should be grouped by evidence source. A graph MCP server should expose graph slices, neighborhood queries, source to symptom paths, and ranked candidate paths. A static analysis MCP server should expose call graphs, control flow graphs, data flow graphs, def use chains, source locations, bytecode views, and LLVM IR views. A trace MCP server should expose original runs, mutant runs, trace differences, output differences, exceptions, and timeouts. A JADIDA MCP server should expose Java runtime dataflow events and original versus mutant dataflow differences. An SMT MCP server should expose path feasibility checks and constraint validation. An experiment runner MCP server should execute selected DDX experiments in an offline environment and return structured results.

The minimum MCP server interface should expose tools, resources, and prompts. Tools should perform actions such as `getStaticSlice`, `compareOriginalAndMutantTrace`, `getJadidaDataflow`, `checkPathFeasibility`, and `runExperiment`. Resources should expose read only evidence such as source files, trace records, graph snapshots, mutation ledgers, and diagnosis reports. Prompts should expose reusable task templates for codebase analysis, hypothesis ranking, DDX planning, and repair review.

A compact MCP tool definition for querying JADIDA dataflow is shown below.

```json
{
  "name": "getJadidaDataflow",
  "description": "Return Java runtime dataflow events collected by JADIDA for a selected run and source location.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "runId": { "type": "string" },
      "sourceLocation": { "type": "string" },
      "maxEvents": { "type": "integer" }
    },
    "required": ["runId", "sourceLocation"]
  }
}
```

A ReAct subagent should use MCP calls as evidence acquisition steps. It should first state what evidence is missing, then call the appropriate MCP tool, then update the working diagnosis from the returned result. The model should not proceed from an assumption when a tool can answer the question. If the subagent needs a dataflow fact, it should call the static analysis MCP server or the JADIDA MCP server. If it needs a feasible path, it should call the SMT MCP server. If it needs to distinguish competing hypotheses, it should ask the experiment runner MCP server to execute the selected DDX experiment.

A simplified ReAct trace for a diagnosis subagent is shown below.

```text
Thought: The symptom is a negative invoice total, so I need the backward dataflow from invoice.total.
Action: call MCP tool getDataFlow with output invoice.total.
Observation: invoice.total depends on discountedSubtotal, taxAdjustment, and exemptionAdjustment.

Thought: The top two hypotheses differ in whether the wrong value enters through taxAdjustment or exemptionAdjustment. I need runtime dataflow from the failing run.
Action: call MCP tool getJadidaDataflow for InvoiceCalculator.java:184.
Observation: exemptionAdjustment flows into discountedSubtotal before tax is applied.

Thought: I need to check whether this path is feasible under the reported configuration.
Action: call MCP tool checkPathFeasibility.
Observation: The path is SAT when taxExempt is true and region is EU.

Thought: The evidence supports the exemption ordering fault. I will rank it above the regional discount hypothesis and propose a DDX experiment.
```

MCP integration should be implemented behind a Scala 3 abstraction so that GRAIL-DX can call native Scala services, remote MCP servers, or local MCP servers with the same agent code. A minimal client interface is shown below.

```scala
trait McpClient:
  def listTools(server: McpServerId): cats.effect.IO[List[McpTool]]
  def callTool(server: McpServerId, name: String, arguments: io.circe.Json): cats.effect.IO[McpToolResult]
  def readResource(server: McpServerId, uri: String): cats.effect.IO[McpResource]
  def getPrompt(server: McpServerId, name: String, arguments: io.circe.Json): cats.effect.IO[McpPrompt]
```

The `grail-llm` module should depend on this abstraction, not on individual analyzer implementations. The analyzer implementations can live behind MCP servers or direct Scala services. This separation allows GRAIL-DX to run fully local, fully cloud based, or mixed. In a privacy sensitive deployment, the MCP servers can run on the same machine as the source code and only return compact evidence packets to the LLM. In a cloud deployment, the MCP servers can run near the graph database, trace store, CI system, or experiment cluster.

MCP calls should be governed by security rules. Each server should expose only the tools needed by the subagent. Tool calls that mutate state, execute code, run experiments, create patches, or access private repositories should require explicit policy approval. Every MCP request and response should be logged with query identifiers, tool names, arguments, evidence identifiers, timestamps, and subagent identifiers. This audit trail is essential because GRAIL-DX must explain not only what diagnosis it produced, but also which external evidence was used to produce it.

This stage feeds the ReAct codebase analysis agent, the abductive diagnosis agent, the DDX planner, and the repair validator. It also makes the system extensible. New analyzers, telemetry stores, theorem provers, repository providers, or experiment runners can be added as MCP servers without rewriting the core GRAIL-DX reasoning loop.


## Step 11. Implement the ReAct codebase analysis agent

The codebase analysis agent runs during onboarding. Its inputs are the application specification, evidence corpus, static analysis outputs, and the tool registry. Its outputs are a codebase analysis report, a refined application specification, entry point candidates, high value output variables, configuration variables, and initial injection targets.

The ReAct agent should inspect repository metadata, read documentation, call static analysis tools, ask graph tools for dependencies, identify missing build metadata, and propose instrumentation targets. It should not state that a control or data flow exists unless the static analyzer, dynamic analyzer, graph query, or JADIDA trace supports it.

For Java and Scala applications, the agent should use JVM specific tools to inspect source, bytecode, runtime probes, and JADIDA dataflow when available. For LLVM based applications, it should use LLVM specific tools to inspect IR, native execution evidence, and source mappings.

This stage feeds mutation selection and instrumentation planning. It also converts documentation into structured but provisional diagnostic evidence.

## Step 12. Implement the abductive diagnosis agent

The abductive diagnosis agent runs when a user enters a field failure. The inputs are the symptom description, input values, configuration values, deployment context, graph retrieval tools, trace comparison tools, JADIDA dataflow tools, bytecode or IR tools, and symbolic checking tools. The outputs are ranked diagnoses.

Each diagnosis should include a suspected source or configuration location, a fault template, a navigation path, supporting trace evidence, similar injected faults, alternative explanations, confidence, and proposed remediation. The agent should first parse the symptom into structured observations. It should then retrieve similar symptoms and mutants, request backward static slices from affected outputs, request dynamic slices and differential traces for similar mutants, ask for JADIDA dataflow differences when Java runtime dataflow matters, ask the graph for source to symptom paths, inspect bytecode or LLVM IR where needed, check feasible paths, rank hypotheses, and generate an explanation tied to concrete evidence.

A diagnosis report record is shown below.

```scala
final case class DiagnosisReport(
  queryId: String,
  applicationId: String,
  rankedDiagnoses: List[Diagnosis],
  recommendedExperiments: List[DDXExperiment],
  generatedAt: java.time.Instant
)
```

This step is the main abductive reasoning stage. It reasons from observed effects back to plausible causes, but it does so using graph facts, trace evidence, and JADIDA dataflow evidence supplied by tools.

## Step 13. Add symbolic path checking

The symbolic checking stage validates local feasibility of candidate explanations. The inputs are candidate fault to symptom paths, branch predicates, expression constraints, input values, configuration values, type constraints, path conditions, bytecode facts, LLVM IR facts, and JADIDA dataflow facts. The outputs are feasibility results with `SAT`, `UNSAT`, or `UNKNOWN`, together with model values or rejection explanations when available.

Z3 is a practical solver for this purpose. GRAIL-DX should not attempt to prove the entire application correct. It should check whether a proposed path is locally feasible, whether a range condition can hold, whether a configuration dependency is satisfiable, whether a runtime dataflow path is compatible with the static graph, and whether a candidate repair violates simple invariants.

A feasibility result is shown below.

```scala
final case class FeasibilityResult(
  pathId: String,
  status: SolverStatus,
  model: Option[Map[String, String]],
  explanation: String
)
```

This stage reduces hallucinated explanations and prunes infeasible graph paths before they reach stakeholders. Its output feeds hypothesis reranking and final explanation generation.

## Step 14. Implement the DDX experiment planner

The differential diagnosis planner is invoked when several diagnoses remain plausible. The inputs are ranked hypotheses, ambiguity clusters, candidate paths, available test commands, input domains, configuration domains, mutation templates, execution cost estimates, and platform specific instrumentation capabilities. The outputs are `DDXExperiment` records.

Each DDX experiment should specify the experiment type, target hypothesis, competing hypothesis, selected inputs, selected configurations, optional micro mutant, predicted outcomes under each hypothesis, selected instrumentation backend, and expected information gain. For Java applications, the planner should select JADIDA when the difference between hypotheses depends on runtime value propagation. The experiment executor runs these tests in the offline environment, not at the customer site. The new traces and differential results are fed back into the graph, and the diagnoses are updated.

A DDX experiment record is shown below.

```scala
final case class DDXExperiment(
  experimentId: String,
  applicationId: String,
  targetHypothesisId: String,
  competingHypothesisIds: List[String],
  inputVector: Map[String, String],
  configurationVector: Map[String, String],
  optionalMutant: Option[MutantSpec],
  instrumentationBackends: List[InstrumentationBackend],
  predictedOutcomes: List[PredictedOutcome],
  expectedInformationGain: Double
)
```

This step is necessary because many field failures admit several plausible explanations. DDX turns ambiguous diagnosis into controlled evidence acquisition.

## Step 15. Implement repair synthesis and validation

The repair stage proposes fixes only after the diagnosis has enough support. The inputs are top ranked diagnoses, source slices, graph paths, differential traces, JADIDA dataflow differences, comments, troubleshooting guides, similar historical patches, test failures, symbolic constraints, bytecode facts, and LLVM IR facts. The outputs are `PatchCandidate` records, patched builds, validation results, and optional pull requests.

Repair should happen in several passes. GRAIL-DX should first retrieve repair templates from prior mutants and historical fixes. It should then ask the LLM to adapt a patch to the specific code or configuration location. It should finally validate the patch using tests, selected DDX experiments, regression runs, JADIDA dataflow comparison for Java applications, and symbolic checks where practical.

For Java and Scala applications, repair may target source code, bytecode level patches for experiments, or configuration artifacts. For LLVM based applications, repair should normally target source code or IR level experimental patches, then validate the native build.

A patch candidate record is shown below.

```scala
final case class PatchCandidate(
  patchId: String,
  diagnosisId: String,
  changedFiles: List[java.nio.file.Path],
  diffText: String,
  rationale: String,
  validationStatus: PatchValidationStatus
)
```

A patch without evidence is only confident vandalism. GRAIL-DX should therefore present the patch together with the diagnosis evidence, expected behavior change, validation result, and remaining risk.

## Step 16. Build the API and command line interface

GRAIL-DX should expose both an HTTP API and a command line interface. The inputs are application registration requests, diagnosis requests, symptoms, input values, configuration values, deployment context, and optional artifact uploads. The outputs are structured diagnosis reports, graph path views, DDX plans, patch candidates, and downloadable evidence summaries.

http4s and Circe are suitable choices for the Scala 3 API layer. The API should support application onboarding, graph construction status, diagnosis requests, DDX execution, patch validation, feedback submission, LLM provider selection, platform selection, and instrumentation backend selection. The CLI should support local development workflows, scripted experiments, and benchmark runs.

A minimal CLI shape is shown below.

```text
grail-dx register --spec application.yaml
grail-dx build-graph --app billing-service
grail-dx diagnose --app billing-service --symptom symptom.json
grail-dx run-ddx --app billing-service --experiment EXP-001
grail-dx validate-patch --app billing-service --patch PATCH-001
grail-dx set-llm --provider claude
grail-dx set-llm --provider ollama --model llama3.1:8b
grail-dx set-instrumentation --app billing-service --backend jadida
```

This stage makes GRAIL-DX usable by developers, researchers, and automation pipelines.

## Step 17. Close the feedback loop

Every diagnosis should produce feedback. The inputs are user confirmations, rejected hypotheses, accepted patches, failed patches, successful DDX experiments, new incidents, and application changes. The outputs are updated graph weights, updated priors, new examples for prompts or fine tuning, and improved mutation selection policies.

This step is required because applications evolve. GRAIL-DX should not rebuild everything from scratch after every release. It should incrementally update changed methods, changed functions, changed configuration files, changed infrastructure resources, changed dataflow observations, and newly observed symptoms.

Feedback from developers also improves trust. A rejected explanation should reduce the future weight of similar evidence. An accepted patch should become repair evidence for related failures.

## Step 18. Evaluate GRAIL-DX

GRAIL-DX should be evaluated against direct LLM debugging, code only GraphRAG, telemetry only diagnosis, mutation based fault localization, and the full GRAIL-DX pipeline. The inputs are benchmark applications, real historical bugs, injected bugs, known fixes, symptom reports, input values, configuration values, and gold standard fault locations.

The outputs are Top one, Top three, and Top five localization accuracy, mean reciprocal rank, precision, recall, false positives, false negatives, diagnosis latency, graph retrieval cost, LLM cost, offline modeling cost, path correctness, dataflow path correctness, DDX experiment count, patch plausibility, patch correctness, and developer rated explanation quality.

The evaluation must cover Java applications, Scala applications, and LLVM based applications. It should also compare cloud based LLMs and local LLMs under the same diagnosis tasks. For Java applications, the evaluation should include an ablation that compares GRAIL-DX without JADIDA, GRAIL-DX with JADIDA, and GRAIL-DX with only generic JVM telemetry. This will show whether home grown runtime dataflow instrumentation improves fault localization and explanation faithfulness.

The evaluation must be honest. If direct Claude or Codex solves the same cases faster and cheaper, GRAIL-DX loses those cases. GRAIL-DX wins only when application specific static analysis, dynamic traces, injected faults, JADIDA dataflow evidence, GraphRAG, and DDX produce better localization, better explanations, fewer wrong fixes, or lower amortized cost.

## Framework choices

The recommended framework stack is shown below.

| Framework | Role | Justification |
|---|---|---|
| Scala 3 | Core implementation language | Strong type system and JVM ecosystem |
| sbt | Build tool | Standard Scala build and test workflow |
| TASTy Inspector | Scala source analysis | Typed tree inspection for Scala 3 |
| SemanticDB | Semantic source information | Symbol and type metadata |
| OPAL | JVM bytecode analysis | Scala based bytecode analysis framework |
| SootUp | Interprocedural analysis | JVM call graph and data flow support |
| JADIDA | Java runtime dataflow instrumentation | Home grown project for observing dynamic dataflow in Java applications |
| MCP | External tool protocol | Standardized invocation of analyzers, graph services, trace stores, solvers, and experiment runners from ReAct subagents |
| ASM | Low level JVM bytecode manipulation | Fine grained probe insertion and instruction rewriting |
| Byte Buddy | JVM instrumentation | Runtime and build time code instrumentation |
| Javassist | JVM bytecode manipulation | Source like bytecode editing and agent experiments |
| cglib | Dynamic class generation | Proxy and subclass based instrumentation patterns |
| LLVM | IR analysis and instrumentation | Native and compiler IR based application support |
| OpenTelemetry | Broad runtime telemetry | Service level traces, metrics, and logs |
| ScalaTest | Test execution | Native Scala testing support |
| Testcontainers | Dependency orchestration | Repeatable offline tests with real services |
| Stryker4s | Mutation design reference | Useful starting point for Scala mutations |
| Neo4j | Graph and vector storage | Graph queries plus vector retrieval |
| Z3 | Symbolic checking | Path feasibility and local constraint solving |
| Claude, GPT, Gemini, or Ollama | LLM diagnosis agent | Cloud and local ReAct reasoning with tool use |
| http4s | HTTP API | Functional Scala web service |
| Circe | JSON support | Typed encoding and decoding |

The stack should be hidden behind stable interfaces wherever possible. Static analyzers, graph databases, solvers, instrumentation libraries, compiler frameworks, and LLM providers will change. The GRAIL-DX domain model should survive those changes.

## Bytecode and instrumentation references

The JVM instrumentation layer may use several bytecode manipulation, runtime instrumentation, dataflow tracing, and class generation tools. Their roles overlap, but they are not identical.

| Tool or resource | Role in GRAIL-DX |
|---|---|
| JADIDA | Home grown Java instrumentation project for computing runtime dataflow |
| cglib | Dynamic proxies, subclass generation, and legacy instrumentation patterns |
| Byte Buddy | Java agent instrumentation, runtime class transformation, and high level bytecode generation |
| Byte Buddy Javadocs | API reference for implementation details |
| Javassist | Source like bytecode transformation experiments |
| ASM | Low level bytecode inspection, transformation, and probe insertion |
| Baeldung ASM guide | Practical introduction to ASM usage |
| UCLA bytecode tutorial | Conceptual background on Java bytecode |
| ASM presentation material | Additional bytecode engineering background |
| DZone ASM dynamic classes article | Example oriented dynamic class generation |
| Beyond Java ASM guide | Practical bytecode writing examples |
| New Relic ASM and Javassist article | Engineering discussion of audit log instrumentation with bytecode tools |
| Javassist and Byte Buddy Java agents article | Practical discussion of Java agents and instrumentation |

The reference URLs are listed below.

- https://github.com/0x1DOCD00D/JADIDA
- https://github.com/cglib/cglib/releases
- https://github.com/raphw/byte-buddy
- http://bytebuddy.net/javadoc/1.9.13/index.html
- https://ivanyu.me/blog/2017/11/04/java-agents-javassist-and-byte-buddy/
- https://blog.newrelic.com/engineering/diving-bytecode-manipulation-creating-audit-log-asm-javassist/
- https://asm.ow2.io/
- https://www.baeldung.com/java-asm
- http://web.cs.ucla.edu/~msb/cs239-tutorial/
- https://s3-eu-west-1.amazonaws.com/presentations2012/30_presentation.pdf
- https://dzone.com/articles/fully-dynamic-classes-with-asm
- https://www.beyondjava.net/quick-guide-writing-byte-code-asm

For GRAIL-DX, the practical rule is straightforward. Use JADIDA when Java runtime dataflow is the central evidence needed for diagnosis. Use Byte Buddy when the implementation needs agent based instrumentation with a higher level API. Use ASM when the implementation needs exact bytecode level control. Use Javassist when source like bytecode edits make a prototype faster. Use cglib when proxy or subclass based interception is enough. No single tool should become a religion. This is instrumentation, not theology.

## End to end data flow

The application specification feeds ingestion, static analysis, testing, and mutation planning. Static analysis produces source graphs, bytecode graphs, LLVM IR graphs, JADIDA dataflow targets, and dependency facts that feed instrumentation and injection selection. Mutation planning produces mutants that feed the runner. Instrumentation produces probes that make runtime traces source aligned. JADIDA produces Java runtime dataflow events that show how values move through selected methods, variables, fields, calls, and returns. Test execution produces traces. Differential analysis turns traces and JADIDA dataflow events into propagation evidence and symptom signatures. Graph construction merges all artifacts into an application specific evidence graph and vector index.

The ReAct agents query this graph through external tools, including MCP servers that expose analyzers, graph services, trace stores, JADIDA dataflow, LLVM IR evidence, symbolic checkers, and experiment runners. The codebase analysis agent refines onboarding. The diagnosis agent retrieves evidence and ranks explanations. The DDX agent requests new experiments when hypotheses remain ambiguous. The repair agent proposes and validates fixes. User feedback and new runs flow back into the graph.

GRAIL-DX therefore becomes a grounded diagnostic system rather than a chatbot with a debugger costume. It uses static analysis, dynamic analysis, fault injection, GraphRAG, symbolic checking, bytecode instrumentation, JADIDA runtime dataflow analysis, LLVM IR analysis, and ReAct prompting as cooperating parts of one pipeline.
