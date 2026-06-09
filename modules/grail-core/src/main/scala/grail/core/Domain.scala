package io.github.`0x1docd00d`
package grail.core

import java.nio.file.Path
import java.time.Instant

// ─────────────────────────────────────────────────────────────────────────────
// Application Definition Types
// ─────────────────────────────────────────────────────────────────────────────

/** Target execution platform for the system under test. */
enum PlatformType:
  case Jvm
  case Llvm
  case Mixed

/** Instrumentation backends supported for probe insertion and trace collection. */
enum InstrumentationBackend:
  case ByteBuddy
  case Asm
  case Javassist
  case Cglib
  case Jadida
  case LlvmPass
  case OpenTelemetry

/** Describes what kind of observable output is produced by the application. */
final case class OutputSpec(
  name: String,
  description: String,
  expectedType: String
)

/** Defines the domain of valid inputs for a named parameter. */
final case class InputDomainSpec(
  parameterName: String,
  valueType: String,
  sampleValues: List[String],
  constraints: Option[String]
)

/** Defines the domain of valid configuration settings for a named key. */
final case class ConfigurationDomainSpec(
  configKey: String,
  valueType: String,
  sampleValues: List[String],
  defaultValue: Option[String]
)

/** Describes a known application entry point (e.g. main class, REST endpoint, CLI command). */
final case class EntryPointSpec(
  name: String,
  fullyQualifiedName: String,
  description: String,
  platform: PlatformType
)

/**
 * Enumeration of symptom categories used to constrain the vocabulary
 * reported by stakeholders or detected automatically.
 */
enum SymptomKind:
  case Functional
  case Exception
  case Timeout
  case DataCorruption
  case ResourceLeak
  case SecurityViolation
  case PerformanceDegradation
  case ConfigurationMismatch

/**
 * Top-level specification for the system under test.
 * Acts as the contract for all downstream GRAIL-DX pipeline stages.
 */
final case class ApplicationSpec(
  applicationId: String,
  name: String,
  repositoryRoot: Path,
  platform: PlatformType,
  instrumentationBackends: List[InstrumentationBackend],
  buildCommand: List[String],
  testCommand: List[String],
  entryPoints: List[EntryPointSpec],
  inputDomains: List[InputDomainSpec],
  configurationDomains: List[ConfigurationDomainSpec],
  observableOutputs: List[OutputSpec],
  deploymentArtifacts: List[Path],
  symptomVocabulary: List[SymptomKind],
  configurations: Map[String, String]
)

// ─────────────────────────────────────────────────────────────────────────────
// Diagnostic Evidence & Symptom Types
// ─────────────────────────────────────────────────────────────────────────────

/** Severity level attached to a reported or detected symptom. */
enum Severity:
  case Critical
  case High
  case Medium
  case Low
  case Informational

/**
 * A stakeholder-reported or automatically detected failure signal.
 * Symptoms are the entry point for all abductive diagnosis in GRAIL-DX.
 */
final case class Symptom(
  symptomId: String,
  applicationId: String,
  message: String,
  severity: Severity,
  kind: SymptomKind,
  context: Map[String, String],
  stackTrace: Option[String],
  observedAt: Instant,
  reportedBy: Option[String]
)

/**
 * Holds the input parameters, environment variables, and region context
 * under which a failure was observed.
 */
final case class InputContext(
  contextId: String,
  inputParameters: Map[String, String],
  environmentVariables: Map[String, String],
  region: Option[String],
  deploymentStage: Option[String],
  additionalMetadata: Map[String, String]
)

/** Provenance of a configuration artifact, distinguishing authoritative sources from derived ones. */
enum ConfigProvenance:
  case SourceControlled
  case InfrastructureAsCode
  case RuntimeObserved
  case DocumentationDerived
  case UserProvided

/**
 * Represents the state of a configuration file, property set, or IaC artifact
 * relevant to a diagnosis session.
 */
final case class ConfigArtifact(
  artifactId: String,
  applicationId: String,
  sourcePath: Path,
  configFormat: String,
  properties: Map[String, String],
  provenance: ConfigProvenance,
  capturedAt: Instant,
  isBaseline: Boolean
)

// ─────────────────────────────────────────────────────────────────────────────
// Fault & Mutation Types
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Pinpoints a specific location in source code or configuration
 * where a fault may reside or where a mutation is injected.
 */
final case class FaultLocation(
  filePath: Path,
  lineNumber: Option[Int],
  columnOffset: Option[Int],
  methodName: Option[String],
  expressionPath: Option[String],
  symbolName: Option[String]
)

/** Classifies the type of mutation applied at a fault location. */
enum MutationOperator:
  case ArithmeticReplacement
  case RelationalReplacement
  case LogicalConnectorReplacement
  case NegateConditional
  case ReturnValueSubstitution
  case NullReturn
  case RemoveConditional
  case BoundaryShift
  case ConfigValueReplacement
  case ExceptionSuppression
  case AssignmentSwap
  case MethodCallRemoval

/** Lifecycle state of an injected fault within a trial run. */
enum FaultState:
  case Planned
  case Injected
  case Executed
  case Killed
  case Survived
  case Equivalent
  case Errored

/**
 * Represents a single synthetic fault injected during offline mutation testing.
 * Each injected fault is traceable back to a location, operator, and trial run.
 */
final case class InjectedFault(
  mutationId: String,
  applicationId: String,
  platform: PlatformType,
  mutationOperator: MutationOperator,
  location: FaultLocation,
  originalText: String,
  mutatedText: String,
  affectedMethodOrFunction: Option[String],
  affectedVariable: Option[String],
  affectedConfigurationKey: Option[String],
  state: FaultState,
  buildId: String
)

/**
 * Tracks the collection of injected faults applied during a single offline trial run,
 * enabling differential and propagation analysis across the full mutant population.
 */
final case class MutationLedger(
  ledgerId: String,
  applicationId: String,
  runId: String,
  faults: List[InjectedFault],
  totalPlanned: Int,
  totalInjected: Int,
  totalKilled: Int,
  totalSurvived: Int,
  createdAt: Instant
)

// ─────────────────────────────────────────────────────────────────────────────
// Trace & Execution Evidence
// ─────────────────────────────────────────────────────────────────────────────

/** High-level outcome of an instrumented execution run. */
enum ExecutionStatus:
  case Success
  case Crash
  case LogicalMismatch
  case Timeout

/**
 * A single normalized dataflow event produced by JADIDA instrumentation,
 * recording how a value moves through a Java method at runtime.
 */
final case class JadidaDataflowFact(
  eventId: String,
  runId: String,
  sourceLocation: FaultLocation,
  fromValueId: Option[String],
  toValueId: String,
  operation: String,
  methodName: String,
  variableName: Option[String],
  fieldName: Option[String],
  timestampNanos: Long
)

/**
 * Captures execution metrics, control flow hashes, dynamic log events,
 * and JADIDA dataflow facts produced during a single instrumented run.
 */
final case class ExecutionTrace(
  traceId: String,
  runId: String,
  applicationId: String,
  platform: PlatformType,
  mutationId: Option[String],
  status: ExecutionStatus,
  inputVector: Map[String, String],
  configurationVector: Map[String, String],
  controlFlowHash: String,
  methodEntryCount: Long,
  methodExitCount: Long,
  branchDecisions: Map[String, Boolean],
  observedOutputs: Map[String, String],
  dynamicLogs: List[String],
  jadidaDataflowFacts: List[JadidaDataflowFact],
  exceptionMessages: List[String],
  durationMillis: Long,
  capturedAt: Instant
)

// ─────────────────────────────────────────────────────────────────────────────
// Evidence Graph ADTs  (Neo4j GraphRAG schema)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Classifies the semantic role of a node in the evidence graph.
 * Nodes span source artifacts, runtime observations, mutations, and diagnoses.
 */
enum GraphNodeKind:
  case Application
  case SourceFile
  case Class
  case Method
  case Function
  case Statement
  case Expression
  case BytecodeInstruction
  case LlvmIrInstruction
  case Variable
  case RuntimeValue
  case ConfigOption
  case CloudResource
  case TraceEvent
  case DataflowEvent
  case Mutant
  case ObservedSymptom
  case Hypothesis
  case PatchCandidate
  case DocumentChunk

/**
 * Classifies the semantic role of an edge connecting two nodes
 * in the evidence graph.
 */
enum GraphEdgeKind:
  case Contains
  case Calls
  case ControlDepends
  case StaticDataFlow
  case DynamicDataFlow
  case ConfigDepends
  case ResourceDepends
  case ExecutedBy
  case MutatedFrom
  case PropagatesTo
  case SymptomObservedAt
  case SimilarTo
  case SupportedBy
  case RepairFor

/**
 * A structural node in the GRAIL-DX evidence graph.
 * Nodes carry typed labels, free-form properties, and references to
 * the raw evidence items that justify their presence.
 */
final case class EvidenceGraphNode(
  id: String,
  applicationId: String,
  platform: PlatformType,
  kind: GraphNodeKind,
  label: String,
  properties: Map[String, String],
  evidenceIds: List[String],
  embeddingText: Option[String],
  createdAt: Instant
)

/**
 * A directed relationship between two nodes in the GRAIL-DX evidence graph.
 * Edges carry a weight (used in path ranking) and back-references to
 * the evidence that established the relationship.
 */
final case class EvidenceGraphEdge(
  id: String,
  applicationId: String,
  sourceId: String,
  targetId: String,
  kind: GraphEdgeKind,
  weight: Double,
  evidenceIds: List[String],
  properties: Map[String, String],
  createdAt: Instant
)
